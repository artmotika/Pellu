package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.common.dto.*;
import org.artmotika.tradingengineservice.config.TradingProperties;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.OrderRepository;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingEngineService {
    private final OrderRepository orderRepository;
    private final TradeLedgerRepository ledgerRepository;
    private final AssetRepository assetRepository;
    private final KafkaTemplate<String, ValidatedOrderEventDto> kafkaTemplate;

    private final VolatilityCheckService volatilityCheckService;
    private final TaxAgentService taxAgentService;
    private final BalanceService balanceService;
    private final TradingProperties tradingProperties;
    private final StatePublishService statePublishService;
    private final org.artmotika.tradingengineservice.mapper.AssetMapper assetMapper;

    @jakarta.annotation.PostConstruct
    public void syncToRedis() {
        log.info("Syncing existing assets to Redis...");
        assetRepository.findAll().forEach(asset -> {
            statePublishService.updateAsset(assetMapper.toDto(asset));
            log.info("Synced asset {} to Redis", asset.getId());
        });
    }

    @KafkaListener(topics = "assets.created", groupId = "trading-engine-group")
    public void handleAssetCreated(AssetDto event) {
        log.info("Consuming asset creation: {}", event.getId());
        Asset asset = new Asset();
        asset.setId(event.getId());
        asset.setName(event.getName());
        asset.setSolanaMintAddress(event.getSolanaMintAddress());
        asset.setTotalSupply(event.getTotalSupply());
        asset.setType(event.getType());
        asset.setStatus(event.getStatus());
        asset.setIpoPrice(event.getIpoPrice());
        asset.setTradeUnlockTimestamp(event.getTradeUnlockTimestamp() != null ? event.getTradeUnlockTimestamp() : 0L);
        assetRepository.save(asset);
        
        statePublishService.updateAsset(event);
        log.info("Asset {} saved to database and Redis", event.getId());
    }

    @KafkaListener(topics = "ipo.status", groupId = "trading-engine-group")
    public void handleIpoStatusUpdate(IpoStatusUpdateDto event) {
        log.info("Consuming IPO status update for asset {}: {}", event.getAssetId(), event.getStatus());
        
        Asset asset = assetRepository.findById(event.getAssetId()).orElse(null);
        if (asset == null) {
            log.error("Asset {} not found. Skipping IPO status update.", event.getAssetId());
            return;
        }

        asset.setStatus(event.getStatus());
        assetRepository.save(asset);
        
        statePublishService.updateAsset(assetMapper.toDto(asset));
        log.info("Asset {} status updated to {}", event.getAssetId(), event.getStatus());
    }

    @KafkaListener(topics = "orders.created", groupId = "trading-engine-group")
    @Transactional
    public void consumeOrder(OrderRequestDto dto) {
        log.info("Consuming order for user: {}, asset: {}", dto.getUserId(), dto.getAssetId());
        
        volatilityCheckService.validatePrice(dto.getAssetId(), dto.getPrice());

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setUserId(dto.getUserId()); 
        order.setWalletAddress(dto.getWalletAddress());
        order.setAsset(assetRepository.findById(dto.getAssetId()).orElseThrow());
        order.setType(Order.OrderType.valueOf(dto.getType().name()));
        order.setAmount(dto.getAmount());
        order.setPrice(dto.getPrice());
        order.setStatus(Order.OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // --- NEW: SIMPLE MATCHING ENGINE ---
        matchOrder(order);
    }

    private void matchOrder(Order order) {
        Order.OrderType targetType = (order.getType() == Order.OrderType.BUY) ? Order.OrderType.SELL : Order.OrderType.BUY;
        
        // Find a matching order: opposite type, same asset, same price, still PENDING
        List<Order> matchingOrders = orderRepository.findByAssetAndTypeAndPriceAndStatus(
                order.getAsset(), targetType, order.getPrice(), Order.OrderStatus.PENDING);

        for (Order other : matchingOrders) {
            if (other.getId().equals(order.getId())) continue;
            if (other.getUserId().equals(order.getUserId())) continue; // Self-trade prevention

            log.info("MATCH FOUND! Order {} matched with {}", order.getId(), other.getId());
            
            // For simplicity, we match 1:1 if amounts are equal, or partial match (not fully implemented here)
            // Here we just trigger "validation" which leads to execution
            triggerExecution(order, other);
            return; 
        }
        
        log.info("No immediate match for order {}. Staying PENDING.", order.getId());
    }

    private void triggerExecution(Order buyOrder, Order sellOrder) {
        // In a real system, we'd handle partial amounts. Here we just complete both.
        buyOrder.setStatus(Order.OrderStatus.EXECUTING);
        sellOrder.setStatus(Order.OrderStatus.EXECUTING);
        orderRepository.save(buyOrder);
        orderRepository.save(sellOrder);

        // Send to Solana Connector for on-chain settlement
        // We pick the buyer and seller wallets
        ValidatedOrderEventDto event = new ValidatedOrderEventDto();
        event.setId(buyOrder.getId()); // Using buyOrder ID as primary
        event.setAssetId(buyOrder.getAsset().getId());
        event.setAmount(buyOrder.getAmount());
        event.setPrice(buyOrder.getPrice());
        event.setSellerWallet(sellOrder.getWalletAddress());
        event.setBuyerWallet(buyOrder.getWalletAddress());
        
        kafkaTemplate.send("orders.validated", event);
        
        // Also send execution result for the 'other' order to mark it complete later
        // or just handle both in handleExecutionResult. 
        // For simplicity, I'll store the mapping or just rely on the ID.
    }

    @KafkaListener(topics = "trades.executed", groupId = "trading-engine-group")
    @Transactional
    public void handleExecutionResult(ExecutionResultDto result) {
        log.info("Handling execution result for order: {}", result.getOrderId());
        Order order = orderRepository.findById(result.getOrderId()).orElseThrow();
        
        // If this was a matched trade, we might need to find the counterparty order
        // But for now, let's just complete the order we have.
        order.setStatus(Order.OrderStatus.COMPLETED);
        orderRepository.save(order);

        TradeLedger ledger = new TradeLedger();
        ledger.setId(UUID.randomUUID().toString());
        ledger.setOrder(order);
        ledger.setTransactionHash(result.getTxHash());
        ledger.setExecutionPrice(order.getPrice());
        ledger.setTimestamp(LocalDateTime.now());
        ledgerRepository.save(ledger);

        taxAgentService.processTransactionTax(order);
        balanceService.updateBalanceOnExecution(order);
        volatilityCheckService.updatePrice(order.getAsset().getId(), order.getPrice());
    }
}
