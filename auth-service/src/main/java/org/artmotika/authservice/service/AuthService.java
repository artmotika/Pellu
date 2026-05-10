package org.artmotika.authservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.artmotika.authservice.model.User;
import org.artmotika.authservice.repo.UserRepository;
import org.artmotika.common.dto.AuthResponseDto;
import org.artmotika.common.dto.KycStatus;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${app.security.jwt.secret}")
    private String secretKey;

    private SecretKey getSignInKey() {
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public User getUser(String id) {
        return userRepository.findById(id).orElseThrow();
    }

    @org.springframework.transaction.annotation.Transactional
    public AuthResponseDto register(String wallet, String password) {
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .walletAddress(wallet)
                .kycStatus(KycStatus.PENDING)
                .amlRiskScore(0)
                .role(wallet.startsWith("ADMIN") ? "ROLE_ADMIN" : "ROLE_USER")
                .password(passwordEncoder.encode(password))
                .build();
        userRepository.save(user);
        
        // Fire and forget to minimize latency
        kafkaTemplate.send("users.registered", user.getWalletAddress());
        
        return new AuthResponseDto(generateToken(user), user.getId());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuthResponseDto login(String wallet, String password) {
        User user = userRepository.findByWalletAddress(wallet)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (passwordEncoder.matches(password, user.getPassword())) {
            return new AuthResponseDto(generateToken(user), user.getId());
        }
        throw new RuntimeException("Invalid credentials");
    }

    public AuthResponseDto loginViaEsia(String code) {
        // MOCK: In reality, we'd exchange 'code' for an access token via ESIA API
        String mockWallet = "ESIA_" + UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .walletAddress(mockWallet)
                .kycStatus(KycStatus.APPROVED) // ESIA users are pre-verified
                .qualified(false)    // Default to retail investor
                .amlRiskScore(0)
                .password(passwordEncoder.encode("ESIA_OAUTH_" + code))
                .build();
        userRepository.save(user);
        kafkaTemplate.send("users.registered", user.getWalletAddress());
        return new AuthResponseDto(generateToken(user), user.getId());
    }

    private String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId())
                .claim("wallet", user.getWalletAddress())
                .claim("kycStatus", user.getKycStatus())
                .claim("role", user.getRole())
                .claim("qualified", user.isQualified())
                .claim("frozen", user.isFrozen())
                .claim("amlRiskScore", user.getAmlRiskScore())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}
