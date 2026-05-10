package org.artmotika.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.authservice.model.User;
import org.artmotika.authservice.repo.UserRepository;
import org.artmotika.common.dto.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {
    private final org.artmotika.authservice.repo.UserRepository userRepository;
    private final org.springframework.cache.CacheManager cacheManager;

    @KafkaListener(topics = "kyc.updated", groupId = "auth-service-group")
    @org.springframework.transaction.annotation.Transactional
    public void consumeKycUpdate(KycUpdateRequestDto req) {
        log.info("Consuming KYC update for user {}: {}", req.getUserId(), req.isApproved());
        try {
            userRepository.updateKycStatus(req.getUserId(), req.isApproved() ? KycStatus.APPROVED : KycStatus.REJECTED);
            evictUserCache(req.getUserId());
            log.info("KYC status updated and cache evicted for user {}", req.getUserId());
        } catch (Exception e) {
            log.error("Failed to update KYC for user {}: {}", req.getUserId(), e.getMessage());
        }
    }

    @KafkaListener(topics = "aml.frozen", groupId = "auth-service-group")
    @org.springframework.transaction.annotation.Transactional
    public void consumeAmlFreeze(FreezeRequestDto req) {
        log.info("Consuming AML freeze for user {}: {}", req.getUserId(), req.isFreeze());
        userRepository.updateFrozen(req.getUserId(), req.isFreeze());
        evictUserCache(req.getUserId());
    }

    @KafkaListener(topics = "aml.risk_score.updated", groupId = "auth-service-group")
    @org.springframework.transaction.annotation.Transactional
    public void consumeRiskScoreUpdate(RiskScoreUpdateRequestDto req) {
        log.info("Consuming AML risk score update for user {}: {}", req.getUserId(), req.getScore());
        userRepository.updateRiskScore(req.getUserId(), req.getScore());
        evictUserCache(req.getUserId());
    }

    private void evictUserCache(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            var cache = cacheManager.getCache("users");
            if (cache != null) {
                cache.evict(user.getWalletAddress());
                log.debug("Evicted cache for wallet: {}", user.getWalletAddress());
            }
        });
    }

}
