package org.artmotika.apigatewayservice.service;

import lombok.RequiredArgsConstructor;
import org.artmotika.apigatewayservice.exception.KycNotVerifiedException;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.artmotika.apigatewayservice.service.validator.OrderValidator;
import org.artmotika.common.dto.OrderRequestDto;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AmlKycService {
    private final KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    private final UserRepository userRepository;
    private final List<OrderValidator> validators;

    public void processOrder(OrderRequestDto order) {
        User user = userRepository.findById(order.getUserId())
                .orElseThrow(() -> new KycNotVerifiedException("User not found"));

        validators.forEach(v -> v.validate(order, user));

        kafkaTemplate.send("orders.created", order);
    }
}
