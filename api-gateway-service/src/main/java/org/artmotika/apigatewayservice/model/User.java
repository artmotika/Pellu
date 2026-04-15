package org.artmotika.apigatewayservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.artmotika.common.dto.KycStatus;

@Entity @Table(name = "users")
@Data
public class User {
    @Id private String id;
    private String walletAddress;
    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus;
    private Integer amlRiskScore;
    private boolean isQualified;
}
