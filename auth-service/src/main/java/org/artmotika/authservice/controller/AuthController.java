package org.artmotika.authservice.controller;

import lombok.RequiredArgsConstructor;
import org.artmotika.authservice.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final org.artmotika.authservice.mapper.UserMapper userMapper;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> req) {
        String token = authService.register(req.get("wallet"), req.get("password"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("token", token));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> req) {
        String token = authService.login(req.get("wallet"), req.get("password"));
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/esia/login")
    public ResponseEntity<Map<String, String>> loginEsia(@RequestParam String code) {
        String token = authService.loginViaEsia(code);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<org.artmotika.common.dto.UserDto> getUser(@PathVariable String id) {
        org.artmotika.authservice.model.User user = authService.getUser(id);
        return ResponseEntity.ok(userMapper.toDto(user));
    }
}
