package org.artmotika.apigatewayservice.controller;

import org.artmotika.common.dto.AssetDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AdminController adminController;

    @Test
    void createAsset_ShouldSendEvent() {
        Map<String, Object> req = Map.of(
                "name", "Test Asset",
                "totalSupply", 1000000L,
                "type", "EQUITY",
                "ipoPrice", "10.50",
                "legalDocHash", "HASH123",
                "tradeUnlockTimestamp", 1700000000L
        );

        ResponseEntity<AssetDto> response = adminController.createAsset(req);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().getId());
        assertEquals("Test Asset", response.getBody().getName());
        assertEquals("HASH123", response.getBody().getLegalDocHash());
        verify(kafkaTemplate, times(1)).send(eq("assets.created"), any());
    }

    @Test
    void startIpo_ShouldSendEvent() {
        ResponseEntity<String> response = adminController.startIpo("a1");

        assertEquals("IPO start command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("ipo.status"), any());
    }

    @Test
    void finalizeIpo_ShouldSendEvent() {
        ResponseEntity<String> response = adminController.finalizeIpo("a1");

        assertEquals("IPO finalize command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("ipo.status"), any());
    }

    @Test
    void startVote_ShouldSendEvent() {
        Map<String, Object> req = Map.of("assetId", "a1", "title", "Split?");
        ResponseEntity<String> response = adminController.startVote(req);
        assertEquals("Voting initiated", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("vote.started"), any(Map.class));
    }

    @Test
    void updateKyc_ShouldSendEvent() {
        ResponseEntity<String> response = adminController.updateKyc(Map.of("userId", "u1", "approved", true));

        assertEquals("KYC Update command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("kyc.updated"), any());
    }

    @Test
    void freeze_ShouldSendEvent() {
        ResponseEntity<String> response = adminController.freeze(Map.of("userId", "u1", "freeze", true));
        assertEquals("Freeze command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("aml.frozen"), any());
    }

    @Test
    void clawback_ShouldSendEvent() {
        ResponseEntity<String> response = adminController.clawback(Map.of("target", "t1"));
        assertEquals("Clawback command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("admin.clawback"), any());
    }
}
