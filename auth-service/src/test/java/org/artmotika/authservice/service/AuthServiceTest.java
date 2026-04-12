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

    @Test
    void register_ShouldSaveUserAndSendKafkaEvent() {
        when(passwordEncoder.encode(any())).thenReturn("hashed_pass");
        
        String token = authService.register("wallet123", "password");
        
        assertNotNull(token);
        verify(userRepository, times(1)).save(any(User.class));
        verify(kafkaTemplate, times(1)).send(eq("users.registered"), anyString());
    }

    @Test
    void login_ShouldReturnToken_WhenCredentialsValid() {
        User user = User.builder()
                .id("user-1")
                .walletAddress("wallet123")
                .password("hashed_pass")
                .build();
        
        when(userRepository.findByWalletAddress("wallet123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed_pass")).thenReturn(true);
        
        String token = authService.login("wallet123", "password");
        
        assertNotNull(token);
    }

    @Test
    void login_ShouldThrowException_WhenPasswordInvalid() {
        User user = User.builder()
                .walletAddress("wallet123")
                .password("hashed_pass")
                .build();
        
        when(userRepository.findByWalletAddress("wallet123")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pass", "hashed_pass")).thenReturn(false);
        
        assertThrows(RuntimeException.class, () -> authService.login("wallet123", "wrong_pass"));
    }
}
