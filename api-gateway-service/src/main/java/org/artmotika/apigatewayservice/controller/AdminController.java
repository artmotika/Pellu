package org.artmotika.apigatewayservice.controller;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final org.artmotika.apigatewayservice.mapper.AdminMapper adminMapper;

    @PostMapping("/assets")
    public ResponseEntity<AssetDto> createAsset(@RequestBody AssetCreateRequestDto req) {
        AssetDto asset = adminMapper.toAssetDto(req);
        kafkaTemplate.send("assets.created", asset);
        return ResponseEntity.ok(asset);
    }

    @PostMapping("/ipo/start")
    public ResponseEntity<String> startIpo(@RequestParam String assetId) {
        kafkaTemplate.send("ipo.status", Map.of("assetId", assetId, "status", AssetStatus.IPO_ACTIVE));
        return ResponseEntity.ok("IPO start command sent");
    }

    @PostMapping("/ipo/finalize")
    public ResponseEntity<String> finalizeIpo(@RequestParam String assetId) {
        kafkaTemplate.send("ipo.status", Map.of("assetId", assetId, "status", AssetStatus.TRADING));
        return ResponseEntity.ok("IPO finalize command sent");
    }

    @PostMapping("/assets/suspend")
    public ResponseEntity<String> suspendAsset(@RequestParam String assetId) {
        kafkaTemplate.send("ipo.status", Map.of("assetId", assetId, "status", AssetStatus.SUSPENDED));
        return ResponseEntity.ok("Asset suspension command sent");
    }

    @PostMapping("/vote")
    public ResponseEntity<Map<String, String>> startVote(@RequestBody VoteCreateRequestDto req) {
        String actionId = req.getActionId() != null ? req.getActionId() : UUID.randomUUID().toString();
        req.setActionId(actionId);
        
        kafkaTemplate.send("vote.started", req);
        return ResponseEntity.ok(Map.of("actionId", actionId, "status", "Voting initiated"));
    }

    @PostMapping("/kyc")
    public ResponseEntity<String> updateKyc(@RequestBody KycUpdateRequestDto req) {
        kafkaTemplate.send("kyc.updated", req);
        return ResponseEntity.ok("KYC Update command sent");
    }

    @PostMapping("/freeze")
    public ResponseEntity<String> freeze(@RequestBody FreezeRequestDto req) {
        kafkaTemplate.send("aml.frozen", req);
        return ResponseEntity.ok("Freeze command sent");
    }

    @PostMapping("/clawback")
    public ResponseEntity<String> clawback(@RequestBody ClawbackRequestDto req) {
        kafkaTemplate.send("admin.clawback", req);
        return ResponseEntity.ok("Clawback command sent");
    }

    @PostMapping("/risk")
    public ResponseEntity<String> updateRiskScore(@RequestBody RiskScoreUpdateRequestDto req) {
        kafkaTemplate.send("aml.risk_score.updated", req);
        return ResponseEntity.ok("Risk score update command sent");
    }

    @PostMapping("/dividends")
    public ResponseEntity<String> triggerDividend(@RequestBody DividendTriggerRequestDto req) {
        kafkaTemplate.send("dividend.trigger", req);
        return ResponseEntity.ok("Dividend trigger command sent");
    }
}
