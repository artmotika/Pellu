package org.artmotika.apigatewayservice.service;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.apigatewayservice.exception.AmlViolationException;
import org.artmotika.apigatewayservice.exception.KycNotVerifiedException;
import org.artmotika.apigatewayservice.model.InvestorLimit;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.InvestorLimitRepository;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AmlKycService {
    private final KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    private final UserRepository userRepository;
    private final InvestorLimitRepository investorLimitRepository;
    private final Map<String, List<Long>> userOrderTimestamps = new ConcurrentHashMap<>();

    private static final BigDecimal RETAIL_ANNUAL_LIMIT = new BigDecimal("600000");

    public void processOrder(OrderRequestDto order) {
        User user = userRepository.findById(order.getUserId())
                .orElseThrow(() -> new KycNotVerifiedException("User not found"));

        if (!"APPROVED".equals(user.getKycStatus())) {
            throw new KycNotVerifiedException("User KYC not approved");
        }

        // --- Investor Limit Check (Central Bank Rule) ---
        if (!user.isQualified() && "BUY".equalsIgnoreCase(order.getType())) {
            BigDecimal orderValue = order.getAmount().multiply(order.getPrice());
            InvestorLimit limit = investorLimitRepository.findById(user.getId()).orElseGet(() -> {
                InvestorLimit l = new InvestorLimit();
                l.setUserId(user.getId());
                l.setAnnualInvestment(BigDecimal.ZERO);
                l.setLastReset(LocalDateTime.now());
                return l;
            });

            BigDecimal newTotal = limit.getAnnualInvestment().add(orderValue);
            if (newTotal.compareTo(RETAIL_ANNUAL_LIMIT) > 0) {
                throw new AmlViolationException("Retail investor annual limit (600,000 RUB) exceeded");
            }
            limit.setAnnualInvestment(newTotal);
            investorLimitRepository.save(limit);
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
