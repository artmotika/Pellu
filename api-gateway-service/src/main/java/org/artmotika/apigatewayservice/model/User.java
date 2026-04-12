package org.artmotika.apigatewayservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity @Table(name = "users")
@Data
public class User {
    @Id private String id;
    private String walletAddress;
    private String kycStatus;
    private Integer amlRiskScore;
    private boolean isQualified;
}
