package org.artmotika.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.authservice.model.User;
import org.artmotika.authservice.repo.UserRepository;
import org.artmotika.common.dto.KycStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {
    private final UserRepository userRepository;

    @KafkaListener(topics = "kyc.updated", groupId = "auth-service-group")
    public void consumeKycUpdate(Map<String, Object> event) {
        String userId = (String) event.get("userId");
        boolean approved = (boolean) event.get("approved");
        
        log.info("Consuming KYC update for user {}: {}", userId, approved);
        
        userRepository.findById(userId).ifPresent(user -> {
            user.setKycStatus(approved ? KycStatus.APPROVED : KycStatus.REJECTED);
            userRepository.save(user);
            log.info("User {} KYC status updated to {}", userId, user.getKycStatus());
        });
    }

    @KafkaListener(topics = "aml.frozen", groupId = "auth-service-group")
    public void consumeAmlFreeze(Map<String, Object> event) {
        String userId = (String) event.get("userId");
        boolean freeze = (boolean) event.getOrDefault("freeze", true);
        log.info("Consuming AML freeze for user {}: {}", userId, freeze);
        
        userRepository.findById(userId).ifPresent(user -> {
            user.setFrozen(freeze);
            userRepository.save(user);
        });
    }

    @KafkaListener(topics = "aml.risk_score.updated", groupId = "auth-service-group")
    public void consumeRiskScoreUpdate(Map<String, Object> event) {
        String userId = (String) event.get("userId");
        int score = ((Number) event.get("score")).intValue();
        log.info("Consuming AML risk score update for user {}: {}", userId, score);
        
        userRepository.findById(userId).ifPresent(user -> {
            user.setAmlRiskScore(score);
            userRepository.save(user);
        });
    }
}
