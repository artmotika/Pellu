package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.common.dto.AssetStatus;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.tradingengineservice.dto.ExecutionResultDto;
import org.artmotika.tradingengineservice.dto.ValidatedOrderEventDto;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.OrderRepository;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
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
        assetRepository.save(asset);
        log.info("Asset {} saved to database", event.getId());
    }

    @KafkaListener(topics = "ipo.status", groupId = "trading-engine-group")
    public void handleIpoStatusUpdate(Map<String, Object> event) {
        String assetId = (String) event.get("assetId");
        String statusStr = (String) event.get("status");
        log.info("Consuming IPO status update for asset {}: {}", assetId, statusStr);
        
        AssetStatus status = AssetStatus.valueOf(statusStr);
        
        // Simple retry for race condition
        Asset asset = null;
        for (int i = 0; i < 5; i++) {
            Optional<Asset> assetOpt = assetRepository.findById(assetId);
            if (assetOpt.isPresent()) {
                asset = assetOpt.get();
                break;
            }
            log.warn("Asset {} not found yet, retrying... ({}/5)", assetId, i + 1);
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (asset == null) {
            log.error("Asset {} still not found after retries. Skipping IPO status update.", assetId);
            return;
        }

        asset.setStatus(status);
        assetRepository.save(asset);
        log.info("Asset {} status updated to {}", assetId, status);
    }

    @Value("${app.platform.wallet:Platform111111111111111111111111111111111}")
    private String platformWallet;

    @KafkaListener(topics = "orders.created", groupId = "trading-engine-group")
    public void consumeOrder(OrderRequestDto dto) {
        // Use external volatility check service
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

        ValidatedOrderEventDto event = new ValidatedOrderEventDto();
        event.setId(order.getId()); 
        event.setAssetId(dto.getAssetId()); 
        event.setAmount(dto.getAmount()); 
        event.setPrice(dto.getPrice());
        
        if (order.getType() == Order.OrderType.SELL) {
            event.setSellerWallet(order.getWalletAddress());
            event.setBuyerWallet(platformWallet);
        } else {
            event.setSellerWallet(platformWallet);
            event.setBuyerWallet(order.getWalletAddress());
        }
        
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
