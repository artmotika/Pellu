package org.artmotika.tradingengineservice.repo;

import org.artmotika.tradingengineservice.model.CorporateAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorporateActionRepository extends JpaRepository<CorporateAction, String> {
}
