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
public class ValidatedOrderEventDto {
    private String id;
    private String assetId;
    private String sellerWallet;
    private String buyerWallet;
    private String sellerTokenAccount;
    private String buyerTokenAccount;
    private BigDecimal amount;
    private BigDecimal price;
}
