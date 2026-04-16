package org.artmotika.apigatewayservice.service;

import org.artmotika.common.dto.KycStatus;
import org.artmotika.common.dto.UserDto;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmlKycServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private KafkaTemplate<String, OrderRequestDto> kafkaTemplate;
    
    @Spy
    private List<OrderValidator> validators = new ArrayList<>();

    @InjectMocks
    private AmlKycService amlKycService;

    private UserDto approvedUser;
    private final String authServiceUrl = "http://auth-service:8083";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(amlKycService, "authServiceUrl", authServiceUrl);
        approvedUser = UserDto.builder()
                .id("user-1")
                .kycStatus(KycStatus.APPROVED)
                .amlRiskScore(0)
                .build();
    }

    @Test
    void processOrder_ShouldSendToKafka_WhenValid() {
        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        order.setAmount(new BigDecimal("100"));
        
        when(restTemplate.getForObject(authServiceUrl + "/api/auth/users/user-1", UserDto.class))
                .thenReturn(approvedUser);
        
        amlKycService.processOrder(order);
        
        verify(kafkaTemplate, times(1)).send(eq("orders.created"), eq(order));
    }

    @Test
    void processOrder_ShouldThrowException_WhenAValidatorFails() {
        OrderValidator failingValidator = mock(OrderValidator.class);
        doThrow(new RuntimeException("Validation Failed")).when(failingValidator).validate(any(), any());
        validators.add(failingValidator);

        OrderRequestDto order = new OrderRequestDto();
        order.setUserId("user-1");
        
        when(restTemplate.getForObject(authServiceUrl + "/api/auth/users/user-1", UserDto.class))
                .thenReturn(approvedUser);
        
        assertThrows(RuntimeException.class, () -> amlKycService.processOrder(order));
    }
}
