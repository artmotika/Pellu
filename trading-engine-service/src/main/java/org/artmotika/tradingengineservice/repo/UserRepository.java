package org.artmotika.tradingengineservice.repo;

import org.artmotika.tradingengineservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
