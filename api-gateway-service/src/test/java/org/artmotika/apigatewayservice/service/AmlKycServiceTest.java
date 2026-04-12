package org.artmotika.apigatewayservice.service;

import org.artmotika.apigatewayservice.exception.AmlViolationException;
import org.artmotika.apigatewayservice.exception.KycNotVerifiedException;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.artmotika.common.dto.OrderRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
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

    @InjectMocks
    private AmlKycService amlKycService;

    private User approvedUser;

    @BeforeEach
    void setUp() {
        approvedUser = new User();
        approvedUser.setId("user-1");
        approvedUser.setKycStatus("APPROVED");
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
    }

    @Test
    void processOrder_ShouldThrowException_WhenKycPending() {
        approvedUser.setKycStatus("PENDING");
        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        
        when(userRepository.findById("user-1")).thenReturn(Optional.of(approvedUser));
        
        assertThrows(KycNotVerifiedException.class, () -> amlKycService.processOrder(order));
    }

    @Test
    void processOrder_ShouldThrowAmlException_WhenAmountTooHigh() {
        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        order.setAmount(new BigDecimal("2000000")); // Limit is 1,000,000
        
        when(userRepository.findById("user-1")).thenReturn(Optional.of(approvedUser));
        
        assertThrows(AmlViolationException.class, () -> amlKycService.processOrder(order));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void processOrder_ShouldThrowAmlException_WhenFrequencyTooHigh() {
        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        order.setAmount(new BigDecimal("100"));
        
        when(userRepository.findById("user-1")).thenReturn(Optional.of(approvedUser));
        
        for(int i=0; i<5; i++) {
            amlKycService.processOrder(order);
        }
        
        assertThrows(AmlViolationException.class, () -> amlKycService.processOrder(order));
    }
}
