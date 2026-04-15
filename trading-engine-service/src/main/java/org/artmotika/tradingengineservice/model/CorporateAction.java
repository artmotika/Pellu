package org.artmotika.tradingengineservice.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "corporate_actions")
@Data
public class CorporateAction {
    @Id private String id;
    @ManyToOne @JoinColumn(name = "asset_id") private Asset asset;
    @Enumerated(EnumType.STRING)
    private CorporateActionType type;
    private BigDecimal amountPerShare;
    @Enumerated(EnumType.STRING)
    private CorporateActionStatus status;
    private LocalDateTime createdAt;
}
