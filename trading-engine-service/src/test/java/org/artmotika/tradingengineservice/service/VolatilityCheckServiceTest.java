package org.artmotika.tradingengineservice.service;

import org.artmotika.tradingengineservice.config.TradingProperties;
import org.artmotika.tradingengineservice.exception.PriceVolatilityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VolatilityCheckServiceTest {

    private VolatilityCheckService volatilityCheckService;

    @BeforeEach
    void setUp() {
        TradingProperties props = new TradingProperties();
        props.getVolatility().setThreshold(new BigDecimal("0.20"));
        props.getVolatility().setWindowSize(5);
        volatilityCheckService = new VolatilityCheckService(props);
    }

    @Test
    void validatePrice_ShouldAllowStablePrice() {
        String assetId = "BTC";
        volatilityCheckService.updatePrice(assetId, new BigDecimal("100"));
        volatilityCheckService.updatePrice(assetId, new BigDecimal("105"));
        volatilityCheckService.updatePrice(assetId, new BigDecimal("98"));

        // Average is (100+105+98)/3 = 101. Threshold is 20.2.
        // 110 is within threshold.
        assertDoesNotThrow(() -> volatilityCheckService.validatePrice(assetId, new BigDecimal("110")));
    }

    @Test
    void validatePrice_ShouldThrowOnPriceSpike() {
        String assetId = "ETH";
        volatilityCheckService.updatePrice(assetId, new BigDecimal("100"));
        
        // 130 is 30% increase, which is > 20%
        assertThrows(PriceVolatilityException.class, () -> 
            volatilityCheckService.validatePrice(assetId, new BigDecimal("130"))
        );
    }

    @Test
    void validatePrice_ShouldThrowOnPriceDrop() {
        String assetId = "SOL";
        volatilityCheckService.updatePrice(assetId, new BigDecimal("100"));
        
        // 70 is 30% decrease, which is > 20%
        assertThrows(PriceVolatilityException.class, () -> 
            volatilityCheckService.validatePrice(assetId, new BigDecimal("70"))
        );
    }

    @Test
    void validatePrice_ShouldHandleEmptyCache() {
        assertDoesNotThrow(() -> volatilityCheckService.validatePrice("NEW_ASSET", new BigDecimal("1000")));
    }
}
