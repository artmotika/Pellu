package org.artmotika.tradingengineservice.repo;

import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.math.BigDecimal;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByAssetAndTypeAndPriceAndStatus(
        Asset asset, Order.OrderType type, BigDecimal price, Order.OrderStatus status);
}
