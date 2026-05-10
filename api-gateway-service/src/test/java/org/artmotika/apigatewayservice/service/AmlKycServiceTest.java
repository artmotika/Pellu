package org.artmotika.apigatewayservice.service;

import org.artmotika.apigatewayservice.service.validator.OrderValidator;
import org.artmotika.common.dto.KycStatus;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.common.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmlKycServiceTest {

    @Mock
    private KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    
    @Spy
    private List<OrderValidator> validators = new ArrayList<>();

    @InjectMocks
    private AmlKycService amlKycService;

    private UserDto approvedUser;

    @BeforeEach
    void setUp() {
        approvedUser = UserDto.builder()
                .id("user-1")
                .walletAddress("wallet123")
                .kycStatus(KycStatus.APPROVED)
                .amlRiskScore(0)
                .build();
        
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(approvedUser, null, Collections.emptyList())
        );
    }

    @Test
    void processOrder_ShouldSendToKafka_WhenValid() {
        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        order.setAmount(new BigDecimal("100"));
        
        amlKycService.processOrder(order);
        
        assertEquals("wallet123", order.getWalletAddress());
        verify(kafkaTemplate, times(1)).send(eq("orders.created"), eq(order));
    }

    @Test
    void processOrder_ShouldThrowException_WhenAValidatorFails() {
        OrderValidator failingValidator = mock(OrderValidator.class);
        doThrow(new RuntimeException("Validation Failed")).when(failingValidator).validate(any(), any());
        validators.add(failingValidator);

        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        
        assertThrows(RuntimeException.class, () -> amlKycService.processOrder(order));
    }
}
