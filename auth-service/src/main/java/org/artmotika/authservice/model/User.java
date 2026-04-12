package org.artmotika.authservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity @Table(name = "users")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id private String id;
    private String walletAddress;
    private String kycStatus; // PENDING, APPROVED, REJECTED
    private Integer amlRiskScore;
    private String password; // hashed
    private boolean isQualified; // true = Qualified Investor
}
