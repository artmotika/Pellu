package org.artmotika.apigatewayservice.controller;

import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AdminController adminController;

    @Test
    void updateKyc_ShouldSaveAndSendEvent() {
        User user = new User();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        String result = adminController.updateKyc(Map.of("userId", "u1", "approved", true));

        assertEquals("KYC Updated", result);
        assertEquals("APPROVED", user.getKycStatus());
        verify(userRepository, times(1)).save(user);
        verify(kafkaTemplate, times(1)).send(eq("kyc.updated"), any());
    }

    @Test
    void freeze_ShouldSendEvent() {
        String result = adminController.freeze(Map.of("userId", "u1", "freeze", true));
        assertEquals("Freeze Command Sent", result);
        verify(kafkaTemplate, times(1)).send(eq("aml.frozen"), any());
    }

    @Test
    void clawback_ShouldSendEvent() {
        String result = adminController.clawback(Map.of("target", "t1"));
        assertEquals("Clawback Command Sent", result);
        verify(kafkaTemplate, times(1)).send(eq("admin.clawback"), any());
    }
}
