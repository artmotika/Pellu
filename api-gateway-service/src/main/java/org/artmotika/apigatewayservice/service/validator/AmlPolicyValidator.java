package org.artmotika.apigatewayservice.service.validator;

import lombok.RequiredArgsConstructor;
import org.artmotika.apigatewayservice.exception.AmlViolationException;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.artmotika.common.dto.OrderRequestDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class AmlPolicyValidator implements OrderValidator {
    private final UserRepository userRepository;
    private final Map<String, List<Long>> userOrderTimestamps = new ConcurrentHashMap<>();
    
    private static final int MAX_ORDERS_PER_MINUTE = 5;
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("1000000");
    private static final int RISK_SCORE_PENALTY = 50;

    @Override
    public void validate(OrderRequestDto order, User user) {
        long now = Instant.now().toEpochMilli();
        userOrderTimestamps.putIfAbsent(order.getUserId(), new ArrayList<>());
        List<Long> times = userOrderTimestamps.get(order.getUserId());
        
        synchronized (times) {
            times.removeIf(t -> now - t > 60000);
            times.add(now);

            if (times.size() > MAX_ORDERS_PER_MINUTE || order.getAmount().compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
                user.setAmlRiskScore(user.getAmlRiskScore() + RISK_SCORE_PENALTY);
                userRepository.save(user);
                throw new AmlViolationException("AML Policy Violation: Volume/Frequency limit exceeded");
            }
        }
    }
}
