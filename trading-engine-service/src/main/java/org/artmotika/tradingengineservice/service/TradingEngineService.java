package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.OrderRequestDto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.artmotika.tradingengineservice.dto.ExecutionResultDto;
import org.artmotika.tradingengineservice.dto.ValidatedOrderEventDto;
import org.artmotika.tradingengineservice.exception.PriceVolatilityException;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.model.TaxLedger;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.OrderRepository;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.artmotika.tradingengineservice.repo.UserRepository;
import org.artmotika.tradingengineservice.repo.TaxLedgerRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TradingEngineService {
    private final OrderRepository orderRepository;
    private final TradeLedgerRepository ledgerRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TaxLedgerRepository taxLedgerRepository;
    private final KafkaTemplate<String, ValidatedOrderEventDto> kafkaTemplate;

    // High-performance in-memory price tracking (Sliding Window of last 10 prices per asset)
    private final Cache<String, LinkedList<BigDecimal>> priceCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(1000)
            .build();

    @PostConstruct
    public void initCache() {
        // Pre-warming cache with recent trades could be added here for production
    }

    @KafkaListener(topics = "orders.created", groupId = "trading-engine-group")
    public void consumeOrder(OrderRequestDto dto) {
        // Fast volatility check using in-memory state
        LinkedList<BigDecimal> prices = priceCache.getIfPresent(dto.getAssetId());
        if (prices != null && !prices.isEmpty()) {
            BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(new BigDecimal(prices.size()), 4, RoundingMode.HALF_UP);
            
            BigDecimal volatilityThreshold = new BigDecimal("0.20"); // 20%
            BigDecimal diff = dto.getPrice().subtract(avg).abs();
            BigDecimal thresholdAmount = avg.multiply(volatilityThreshold);

            if (diff.compareTo(thresholdAmount) > 0) {
                throw new PriceVolatilityException("Price spike detected (20%+). Risk check failed.");
            }
        }

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

        // --- Tax Agent Module (Regulatory Requirement) ---
        if (order.getType() == Order.OrderType.SELL) {
            BigDecimal taxRate = new BigDecimal("0.13"); // 13% NDFL
            BigDecimal volume = order.getAmount().multiply(order.getPrice());
            BigDecimal taxAmount = volume.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);

            TaxLedger tax = new TaxLedger();
            tax.setId(UUID.randomUUID().toString());
            tax.setUser(order.getUser());
            tax.setOrder(order);
            tax.setTaxAmount(taxAmount);
            tax.setTimestamp(LocalDateTime.now());
            taxLedgerRepository.save(tax);
        }

        // Atomic update of in-memory price tracker
        updatePriceCache(order.getAsset().getId(), order.getPrice());
    }

    private synchronized void updatePriceCache(String assetId, BigDecimal price) {
        LinkedList<BigDecimal> prices = priceCache.get(assetId, k -> new LinkedList<>());
        if (prices.size() >= 10) { prices.removeFirst(); }
        prices.addLast(price);
    }

    @Scheduled(cron = "0 0 23 * * ?")
    public void generateBankReport() throws Exception {
        List<TradeLedger> dailyTrades = ledgerRepository.findByTimestampAfter(LocalDateTime.now().minusDays(1));
        StringBuilder csv = new StringBuilder("ID,TxHash,Price,Time\n");
        for (TradeLedger t : dailyTrades) {
            csv.append(t.getId()).append(",").append(t.getTransactionHash()).append(",")
                    .append(t.getExecutionPrice()).append(",").append(t.getTimestamp()).append("\n");
        }
        Path path = Paths.get("/reports/daily_ledger_" + System.currentTimeMillis() + ".csv");
        Files.createDirectories(path.getParent());
        Files.writeString(path, csv.toString());
    }
}
