package org.artmotika.tradingengineservice.repo;

import org.artmotika.tradingengineservice.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, String> {
}
