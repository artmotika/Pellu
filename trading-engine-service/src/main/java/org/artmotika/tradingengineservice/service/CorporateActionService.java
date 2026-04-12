package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.CorporateAction;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.CorporateActionRepository;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorporateActionService {
    private final CorporateActionRepository corporateActionRepository;
    private final AssetRepository assetRepository;
    private final TradeLedgerRepository tradeLedgerRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void triggerDividend(String assetId, BigDecimal amountPerShare) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        
        CorporateAction ca = new CorporateAction();
        ca.setId(UUID.randomUUID().toString());
        ca.setAsset(asset);
        ca.setType("DIVIDEND");
        ca.setAmountPerShare(amountPerShare);
        ca.setStatus("PENDING");
        ca.setCreatedAt(LocalDateTime.now());
        corporateActionRepository.save(ca);

        // Calculate balances based on ledger (Simplified: sum of Buy minus sum of Sell)
        List<TradeLedger> trades = tradeLedgerRepository.findTop10ByOrder_Asset_IdOrderByTimestampDesc(assetId);
        Map<String, BigDecimal> userBalances = new HashMap<>();
        
        // In real system, we'd iterate over ALL trades or use a balance snapshot table
        // For thesis, we simulate the logic
        for (TradeLedger t : trades) {
            String userId = t.getOrder().getUser().getId();
            BigDecimal amount = t.getOrder().getAmount();
            if (t.getOrder().getType().name().equals("BUY")) {
                userBalances.put(userId, userBalances.getOrDefault(userId, BigDecimal.ZERO).add(amount));
            } else {
                userBalances.put(userId, userBalances.getOrDefault(userId, BigDecimal.ZERO).subtract(amount));
            }
        }

        userBalances.forEach((userId, balance) -> {
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal payout = balance.multiply(amountPerShare);
                log.info("Triggering Dividend Payout for User {}: {} units", userId, payout);
                kafkaTemplate.send("dividend.payout", Map.of(
                    "userId", userId,
                    "assetId", assetId,
                    "amount", payout,
                    "actionId", ca.getId()
                ));
            }
        });

        ca.setStatus("COMPLETED");
        corporateActionRepository.save(ca);
    }
}
