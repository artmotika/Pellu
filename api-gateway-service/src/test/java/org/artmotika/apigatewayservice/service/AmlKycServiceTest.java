package org.artmotika.apigatewayservice.service;

import org.artmotika.common.dto.KycStatus;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.artmotika.apigatewayservice.service.validator.OrderValidator;
import org.artmotika.common.dto.OrderRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmlKycServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    
    @Spy
    private List<OrderValidator> validators = new ArrayList<>();

    @InjectMocks
    private AmlKycService amlKycService;

    private User approvedUser;

    @BeforeEach
    void setUp() {
        approvedUser = new User();
        approvedUser.setId("user-1");
        approvedUser.setKycStatus(KycStatus.APPROVED);
        approvedUser.setAmlRiskScore(0);
    }

    @Test
    void processOrder_ShouldSendToKafka_WhenValid() {
        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        order.setAmount(new BigDecimal("100"));
        
        when(userRepository.findById("user-1")).thenReturn(Optional.of(approvedUser));
        
        amlKycService.processOrder(order);
        
        verify(kafkaTemplate, times(1)).send(eq("orders.created"), eq(order));
        validators.forEach(v -> verify(v).validate(eq(order), eq(approvedUser)));
    }

    @Test
    void processOrder_ShouldThrowException_WhenAValidatorFails() {
        OrderValidator failingValidator = mock(OrderValidator.class);
        doThrow(new RuntimeException("Validation Failed")).when(failingValidator).validate(any(), any());
        validators.add(failingValidator);

        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        
        when(userRepository.findById("user-1")).thenReturn(Optional.of(approvedUser));
        
        assertThrows(RuntimeException.class, () -> amlKycService.processOrder(order));
    }
}
