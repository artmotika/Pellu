package org.artmotika.tradingengineservice.service;

import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.UserBalance;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.artmotika.tradingengineservice.repo.CorporateActionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorporateActionServiceTest {

    @Mock private CorporateActionRepository corporateActionRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private BalanceService balanceService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private CorporateActionService corporateActionService;

    @Test
    void triggerDividend_ShouldCalculatePayoutsAndSendToKafka() {
        String assetId = "a1";
        Asset asset = new Asset(); asset.setId(assetId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        UserBalance b1 = new UserBalance();
        b1.setUserId("u1");
        b1.setAmount(new BigDecimal("100"));

        when(balanceService.getBalancesByAsset(assetId)).thenReturn(List.of(b1));

        corporateActionService.triggerDividend(assetId, new BigDecimal("2.5"));

        verify(kafkaTemplate, times(1)).send(eq("dividend.payout"), argThat(map -> {
            Map m = (Map) map;
            return m.get("userId").equals("u1") && m.get("amount").equals(new BigDecimal("250.0"));
        }));
        verify(corporateActionRepository, times(2)).save(any());
    }

    @Test
    void triggerVote_ShouldSendKafkaEvent() {
        String assetId = "a1";
        Asset asset = new Asset(); asset.setId(assetId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        corporateActionService.triggerVote(assetId, "Split?", List.of("Yes", "No"));

        verify(kafkaTemplate, times(1)).send(eq("vote.started"), any(Map.class));
        verify(corporateActionRepository, times(2)).save(any());
    }
}
