package org.artmotika.common.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderRequestDto {
    String userId;
    String assetId;
    OrderType type;
    BigDecimal amount;
    BigDecimal price;
}