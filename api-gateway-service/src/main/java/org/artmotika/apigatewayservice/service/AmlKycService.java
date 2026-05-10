package org.artmotika.apigatewayservice.service;

import lombok.RequiredArgsConstructor;
import org.artmotika.apigatewayservice.exception.KycNotVerifiedException;
import org.artmotika.common.dto.UserDto;
import org.artmotika.apigatewayservice.service.validator.OrderValidator;
import org.artmotika.common.dto.OrderRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AmlKycService {
    private final KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    private final List<OrderValidator> validators;
    private final RestTemplate restTemplate;

    @Value("${app.services.auth}")
    private String authServiceUrl;

    public void processOrder(OrderRequestDto order) {
        System.out.println("DEBUG: Fetching user " + order.getUserId() + " from " + authServiceUrl);
        UserDto user = restTemplate.getForObject(authServiceUrl + "/api/v1/auth/users/" + order.getUserId(), UserDto.class);
        
        if (user == null) {
            System.out.println("DEBUG: User not found!");
            throw new KycNotVerifiedException("User not found via Auth Service");
        }

        System.out.println("DEBUG: User found, wallet: " + user.getWalletAddress());
        order.setWalletAddress(user.getWalletAddress());

        validators.forEach(v -> {
            System.out.println("DEBUG: Running validator: " + v.getClass().getSimpleName());
            try {
                v.validate(order, user);
            } catch (Exception e) {
                System.out.println("DEBUG: Validator " + v.getClass().getSimpleName() + " threw: " + e.getMessage());
                throw e;
            }
        });

        System.out.println("DEBUG: Sending to Kafka: " + order);
        kafkaTemplate.send("orders.created", order);
    }
}
