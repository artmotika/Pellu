package org.artmotika.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String id;
    private String walletAddress;
    private KycStatus kycStatus;
    private Integer amlRiskScore;
    private boolean isQualified;
    private boolean isFrozen;
}
