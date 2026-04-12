package org.artmotika.authservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.artmotika.authservice.model.User;
import org.artmotika.authservice.repo.UserRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final SecretKey KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    public String register(String wallet, String password) {
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .walletAddress(wallet)
                .kycStatus("PENDING")
                .amlRiskScore(0)
                .password(passwordEncoder.encode(password))
                .build();
        userRepository.save(user);
        kafkaTemplate.send("users.registered", user.getId());
        return generateToken(user);
    }

    public String login(String wallet, String password) {
        User user = userRepository.findByWalletAddress(wallet).orElseThrow();
        if (passwordEncoder.matches(password, user.getPassword())) {
            return generateToken(user);
        }
        throw new RuntimeException("Invalid credentials");
    }

    private String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId())
                .claim("wallet", user.getWalletAddress())
                .claim("kycStatus", user.getKycStatus())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(KEY)
                .compact();
    }
}
