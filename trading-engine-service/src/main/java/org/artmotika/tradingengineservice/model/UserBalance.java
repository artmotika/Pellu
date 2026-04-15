package org.artmotika.tradingengineservice.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "user_balances")
@Data
public class UserBalance {
    @Id private String id; // Typically user_id + ":" + asset_id
    
    @ManyToOne @JoinColumn(name = "user_id") private User user;
    @ManyToOne @JoinColumn(name = "asset_id") private Asset asset;
    
    private BigDecimal amount;
    private BigDecimal weightedAverageCost; // To support tax calculation (WAC method)
    
    private LocalDateTime lastUpdate;
}
