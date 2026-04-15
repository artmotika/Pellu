package org.artmotika.tradingengineservice.service;

import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.User;
import org.artmotika.tradingengineservice.model.UserBalance;
import org.artmotika.tradingengineservice.repo.TaxLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxAgentServiceTest {

    @Mock private TaxLedgerRepository taxLedgerRepository;
    @Mock private BalanceService balanceService;

    @InjectMocks
    private TaxAgentService taxAgentService;

    @Test
    void processTransactionTax_ShouldCalculateTaxForSellWithGain() {
        ReflectionTestUtils.setField(taxAgentService, "defaultTaxRate", new BigDecimal("0.13"));

        User user = new User(); user.setId("u1");
        Asset asset = new Asset(); asset.setId("a1");
        
        Order order = new Order();
        order.setId("o1");
        order.setUser(user);
        order.setAsset(asset);
        order.setType(Order.OrderType.SELL);
        order.setAmount(new BigDecimal("10"));
        order.setPrice(new BigDecimal("150")); // Sale price 150

        UserBalance balance = new UserBalance();
        balance.setWeightedAverageCost(new BigDecimal("100")); // Cost basis 100
        
        when(balanceService.getBalance("u1", "a1")).thenReturn(balance);

        taxAgentService.processTransactionTax(order);

        // Gain per unit: 150 - 100 = 50. Total gain: 50 * 10 = 500. Tax: 500 * 0.13 = 65.
        verify(taxLedgerRepository, times(1)).save(argThat(tax -> 
            tax.getTaxAmount().compareTo(new BigDecimal("65.0000")) == 0
        ));
    }

    @Test
    void processTransactionTax_ShouldNotCalculateTaxIfNoGain() {
        ReflectionTestUtils.setField(taxAgentService, "defaultTaxRate", new BigDecimal("0.13"));

        User user = new User(); user.setId("u1");
        Asset asset = new Asset(); asset.setId("a1");
        
        Order order = new Order();
        order.setId("o1");
        order.setUser(user);
        order.setAsset(asset);
        order.setType(Order.OrderType.SELL);
        order.setAmount(new BigDecimal("10"));
        order.setPrice(new BigDecimal("80")); // Sale price 80

        UserBalance balance = new UserBalance();
        balance.setWeightedAverageCost(new BigDecimal("100")); // Cost basis 100
        
        when(balanceService.getBalance("u1", "a1")).thenReturn(balance);

        taxAgentService.processTransactionTax(order);

        // Loss/No gain -> tax 0.
        verify(taxLedgerRepository, times(1)).save(argThat(tax -> 
            tax.getTaxAmount().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @Test
    void processTransactionTax_ShouldIgnoreBuyOrders() {
        Order order = new Order();
        order.setType(Order.OrderType.BUY);

        taxAgentService.processTransactionTax(order);

        verifyNoInteractions(taxLedgerRepository);
    }
}
