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
    private String type; // DIVIDEND, COUPON
    private BigDecimal amountPerShare;
    private String status; // PENDING, COMPLETED
    private LocalDateTime createdAt;
}
