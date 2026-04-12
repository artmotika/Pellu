package org.artmotika.solanaconnectorservice.service;

import org.artmotika.solanaconnectorservice.dto.ValidatedOrderEventDto;
import org.artmotika.solanaconnectorservice.dto.ExecutionResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolanaBlockchainServiceTest {

    @Mock
    private KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;

    @InjectMocks
    private SolanaBlockchainService solanaBlockchainService;

    @Test
    void tradeDfa_ShouldAttemptTransactionAndSendEvent() {
        ValidatedOrderEventDto event = new ValidatedOrderEventDto();
        event.setId("order-1");
        event.setAmount(new BigDecimal("100"));

        // Note: Real RPC calls are avoided in unit tests by mocking or by the fact 
        // that RpcClient/Account initialization doesn't hit the network immediately 
        // in some SDK versions, or we expect it to fail gracefully in test env.
        // In a real scenario, we'd mock RpcClient.
        
        solanaBlockchainService.tradeDfa(event);

        verify(kafkaTemplate, timeout(10000).times(1)).send(eq("trades.executed"), any(ExecutionResultDto.class));
    }
}
