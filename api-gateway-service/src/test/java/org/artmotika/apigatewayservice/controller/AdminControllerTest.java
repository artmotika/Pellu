package org.artmotika.apigatewayservice.controller;

import org.artmotika.common.dto.*;
import org.artmotika.apigatewayservice.mapper.AdminMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private AdminMapper adminMapper;

    @InjectMocks
    private AdminController adminController;

    @Test
    void createAsset_ShouldSendEvent() {
        AssetCreateRequestDto req = AssetCreateRequestDto.builder()
                .name("Test Asset")
                .totalSupply(1000000L)
                .type(AssetType.EQUITY)
                .ipoPrice(new BigDecimal("10.50"))
                .legalDocHash("HASH123")
                .tradeUnlockTimestamp(1700000000L)
                .build();

        AssetDto mockDto = AssetDto.builder()
                .id("a1")
                .name("Test Asset")
                .legalDocHash("HASH123")
                .build();

        when(adminMapper.toAssetDto(any())).thenReturn(mockDto);

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
        VoteCreateRequestDto req = VoteCreateRequestDto.builder()
                .assetId("a1")
                .title("Split?")
                .build();
        ResponseEntity<Map<String, String>> response = adminController.startVote(req);
        assertEquals("Voting initiated", response.getBody().get("status"));
        verify(kafkaTemplate, times(1)).send(eq("vote.started"), any());
    }

    @Test
    void updateKyc_ShouldSendEvent() {
        KycUpdateRequestDto req = KycUpdateRequestDto.builder()
                .userId("u1")
                .approved(true)
                .build();
        ResponseEntity<String> response = adminController.updateKyc(req);

        assertEquals("KYC Update command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("kyc.updated"), any());
    }

    @Test
    void freeze_ShouldSendEvent() {
        FreezeRequestDto req = FreezeRequestDto.builder()
                .userId("u1")
                .freeze(true)
                .build();
        ResponseEntity<String> response = adminController.freeze(req);
        assertEquals("Freeze command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("aml.frozen"), any());
    }

    @Test
    void clawback_ShouldSendEvent() {
        ClawbackRequestDto req = ClawbackRequestDto.builder()
                .target("t1")
                .build();
        ResponseEntity<String> response = adminController.clawback(req);
        assertEquals("Clawback command sent", response.getBody());
        verify(kafkaTemplate, times(1)).send(eq("admin.clawback"), any());
    }
}
