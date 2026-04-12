package org.artmotika.apigatewayservice.repo;

import org.artmotika.apigatewayservice.model.InvestorLimit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorLimitRepository extends JpaRepository<InvestorLimit, String> {
}
