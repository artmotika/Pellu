package org.artmotika.apigatewayservice.service.validator;

import org.artmotika.apigatewayservice.exception.KycNotVerifiedException;
import org.artmotika.common.dto.KycStatus;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.common.dto.OrderRequestDto;
import org.springframework.stereotype.Component;

@Component
public class KycValidator implements OrderValidator {
    @Override
    public void validate(OrderRequestDto order, User user) {
        if (user.getKycStatus() != KycStatus.APPROVED) {
            throw new KycNotVerifiedException("User KYC not approved");
        }
    }
}
