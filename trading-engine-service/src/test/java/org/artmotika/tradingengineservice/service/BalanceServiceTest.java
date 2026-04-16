package org.artmotika.tradingengineservice.service;

import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.UserBalance;
import org.artmotika.tradingengineservice.repo.UserBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock private UserBalanceRepository balanceRepository;
    @InjectMocks private BalanceService balanceService;

    @Test
    void updateBalanceOnExecution_ShouldIncreaseBalanceOnBuy() {
        Asset asset = new Asset(); asset.setId("a1");
        
        Order order = new Order();
        order.setUserId("u1");
        order.setAsset(asset);
        order.setType(Order.OrderType.BUY);
        order.setAmount(new BigDecimal("10"));
        order.setPrice(new BigDecimal("100"));

        when(balanceRepository.findById("u1:a1")).thenReturn(Optional.empty());

        balanceService.updateBalanceOnExecution(order);

        verify(balanceRepository, times(1)).save(argThat(balance -> 
            balance.getAmount().equals(new BigDecimal("10")) &&
            balance.getWeightedAverageCost().equals(new BigDecimal("100.0000")) &&
            balance.getUserId().equals("u1")
        ));
    }

    @Test
    void updateBalanceOnExecution_ShouldUpdateWacOnSecondBuy() {
        Asset asset = new Asset(); asset.setId("a1");
        
        UserBalance existing = new UserBalance();
        existing.setId("u1:a1");
        existing.setUserId("u1");
        existing.setAsset(asset);
        existing.setAmount(new BigDecimal("10"));
        existing.setWeightedAverageCost(new BigDecimal("100"));

        Order order = new Order();
        order.setUserId("u1");
        order.setAsset(asset);
        order.setType(Order.OrderType.BUY);
        order.setAmount(new BigDecimal("10"));
        order.setPrice(new BigDecimal("200"));

        when(balanceRepository.findById("u1:a1")).thenReturn(Optional.of(existing));

        balanceService.updateBalanceOnExecution(order);

        // New total amount: 20. Total cost: (10*100) + (10*200) = 3000. WAC: 3000/20 = 150.
        verify(balanceRepository, times(1)).save(argThat(balance -> 
            balance.getAmount().equals(new BigDecimal("20")) &&
            balance.getWeightedAverageCost().compareTo(new BigDecimal("150.0000")) == 0
        ));
    }

    @Test
    void updateBalanceOnExecution_ShouldDecreaseBalanceOnSell() {
        Asset asset = new Asset(); asset.setId("a1");
        
        UserBalance existing = new UserBalance();
        existing.setId("u1:a1");
        existing.setUserId("u1");
        existing.setAmount(new BigDecimal("30"));
        existing.setWeightedAverageCost(new BigDecimal("100"));

        Order order = new Order();
        order.setUserId("u1");
        order.setAsset(asset);
        order.setType(Order.OrderType.SELL);
        order.setAmount(new BigDecimal("10"));

        when(balanceRepository.findById("u1:a1")).thenReturn(Optional.of(existing));

        balanceService.updateBalanceOnExecution(order);

        verify(balanceRepository, times(1)).save(argThat(balance -> 
            balance.getAmount().equals(new BigDecimal("20")) &&
            balance.getWeightedAverageCost().equals(new BigDecimal("100"))
        ));
    }
}
