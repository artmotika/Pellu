package org.artmotika.tradingengineservice.controller;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.common.dto.InvestorLimitDto;
import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.repo.AssetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradingApiController {
    private final AssetRepository assetRepository;

    @GetMapping("/assets/{id}")
    public ResponseEntity<AssetDto> getAsset(@PathVariable String id) {
        Optional<Asset> assetOpt = assetRepository.findById(id);
        if (assetOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Asset asset = assetOpt.get();
        AssetDto dto = AssetDto.builder()
                .id(asset.getId())
                .name(asset.getName())
                .totalSupply(asset.getTotalSupply())
                .type(asset.getType())
                .status(asset.getStatus())
                .ipoPrice(asset.getIpoPrice())
                .tradeUnlockTimestamp(asset.getTradeUnlockTimestamp())
                .solanaMintAddress(asset.getSolanaMintAddress())
                .build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/limits/{userId}")
    public ResponseEntity<InvestorLimitDto> getLimit(@PathVariable String userId) {
        // Mocking limit for now, or you can use a real repository if it exists
        InvestorLimitDto dto = InvestorLimitDto.builder()
                .userId(userId)
                .annualInvestment(BigDecimal.ZERO)
                .build();
        return ResponseEntity.ok(dto);
    }
}
