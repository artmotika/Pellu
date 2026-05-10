package org.artmotika.authservice.controller;

import org.artmotika.authservice.service.AuthService;
import org.artmotika.common.dto.AuthRequestDto;
import org.artmotika.common.dto.AuthResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private org.artmotika.authservice.mapper.UserMapper userMapper;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_ShouldReturnToken() {
        AuthResponseDto resp = new AuthResponseDto("token123", "u1");
        when(authService.register("w1", "p1")).thenReturn(resp);
        ResponseEntity<AuthResponseDto> result = authController.register(new AuthRequestDto("w1", "p1"));
        assertEquals("token123", result.getBody().getToken());
        assertEquals("u1", result.getBody().getUserId());
    }

    @Test
    void login_ShouldReturnToken() {
        AuthResponseDto resp = new AuthResponseDto("token123", "u1");
        when(authService.login("w1", "p1")).thenReturn(resp);
        ResponseEntity<AuthResponseDto> result = authController.login(new AuthRequestDto("w1", "p1"));
        assertEquals("token123", result.getBody().getToken());
        assertEquals("u1", result.getBody().getUserId());
    }
}
