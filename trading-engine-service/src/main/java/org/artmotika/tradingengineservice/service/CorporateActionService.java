package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.CorporateAction;
import org.artmotika.tradingengineservice.model.CorporateActionStatus;
import org.artmotika.tradingengineservice.model.CorporateActionType;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.CorporateActionRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorporateActionService {
    private final CorporateActionRepository corporateActionRepository;
    private final AssetRepository assetRepository;
    private final BalanceService balanceService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void triggerDividend(String assetId, BigDecimal amountPerShare) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        
        CorporateAction ca = new CorporateAction();
        ca.setId(UUID.randomUUID().toString());
        ca.setAsset(asset);
        ca.setType(CorporateActionType.DIVIDEND);
        ca.setAmountPerShare(amountPerShare);
        ca.setStatus(CorporateActionStatus.PENDING);
        ca.setCreatedAt(LocalDateTime.now());
        corporateActionRepository.save(ca);

        // Fetch user balances from snapshot instead of calculating from ledger
        var balances = balanceService.getBalancesByAsset(assetId);

        balances.forEach(balance -> {
            if (balance.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal payout = balance.getAmount().multiply(amountPerShare);
                log.info("Triggering Dividend Payout for User {}: {} units", balance.getUser().getId(), payout);
                kafkaTemplate.send("dividend.payout", Map.of(
                    "userId", balance.getUser().getId(),
                    "assetId", assetId,
                    "amount", payout,
                    "actionId", ca.getId()
                ));
            }
        });

        ca.setStatus(CorporateActionStatus.COMPLETED);
        corporateActionRepository.save(ca);
    }
}
