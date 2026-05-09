package org.artmotika.apigatewayservice.service.validator;

import org.artmotika.apigatewayservice.exception.AmlViolationException;
import org.artmotika.common.dto.UserDto;
import org.artmotika.common.dto.OrderRequestDto;
import org.springframework.stereotype.Component;

@Component
public class AmlPolicyValidator implements OrderValidator {
    @Override
    public void validate(OrderRequestDto order, UserDto user) {
        if (user.getAmlRiskScore() != null && user.getAmlRiskScore() > 70) {
            throw new AmlViolationException("High AML risk score: " + user.getAmlRiskScore());
        }
        if (user.isFrozen()) {
            throw new AmlViolationException("User account is frozen due to AML/compliance concerns");
        }
    }
}
