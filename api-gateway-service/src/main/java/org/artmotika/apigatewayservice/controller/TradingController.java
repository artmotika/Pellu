package org.artmotika.apigatewayservice.controller;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.apigatewayservice.service.StateCacheService;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.common.dto.InvestorLimitDto;
import org.artmotika.common.dto.UserDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/trading")
@RequiredArgsConstructor
public class TradingController {

    private final StateCacheService cacheService;

    @PostConstruct
    public void init() {
        log.info("TradingController initialized and active");
    }

    @GetMapping("/assets/{assetId}")
    public ResponseEntity<AssetDto> getAsset(@PathVariable String assetId) {
        log.debug("Get asset request for: {}", assetId);
        AssetDto asset = cacheService.getAsset(assetId);
        if (asset == null) {
            log.warn("Asset not found in cache: {}", assetId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(asset);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDto user) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }


    @GetMapping("/limits/{userId}")
    public ResponseEntity<InvestorLimitDto> getLimit(@PathVariable String userId) {
        log.debug("Get limit request for user: {}", userId);
        InvestorLimitDto limit = cacheService.getUserLimit(userId);
        if (limit == null) {
            return ResponseEntity.ok(InvestorLimitDto.builder()
                    .userId(userId)
                    .annualInvestment(BigDecimal.ZERO)
                    .lastReset(LocalDateTime.now())
                    .build());
        }
        return ResponseEntity.ok(limit);
    }
}
