package org.artmotika.apigatewayservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/votes")
@RequiredArgsConstructor
public class VotingController {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/{votingId}/cast")
    public ResponseEntity<String> castVote(@PathVariable String votingId, @RequestBody Map<String, Object> req) {
        // req should contain: userId, optionIndex
        Map<String, Object> event = new java.util.HashMap<>(req);
        event.put("votingId", votingId);
        kafkaTemplate.send("vote.cast", event);
        return ResponseEntity.accepted().body("Vote cast command sent");
    }
}
