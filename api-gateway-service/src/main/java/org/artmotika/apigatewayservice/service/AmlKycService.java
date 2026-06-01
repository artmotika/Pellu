package org.artmotika.apigatewayservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.apigatewayservice.service.validator.OrderValidator;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.common.dto.UserDto;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmlKycService {
    private final KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    private final List<OrderValidator> validators;

    public void processOrder(OrderRequestDto order) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (!(principal instanceof UserDto user)) {
            log.error("Unauthorized order attempt or invalid principal type");
            throw new RuntimeException("Unauthorized");
        }

        log.debug("Processing order for user: {}, wallet: {}, KYC: {}, qualified: {}, amlRisk: {}", 
            user.getId(), user.getWalletAddress(), user.getKycStatus(), user.isQualified(), user.getAmlRiskScore());
        
        order.setUserId(user.getId());
        order.setWalletAddress(user.getWalletAddress());

        log.debug("Running {} validators", validators.size());
        for (OrderValidator v : validators) {
            log.debug("Running validator: {}", v.getClass().getSimpleName());
            try {
                v.validate(order, user);
            } catch (Exception e) {
                log.warn("Validator {} threw: {}", v.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }

        kafkaTemplate.send("orders.created", order);
    }
}
