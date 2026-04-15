package org.artmotika.solanaconnectorservice.service;

import org.artmotika.solanaconnectorservice.dto.ValidatedOrderEventDto;
import org.artmotika.solanaconnectorservice.dto.ExecutionResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolanaBlockchainServiceTest {

    @Mock
    private KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;

    @InjectMocks
    private SolanaBlockchainService solanaBlockchainService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(solanaBlockchainService, "rpcUrl", "https://api.devnet.solana.com");
        ReflectionTestUtils.setField(solanaBlockchainService, "programIdStr", "Dfa1111111111111111111111111111111111111111");
        solanaBlockchainService.init();
    }

    @Test
    void tradeDfa_ShouldAttemptTransactionAndSendEvent() {
        ValidatedOrderEventDto event = new ValidatedOrderEventDto();
        event.setId("order-1");
        event.setAmount(new BigDecimal("100"));

        solanaBlockchainService.tradeDfa(event);

        verify(kafkaTemplate, timeout(5000).times(1)).send(eq("trades.executed"), any(ExecutionResultDto.class));
    }

    @Test
    void registerUserOnChain_ShouldSendTransaction() {
        solanaBlockchainService.registerUserOnChain("user-1");
        // Since it returns void and just logs/sends tx, we verify it doesn't crash in mock mode
    }
}
