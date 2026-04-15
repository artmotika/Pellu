package org.artmotika.tradingengineservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.tradingengineservice.exception.PriceVolatilityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VolatilityCheckService {

    private final BigDecimal volatilityThreshold;
    private final int windowSize;

    // High-performance in-memory price tracking
    private final Cache<String, LinkedList<BigDecimal>> priceCache;

    public VolatilityCheckService(
            @Value("${trading.volatility.threshold:0.20}") BigDecimal volatilityThreshold,
            @Value("${trading.volatility.window-size:10}") int windowSize) {
        this.volatilityThreshold = volatilityThreshold;
        this.windowSize = windowSize;
        this.priceCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(1000)
                .build();
    }

    public void validatePrice(String assetId, BigDecimal currentPrice) {
        LinkedList<BigDecimal> prices = priceCache.getIfPresent(assetId);
        if (prices != null && !prices.isEmpty()) {
            BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(new BigDecimal(prices.size()), 4, RoundingMode.HALF_UP);

            BigDecimal diff = currentPrice.subtract(avg).abs();
            BigDecimal thresholdAmount = avg.multiply(volatilityThreshold);

            if (diff.compareTo(thresholdAmount) > 0) {
                log.warn("Price spike detected for asset {}: current={}, avg={}, threshold={}%",
                        assetId, currentPrice, avg, volatilityThreshold.multiply(new BigDecimal("100")));
                throw new PriceVolatilityException("Price spike detected. Risk check failed.");
            }
        }
    }

    public synchronized void updatePrice(String assetId, BigDecimal price) {
        LinkedList<BigDecimal> prices = priceCache.get(assetId, k -> new LinkedList<>());
        if (prices.size() >= windowSize) {
            prices.removeFirst();
        }
        prices.addLast(price);
    }
}
