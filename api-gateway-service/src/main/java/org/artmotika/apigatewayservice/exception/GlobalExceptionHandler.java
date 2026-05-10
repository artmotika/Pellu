package org.artmotika.apigatewayservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AmlViolationException.class)
    public ResponseEntity<Map<String, String>> handleAmlViolation(AmlViolationException e) {
        log.warn("AML Violation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Compliance Violation", "message", e.getMessage()));
    }

    @ExceptionHandler(KycNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleKycNotVerified(KycNotVerifiedException e) {
        log.warn("KYC Violation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "KYC Required", "message", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        log.error("Unhandled error: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal Error", "message", e.getMessage()));
    }
}
