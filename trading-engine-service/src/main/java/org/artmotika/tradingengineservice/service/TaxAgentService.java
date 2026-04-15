package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.TaxLedger;
import org.artmotika.tradingengineservice.repo.TaxLedgerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxAgentService {

    private final TaxLedgerRepository taxLedgerRepository;
    private final BalanceService balanceService;

    @Value("${tax.rate.ndfl:0.13}")
    private BigDecimal defaultTaxRate;

    public void processTransactionTax(Order order) {
        if (order.getType() != Order.OrderType.SELL) {
            return;
        }

        log.info("Calculating tax for SELL order: {}", order.getId());

        // In production systems, tax is calculated as: (Sale Price - Weighted Average Cost) * Tax Rate
        BigDecimal costBasis = calculateCostBasis(order);
        BigDecimal salePrice = order.getPrice();
        BigDecimal capitalGainPerUnit = salePrice.subtract(costBasis);
        
        BigDecimal taxAmount;
        if (capitalGainPerUnit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalGain = capitalGainPerUnit.multiply(order.getAmount());
            taxAmount = totalGain.multiply(defaultTaxRate).setScale(4, RoundingMode.HALF_UP);
            log.info("Capital gain detected: {} per unit. Total tax: {}", capitalGainPerUnit, taxAmount);
        } else {
            // If no gain, tax is 0 (simplified, normally we'd track losses too)
            taxAmount = BigDecimal.ZERO;
            log.info("No capital gain for order {}. Tax is 0.", order.getId());
        }

        TaxLedger tax = new TaxLedger();
        tax.setId(UUID.randomUUID().toString());
        tax.setUser(order.getUser());
        tax.setOrder(order);
        tax.setTaxAmount(taxAmount);
        tax.setTimestamp(LocalDateTime.now());

        taxLedgerRepository.save(tax);
    }

    private BigDecimal calculateCostBasis(Order order) {
        // Use Weighted Average Cost from the user's current balance
        var balance = balanceService.getBalance(order.getUser().getId(), order.getAsset().getId());
        return balance != null ? balance.getWeightedAverageCost() : BigDecimal.ZERO;
    }
}
