package org.artmotika.apigatewayservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "investor_limits")
@Data
public class InvestorLimit {
    @Id private String userId;
    private BigDecimal annualInvestment;
    private LocalDateTime lastReset;
}
