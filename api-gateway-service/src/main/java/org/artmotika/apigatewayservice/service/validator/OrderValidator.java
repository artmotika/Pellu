package org.artmotika.apigatewayservice.service.validator;

import org.artmotika.apigatewayservice.model.User;
import org.artmotika.common.dto.OrderRequestDto;

public interface OrderValidator {
    void validate(OrderRequestDto order, User user);
}
