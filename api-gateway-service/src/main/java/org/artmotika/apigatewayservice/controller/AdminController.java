package org.artmotika.apigatewayservice.controller;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.common.dto.AssetStatus;
import org.artmotika.common.dto.AssetType;
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

    @PostMapping("/assets")
    public ResponseEntity<AssetDto> createAsset(@RequestBody Map<String, Object> req) {
        AssetDto asset = AssetDto.builder()
                .id(UUID.randomUUID().toString())
                .name((String) req.get("name"))
                .totalSupply(((Number) req.get("totalSupply")).longValue())
                .type(AssetType.valueOf((String) req.get("type")))
                .status(AssetStatus.IPO_PLANNED)
                .ipoPrice(new BigDecimal(req.get("ipoPrice").toString()))
                .legalDocHash((String) req.getOrDefault("legalDocHash", "MOCK_HASH"))
                .tradeUnlockTimestamp(((Number) req.getOrDefault("tradeUnlockTimestamp", System.currentTimeMillis() / 1000 + 3600)).longValue())
                .solanaMintAddress("MOCK_MINT_" + UUID.randomUUID().toString().substring(0, 8))
                .build();

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

    @PostMapping("/vote")
    public ResponseEntity<String> startVote(@RequestBody Map<String, Object> req) {
        Map<String, Object> mutableReq = new java.util.HashMap<>(req);
        if (!mutableReq.containsKey("actionId")) {
            mutableReq.put("actionId", UUID.randomUUID().toString());
        }
        kafkaTemplate.send("vote.started", mutableReq);
        return ResponseEntity.ok("Voting initiated");
    }

    @PostMapping("/kyc")
    public ResponseEntity<String> updateKyc(@RequestBody Map<String, Object> req) {
        kafkaTemplate.send("kyc.updated", req);
        return ResponseEntity.ok("KYC Update command sent");
    }

    @PostMapping("/freeze")
    public ResponseEntity<String> freeze(@RequestBody Map<String, Object> req) {
        kafkaTemplate.send("aml.frozen", req);
        return ResponseEntity.ok("Freeze command sent");
    }

    @PostMapping("/clawback")
    public ResponseEntity<String> clawback(@RequestBody Map<String, Object> req) {
        kafkaTemplate.send("admin.clawback", req);
        return ResponseEntity.ok("Clawback command sent");
    }
}
