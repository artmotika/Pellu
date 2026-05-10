package org.artmotika.tradingengineservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {
    private Volatility volatility = new Volatility();
    private App app = new App();

    @Data
    public static class Volatility {
        private BigDecimal threshold = new BigDecimal("0.20");
        private int windowSize = 10;
    }

    @Data
    public static class App {
        private String platformWallet = "Platform111111111111111111111111111111111";
    }
}
