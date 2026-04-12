package org.artmotika.tradingengineservice.repo;

import org.artmotika.tradingengineservice.model.TaxLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxLedgerRepository extends JpaRepository<TaxLedger, String> {
}
