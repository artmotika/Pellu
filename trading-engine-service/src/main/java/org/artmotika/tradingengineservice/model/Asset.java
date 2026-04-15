package org.artmotika.tradingengineservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data @Entity @Table(name = "assets")
public class Asset {
    @Id private String id;
    private String solanaMintAddress;
    private String name;
    private long totalSupply;
}
