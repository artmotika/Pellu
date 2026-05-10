package org.artmotika.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCreateRequestDto {
    private String name;
    private Long totalSupply;
    private AssetType type;
    private BigDecimal ipoPrice;
    private String legalDocHash;
    private Long tradeUnlockTimestamp;
}
