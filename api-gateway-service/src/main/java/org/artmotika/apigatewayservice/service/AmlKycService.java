package org.artmotika.apigatewayservice.service;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.apigatewayservice.exception.AmlViolationException;
import org.artmotika.apigatewayservice.exception.KycNotVerifiedException;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AmlKycService {
    private final KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    private final UserRepository userRepository;
    private final Map<String, List<Long>> userOrderTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Integer> userAmlScores = new ConcurrentHashMap<>();

    public void processOrder(OrderRequestDto order) {
        User user = userRepository.findById(order.getUserId())
                .orElseThrow(() -> new KycNotVerifiedException("User not found"));

        if (!"APPROVED".equals(user.getKycStatus())) {
            throw new KycNotVerifiedException("User KYC not approved");
        }

        long now = Instant.now().toEpochMilli();
        userOrderTimestamps.putIfAbsent(order.getUserId(), new ArrayList<>());
        List<Long> times = userOrderTimestamps.get(order.getUserId());
        times.removeIf(t -> now - t > 60000);
        times.add(now);

        if (times.size() > 5 || order.getAmount().compareTo(new BigDecimal("1000000")) > 0) {
            user.setAmlRiskScore(user.getAmlRiskScore() + 50);
            userRepository.save(user);
            throw new AmlViolationException("AML Policy Violation: Volume/Frequency limit exceeded");
        }

        kafkaTemplate.send("orders.created", order);
    }
}
