package org.artmotika.tradingengineservice;

import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.common.dto.OrderType;
import org.artmotika.common.dto.ValidatedOrderEventDto;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.OrderRepository;
import org.artmotika.tradingengineservice.service.TradingEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class TradingFlowIntegrationTest {

    @Autowired
    private TradingEngineService tradingEngineService;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private AssetRepository assetRepository;

    @MockBean
    private KafkaTemplate<String, ValidatedOrderEventDto> kafkaTemplate;

    @MockBean
    private org.springframework.kafka.config.KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Test
    void testOrderConsumptionAndValidation() {
        // 1. Setup mock data
        Asset asset = new Asset();
        asset.setId("a1");
        asset.setSolanaMintAddress("mint123");
        when(assetRepository.findById("a1")).thenReturn(Optional.of(asset));

        OrderRequestDto dto = new OrderRequestDto();
        dto.setUserId("u1");
        dto.setWalletAddress("wallet123");
        dto.setAssetId("a1");
        dto.setAmount(new BigDecimal("100"));
        dto.setPrice(new BigDecimal("50"));
        dto.setType(OrderType.SELL);

        // 2. Trigger consumption
        tradingEngineService.consumeOrder(dto);

        // 3. Verify order saved with correct wallet
        verify(orderRepository).save(argThat(order -> 
            order.getUserId().equals("u1") && 
            order.getWalletAddress().equals("wallet123") &&
            order.getStatus() == Order.OrderStatus.PENDING
        ));

        // 4. Verify validation event NOT sent if no match
        verify(kafkaTemplate, never()).send(eq("orders.validated"), any());
    }
}
