package org.artmotika.apigatewayservice.repo;

import org.artmotika.apigatewayservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
