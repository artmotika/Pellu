package org.artmotika.tradingengineservice.service;

import org.artmotika.common.dto.*;
import org.artmotika.tradingengineservice.config.TradingProperties;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.OrderRepository;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingEngineServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TradeLedgerRepository ledgerRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private VolatilityCheckService volatilityCheckService;
    @Mock private TaxAgentService taxAgentService;
    @Mock private BalanceService balanceService;
    @Mock private TradingProperties tradingProperties;
    @Mock private StatePublishService statePublishService;
    @Mock private org.artmotika.tradingengineservice.mapper.AssetMapper assetMapper;

    @InjectMocks
    private TradingEngineService tradingEngineService;

    @Test
    void handleAssetCreated_ShouldSaveAsset() {
        AssetDto event = new AssetDto();
        event.setId("a1");
        event.setName("Gold");
        event.setTotalSupply(1000L);
        event.setType(AssetType.COMMODITY);
        event.setStatus(AssetStatus.IPO_PLANNED);
        event.setIpoPrice(BigDecimal.TEN);

        tradingEngineService.handleAssetCreated(event);

        verify(assetRepository, times(1)).save(argThat(asset -> 
            asset.getId().equals("a1") && 
            asset.getName().equals("Gold") && 
            asset.getStatus() == AssetStatus.IPO_PLANNED
        ));
        verify(statePublishService).updateAsset(event);
    }

    @Test
    void handleIpoStatusUpdate_ShouldUpdateAssetStatus() {
        Asset asset = new Asset();
        asset.setId("a1");
        asset.setStatus(AssetStatus.IPO_PLANNED);
        when(assetRepository.findById("a1")).thenReturn(Optional.of(asset));
        when(assetMapper.toDto(any())).thenReturn(new AssetDto());

        tradingEngineService.handleIpoStatusUpdate(new IpoStatusUpdateDto("a1", AssetStatus.IPO_ACTIVE));

        assertEquals(AssetStatus.IPO_ACTIVE, asset.getStatus());
        verify(assetRepository, times(1)).save(asset);
        verify(statePublishService).updateAsset(any());
    }

    @Test
    void consumeOrder_ShouldStayPending_WhenNoMatchFound() {
        TradingProperties.App app = mock(TradingProperties.App.class);
        
        OrderRequestDto dto = new OrderRequestDto();
        dto.setUserId("u1"); 
        dto.setWalletAddress("wallet1");
        dto.setAssetId("a1"); 
        dto.setAmount(BigDecimal.ONE); 
        dto.setPrice(BigDecimal.TEN); 
        dto.setType(OrderType.BUY);

        Asset asset = new Asset(); asset.setId("a1");
        when(assetRepository.findById("a1")).thenReturn(Optional.of(asset));
        // No matching orders
        when(orderRepository.findByAssetAndTypeAndPriceAndStatus(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        tradingEngineService.consumeOrder(dto);

        verify(volatilityCheckService).validatePrice("a1", BigDecimal.TEN);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus() == Order.OrderStatus.PENDING && 
            order.getPrice().equals(BigDecimal.TEN)
        ));
        // Should NOT send validated event if no match
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void consumeOrder_ShouldTriggerExecution_WhenMatchFound() {
        OrderRequestDto dto = new OrderRequestDto();
        dto.setUserId("u1"); 
        dto.setWalletAddress("wallet1");
        dto.setAssetId("a1"); 
        dto.setAmount(BigDecimal.TEN); 
        dto.setPrice(BigDecimal.valueOf(100)); 
        dto.setType(OrderType.BUY);

        Asset asset = new Asset(); asset.setId("a1");
        when(assetRepository.findById("a1")).thenReturn(Optional.of(asset));

        Order matchingOrder = new Order();
        matchingOrder.setId("o-other");
        matchingOrder.setUserId("u2");
        matchingOrder.setWalletAddress("wallet2");
        matchingOrder.setAsset(asset);
        matchingOrder.setType(Order.OrderType.SELL);
        matchingOrder.setPrice(BigDecimal.valueOf(100));
        matchingOrder.setAmount(BigDecimal.TEN);
        matchingOrder.setStatus(Order.OrderStatus.PENDING);

        when(orderRepository.findByAssetAndTypeAndPriceAndStatus(any(), any(), any(), any()))
                .thenReturn(List.of(matchingOrder));

        tradingEngineService.consumeOrder(dto);

        // Verify both orders updated to EXECUTING
        verify(orderRepository, atLeast(2)).save(argThat(order -> 
            order.getStatus() == Order.OrderStatus.EXECUTING
        ));
        
        // Verify Kafka event sent
        verify(kafkaTemplate, times(1)).send(eq("orders.validated"), any());
    }

    @Test
    void handleExecutionResult_ShouldCompleteOrderAndTriggerModules() {
        ExecutionResultDto result = new ExecutionResultDto();
        result.setOrderId("o1");
        result.setTxHash("hash123");

        Asset asset = new Asset(); asset.setId("a1");
        Order order = new Order(); order.setId("o1"); order.setPrice(BigDecimal.TEN); order.setAsset(asset); order.setUserId("u1");

        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        tradingEngineService.handleExecutionResult(result);

        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        verify(taxAgentService).processTransactionTax(order);
        verify(balanceService).updateBalanceOnExecution(order);
        verify(volatilityCheckService).updatePrice("a1", BigDecimal.TEN);
        verify(orderRepository, times(1)).save(order);
        verify(ledgerRepository, times(1)).save(any());
    }
}
