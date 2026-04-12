package org.artmotika.authservice.controller;

import org.artmotika.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_ShouldReturnToken() {
        when(authService.register("w1", "p1")).thenReturn("token123");
        Map<String, String> result = authController.register(Map.of("wallet", "w1", "password", "p1"));
        assertEquals("token123", result.get("token"));
    }

    @Test
    void login_ShouldReturnToken() {
        when(authService.login("w1", "p1")).thenReturn("token123");
        Map<String, String> result = authController.login(Map.of("wallet", "w1", "password", "p1"));
        assertEquals("token123", result.get("token"));
    }
}
