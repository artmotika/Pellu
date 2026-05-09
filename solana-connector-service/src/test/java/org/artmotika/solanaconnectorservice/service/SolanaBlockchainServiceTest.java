package org.artmotika.solanaconnectorservice.service;

import org.artmotika.common.dto.AssetDto;
import org.artmotika.solanaconnectorservice.dto.VotingEventDto;
import org.artmotika.solanaconnectorservice.dto.ValidatedOrderEventDto;
import org.artmotika.solanaconnectorservice.dto.ExecutionResultDto;
import org.artmotika.solanaconnectorservice.dto.KycUpdateEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolanaBlockchainServiceTest {

    private static final String VALID_PUBKEY = "vines1vzrYbzduYv9bP5McaS1quZ756C87S9ER69s9P";

    @Mock
    private KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;

    @Mock
    private org.artmotika.solanaconnectorservice.config.SolanaProperties solanaProperties;

    @InjectMocks
    private SolanaBlockchainService solanaBlockchainService;

    @BeforeEach
    void setUp() {
        org.artmotika.solanaconnectorservice.config.SolanaProperties.Program program = mock(org.artmotika.solanaconnectorservice.config.SolanaProperties.Program.class);
        when(program.getId()).thenReturn("Dfa1111111111111111111111111111111111111111");
        when(program.getTokenProgramId()).thenReturn("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
        when(program.getAssociatedTokenProgramId()).thenReturn("ATokenGPvbdQxrXJw2Qy7u7L8xN8yA9f9f9f9f9f9f9");
        when(program.getSystemProgramId()).thenReturn("11111111111111111111111111111111");
        
        org.artmotika.solanaconnectorservice.config.SolanaProperties.Admin admin = mock(org.artmotika.solanaconnectorservice.config.SolanaProperties.Admin.class);
        when(admin.getPrivateKey()).thenReturn("");

        when(solanaProperties.getProgram()).thenReturn(program);
        when(solanaProperties.getAdmin()).thenReturn(admin);
        when(solanaProperties.getRpcUrl()).thenReturn("https://api.devnet.solana.com");
        
        when(solanaProperties.getDiscriminators()).thenReturn(Map.of(
            "create-asset", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "toggle-ipo", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "start-voting", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "update-kyc", List.of(0, 0, 0, 0, 0, 0, 0, 0),
            "dividend-payout", List.of(0, 0, 0, 0, 0, 0, 0, 0)
        ));

        org.artmotika.solanaconnectorservice.config.SolanaProperties.Pda pda = mock(org.artmotika.solanaconnectorservice.config.SolanaProperties.Pda.class);
        when(pda.getPrefix()).thenReturn(Map.of(
            "registry", "registry",
            "voting", "voting",
            "user", "user"
        ));
        when(solanaProperties.getPda()).thenReturn(pda);

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
        solanaBlockchainService.toggleIpoOnChain(Map.of("assetId", "a1", "status", "IPO_ACTIVE"));
    }

    @Test
    void startVotingOnChain_ShouldNotCrash() {
        VotingEventDto event = new VotingEventDto();
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
        Map<String, Object> event = Map.of(
            "assetId", "a1",
            "userWallet", VALID_PUBKEY,
            "sourceTokenAccount", VALID_PUBKEY,
            "userTokenAccount", VALID_PUBKEY,
            "amount", 100L
        );
        solanaBlockchainService.executeDividendPayout(event);
    }
}
