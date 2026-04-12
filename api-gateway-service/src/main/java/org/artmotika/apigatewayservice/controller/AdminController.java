package org.artmotika.apigatewayservice.controller;

import lombok.RequiredArgsConstructor;
import org.artmotika.apigatewayservice.model.User;
import org.artmotika.apigatewayservice.repo.UserRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/kyc")
    public String updateKyc(@RequestBody Map<String, Object> req) {
        String userId = (String) req.get("userId");
        boolean approved = (Boolean) req.get("approved");
        
        User user = userRepository.findById(userId).orElseThrow();
        user.setKycStatus(approved ? "APPROVED" : "REJECTED");
        userRepository.save(user);
        
        kafkaTemplate.send("kyc.updated", Map.of("userId", userId, "approved", approved));
        return "KYC Updated";
    }

    @PostMapping("/freeze")
    public String freeze(@RequestBody Map<String, Object> req) {
        String userId = (String) req.get("userId");
        boolean freeze = (Boolean) req.get("freeze");
        
        kafkaTemplate.send("aml.frozen", Map.of("userId", userId, "freeze", freeze));
        return "Freeze Command Sent";
    }

    @PostMapping("/clawback")
    public String clawback(@RequestBody Map<String, Object> req) {
        kafkaTemplate.send("admin.clawback", req);
        return "Clawback Command Sent";
    }
}
