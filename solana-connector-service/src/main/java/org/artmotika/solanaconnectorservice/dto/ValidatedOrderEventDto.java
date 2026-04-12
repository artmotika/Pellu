package org.artmotika.solanaconnectorservice.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ValidatedOrderEventDto {
    String id;
    String userId;
    String assetId;
    BigDecimal amount;
    BigDecimal price;
}
