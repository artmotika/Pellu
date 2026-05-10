package org.artmotika.authservice.repo;

import org.artmotika.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByWalletAddress(String walletAddress);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.kycStatus = :status WHERE u.id = :id")
    void updateKycStatus(String id, org.artmotika.common.dto.KycStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.frozen = :frozen WHERE u.id = :id")
    void updateFrozen(String id, boolean frozen);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.amlRiskScore = :score WHERE u.id = :id")
    void updateRiskScore(String id, int score);
}
