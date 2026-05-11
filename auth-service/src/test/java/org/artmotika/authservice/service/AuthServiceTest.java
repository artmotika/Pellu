package org.artmotika.authservice.service;

import org.artmotika.authservice.model.User;
import org.artmotika.authservice.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.artmotika.common.dto.KycStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
    }

    @Test
    void register_ShouldSaveUserAndSendKafkaEvent() {
        when(passwordEncoder.encode(any())).thenReturn("hashed_pass");
        
        String wallet = "wallet123";
        var response = authService.register(wallet, "password");
        
        assertNotNull(response.getToken());
        verify(userRepository, times(1)).save(any(User.class));
        verify(kafkaTemplate, times(1)).send(eq("users.registered"), eq(wallet));
    }

    @Test
    void login_ShouldReturnToken_WhenCredentialsValid() {
        User user = User.builder()
                .id("user-1")
                .walletAddress("wallet123")
                .password("hashed_pass")
                .kycStatus(KycStatus.APPROVED)
                .build();
        
        when(userRepository.findByWalletAddress("wallet123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed_pass")).thenReturn(true);
        
        var response = authService.login("wallet123", "password");
        
        assertNotNull(response.getToken());
        assertEquals("user-1", response.getUserId());
    }

    @Test
    void login_ShouldThrowException_WhenPasswordInvalid() {
        User user = User.builder()
                .walletAddress("wallet123")
                .password("hashed_pass")
                .kycStatus(KycStatus.PENDING)
                .build();
        
        when(userRepository.findByWalletAddress("wallet123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pass", "hashed_pass")).thenReturn(false);
        
        assertThrows(RuntimeException.class, () -> authService.login("wallet123", "wrong_pass"));
    }
}
