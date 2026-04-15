package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.tradingengineservice.dto.ExecutionResultDto;
import org.artmotika.tradingengineservice.dto.ValidatedOrderEventDto;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.OrderRepository;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.artmotika.tradingengineservice.repo.UserRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingEngineService {
    private final OrderRepository orderRepository;
    private final TradeLedgerRepository ledgerRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final KafkaTemplate<String, ValidatedOrderEventDto> kafkaTemplate;

    private final VolatilityCheckService volatilityCheckService;
    private final TaxAgentService taxAgentService;
    private final BalanceService balanceService;

    @KafkaListener(topics = "orders.created", groupId = "trading-engine-group")
    public void consumeOrder(OrderRequestDto dto) {
        // Use external volatility check service
        volatilityCheckService.validatePrice(dto.getAssetId(), dto.getPrice());

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setUser(userRepository.findById(dto.getUserId()).orElseThrow());
        order.setAsset(assetRepository.findById(dto.getAssetId()).orElseThrow());
        order.setType(Order.OrderType.valueOf(dto.getType()));
        order.setAmount(dto.getAmount());
        order.setPrice(dto.getPrice());
        order.setStatus(Order.OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        ValidatedOrderEventDto event = new ValidatedOrderEventDto();
        event.setId(order.getId()); event.setUserId(dto.getUserId());
        event.setAssetId(dto.getAssetId()); event.setAmount(dto.getAmount()); event.setPrice(dto.getPrice());
        kafkaTemplate.send("orders.validated", event);
    }

    @KafkaListener(topics = "trades.executed", groupId = "trading-engine-group")
    public void handleExecutionResult(ExecutionResultDto result) {
        Order order = orderRepository.findById(result.getOrderId()).orElseThrow();
        order.setStatus(Order.OrderStatus.COMPLETED);
        orderRepository.save(order);

        TradeLedger ledger = new TradeLedger();
        ledger.setId(UUID.randomUUID().toString());
        ledger.setOrder(order);
        ledger.setTransactionHash(result.getTxHash());
        ledger.setExecutionPrice(order.getPrice());
        ledger.setTimestamp(LocalDateTime.now());
        ledgerRepository.save(ledger);

        // --- Use external Tax Agent Module ---
        taxAgentService.processTransactionTax(order);

        // Update user balances
        balanceService.updateBalanceOnExecution(order);

        // Atomic update of volatility price tracker
        volatilityCheckService.updatePrice(order.getAsset().getId(), order.getPrice());
    }
}
