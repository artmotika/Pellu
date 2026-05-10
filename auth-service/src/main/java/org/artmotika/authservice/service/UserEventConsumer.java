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
    private final UserRepository userRepository;

    @KafkaListener(topics = "kyc.updated", groupId = "auth-service-group")
    public void consumeKycUpdate(KycUpdateRequestDto req) {
        log.info("Consuming KYC update for user {}: {}", req.getUserId(), req.isApproved());
        try {
            userRepository.updateKycStatus(req.getUserId(), req.isApproved() ? KycStatus.APPROVED : KycStatus.REJECTED);
            log.info("KYC status updated in DB for user {}", req.getUserId());
        } catch (Exception e) {
            log.error("Failed to update KYC for user {}: {}", req.getUserId(), e.getMessage());
        }
    }

    @KafkaListener(topics = "aml.frozen", groupId = "auth-service-group")
    public void consumeAmlFreeze(FreezeRequestDto req) {
        log.info("Consuming AML freeze for user {}: {}", req.getUserId(), req.isFreeze());
        userRepository.updateFrozen(req.getUserId(), req.isFreeze());
    }

    @KafkaListener(topics = "aml.risk_score.updated", groupId = "auth-service-group")
    public void consumeRiskScoreUpdate(RiskScoreUpdateRequestDto req) {
        log.info("Consuming AML risk score update for user {}: {}", req.getUserId(), req.getScore());
        userRepository.updateRiskScore(req.getUserId(), req.getScore());
    }
}
