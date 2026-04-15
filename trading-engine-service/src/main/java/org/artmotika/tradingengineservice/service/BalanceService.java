package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.UserBalance;
import org.artmotika.tradingengineservice.repo.UserBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private final UserBalanceRepository balanceRepository;

    @Transactional
    public void updateBalanceOnExecution(Order order) {
        String userId = order.getUser().getId();
        String assetId = order.getAsset().getId();
        String id = userId + ":" + assetId;

        UserBalance balance = balanceRepository.findById(id).orElseGet(() -> {
            UserBalance b = new UserBalance();
            b.setId(id);
            b.setUser(order.getUser());
            b.setAsset(order.getAsset());
            b.setAmount(BigDecimal.ZERO);
            b.setWeightedAverageCost(BigDecimal.ZERO);
            return b;
        });

        if (order.getType() == Order.OrderType.BUY) {
            BigDecimal currentAmount = balance.getAmount();
            BigDecimal currentCost = balance.getWeightedAverageCost().multiply(currentAmount);
            BigDecimal newCost = order.getAmount().multiply(order.getPrice());
            
            BigDecimal totalAmount = currentAmount.add(order.getAmount());
            BigDecimal totalCost = currentCost.add(newCost);
            
            balance.setAmount(totalAmount);
            if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                balance.setWeightedAverageCost(totalCost.divide(totalAmount, 4, RoundingMode.HALF_UP));
            }
        } else {
            // SELL: Balance decreases, WAC stays the same but total cost decreases proportionally
            balance.setAmount(balance.getAmount().subtract(order.getAmount()));
        }

        balance.setLastUpdate(LocalDateTime.now());
        balanceRepository.save(balance);
        log.info("Updated balance for User {} on Asset {}: new balance = {}", userId, assetId, balance.getAmount());
    }

    public List<UserBalance> getBalancesByAsset(String assetId) {
        return balanceRepository.findByAssetId(assetId);
    }
    
    public UserBalance getBalance(String userId, String assetId) {
        return balanceRepository.findByUserIdAndAssetId(userId, assetId).orElse(null);
    }
}
