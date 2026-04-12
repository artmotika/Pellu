package org.artmotika.authservice.controller;

import lombok.RequiredArgsConstructor;
import org.artmotika.authservice.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody Map<String, String> req) {
        return Map.of("token", authService.register(req.get("wallet"), req.get("password")));
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> req) {
        return Map.of("token", authService.login(req.get("wallet"), req.get("password")));
    }
}
