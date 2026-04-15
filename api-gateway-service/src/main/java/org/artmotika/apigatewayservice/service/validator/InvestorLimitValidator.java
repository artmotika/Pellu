package org.artmotika.apigatewayservice.service.validator;

import lombok.RequiredArgsConstructor;
import org.artmotika.apigatewayservice.exception.AmlViolationException;
import org.artmotika.apigatewayservice.model.InvestorLimit;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.InvestorLimitRepository;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.common.dto.OrderType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InvestorLimitValidator implements OrderValidator {
    private final InvestorLimitRepository investorLimitRepository;
    private static final BigDecimal RETAIL_ANNUAL_LIMIT = new BigDecimal("600000");

    @Override
    public void validate(OrderRequestDto order, User user) {
        if (!user.isQualified() && order.getType() == OrderType.BUY) {
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
    }
}
