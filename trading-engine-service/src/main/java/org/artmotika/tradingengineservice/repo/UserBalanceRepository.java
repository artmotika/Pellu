package org.artmotika.tradingengineservice.repo;

import org.artmotika.tradingengineservice.model.UserBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserBalanceRepository extends JpaRepository<UserBalance, String> {
    Optional<UserBalance> findByUserIdAndAssetId(String userId, String assetId);
    List<UserBalance> findByAssetId(String assetId);
}
