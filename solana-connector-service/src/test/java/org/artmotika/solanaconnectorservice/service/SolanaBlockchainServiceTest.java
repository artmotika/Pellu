package org.artmotika.solanaconnectorservice.service;

import org.artmotika.common.dto.*;
import org.artmotika.solanaconnectorservice.dto.ClawbackEventDto;
import org.artmotika.solanaconnectorservice.dto.ExecutionResultDto;
import org.artmotika.solanaconnectorservice.dto.KycUpdateEventDto;
import org.artmotika.solanaconnectorservice.dto.ValidatedOrderEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolanaBlockchainServiceTest {

    private static final String VALID_PUBKEY = "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R";

    @Mock
    private KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;

    @Mock
    private org.artmotika.solanaconnectorservice.config.SolanaProperties solanaProperties;

    @InjectMocks
    private SolanaBlockchainService solanaBlockchainService;

    @BeforeEach
    void setUp() {
        org.artmotika.solanaconnectorservice.config.SolanaProperties.Program program = new org.artmotika.solanaconnectorservice.config.SolanaProperties.Program();
        program.setId(VALID_PUBKEY);
        program.setTokenProgramId(VALID_PUBKEY);
        program.setAssociatedTokenProgramId(VALID_PUBKEY);
        program.setSystemProgramId(VALID_PUBKEY);
        
        org.artmotika.solanaconnectorservice.config.SolanaProperties.Admin admin = new org.artmotika.solanaconnectorservice.config.SolanaProperties.Admin();
        admin.setPrivateKey("3JhdAWiLtwKzjiqamDsNRz54QnvFs7myEYvYQiuAccWraULzxouRjiqnXinscEfymSdvDrSNTaPCW4xDBTYANov9");

        org.artmotika.solanaconnectorservice.config.SolanaProperties.Pda pda = new org.artmotika.solanaconnectorservice.config.SolanaProperties.Pda();
        pda.setPrefix(Map.of(
            "registry", "registry",
            "voting", "voting",
            "user", "user"
        ));

        when(solanaProperties.getProgram()).thenReturn(program);
        when(solanaProperties.getAdmin()).thenReturn(admin);
        when(solanaProperties.getRpcUrl()).thenReturn("http://localhost:8899");
        when(solanaProperties.getPda()).thenReturn(pda);
        when(solanaProperties.getDiscriminators()).thenReturn(Map.of(
            "create-asset", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "toggle-ipo", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "start-voting", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "update-kyc", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "dividend-payout", List.of(0, 0, 0, 0, 0, 0, 0, 0)
        ));

        solanaBlockchainService.init();
    }

    @Test
    void createAssetOnChain_ShouldNotCrash() {
        AssetDto asset = new AssetDto();
        asset.setId("a1");
        asset.setName("Test Asset");
        asset.setTotalSupply(1000L);
        asset.setSolanaMintAddress(VALID_PUBKEY);
        solanaBlockchainService.createAssetOnChain(asset);
    }

    @Test
    void toggleIpoOnChain_ShouldNotCrash() {
        solanaBlockchainService.toggleIpoOnChain(new IpoStatusUpdateDto("a1", AssetStatus.IPO_ACTIVE));
    }

    @Test
    void startVotingOnChain_ShouldNotCrash() {
        VoteStartedEventDto event = new VoteStartedEventDto();
        event.setActionId("v1");
        event.setAssetId("a1");
        event.setTitle("Test Vote");
        event.setOptions(List.of("A", "B"));
        solanaBlockchainService.startVotingOnChain(event);
    }

    @Test
    void updateKycOnChain_ShouldNotCrash() {
        KycUpdateEventDto event = new KycUpdateEventDto();
        event.setAssetId("a1");
        event.setUserWallet(VALID_PUBKEY);
        event.setApproved(true);
        solanaBlockchainService.updateKycOnChain(event);
    }

    @Test
    void executeDividendPayout_ShouldNotCrash() {
        DividendPayoutEventDto event = DividendPayoutEventDto.builder()
            .assetId("a1")
            .userWallet(VALID_PUBKEY)
            .sourceTokenAccount(VALID_PUBKEY)
            .amount(100L)
            .build();
        solanaBlockchainService.executeDividendPayout(event);
    }
}
