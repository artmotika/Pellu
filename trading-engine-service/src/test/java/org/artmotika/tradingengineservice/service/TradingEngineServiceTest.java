package org.artmotika.tradingengineservice.service;

import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.tradingengineservice.dto.ExecutionResultDto;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.User;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.OrderRepository;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.artmotika.tradingengineservice.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import org.artmotika.common.dto.OrderType;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingEngineServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TradeLedgerRepository ledgerRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private VolatilityCheckService volatilityCheckService;
    @Mock private TaxAgentService taxAgentService;
    @Mock private BalanceService balanceService;

    @InjectMocks
    private TradingEngineService tradingEngineService;

    @Test
    void consumeOrder_ShouldValidateAndSavePendingOrder() {
        OrderRequestDto dto = new OrderRequestDto();
        dto.setUserId("u1"); dto.setAssetId("a1"); dto.setAmount(BigDecimal.ONE); dto.setPrice(BigDecimal.TEN); dto.setType(OrderType.BUY);

        when(userRepository.findById("u1")).thenReturn(Optional.of(new User()));
        when(assetRepository.findById("a1")).thenReturn(Optional.of(new Asset()));

        tradingEngineService.consumeOrder(dto);

        verify(volatilityCheckService).validatePrice("a1", BigDecimal.TEN);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus() == Order.OrderStatus.PENDING && order.getPrice().equals(BigDecimal.TEN)
        ));
        verify(kafkaTemplate, times(1)).send(eq("orders.validated"), any());
    }

    @Test
    void handleExecutionResult_ShouldCompleteOrderAndTriggerModules() {
        ExecutionResultDto result = new ExecutionResultDto();
        result.setOrderId("o1");
        result.setTxHash("hash123");

        Asset asset = new Asset(); asset.setId("a1");
        Order order = new Order(); order.setId("o1"); order.setPrice(BigDecimal.TEN); order.setAsset(asset);

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
