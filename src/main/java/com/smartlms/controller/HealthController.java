package com.smartlms.controller;

import com.smartlms.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple health-check controller.
 * Used to verify the application is running and the API layer is reachable.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> healthData = new HashMap<>();
        healthData.put("status", "UP");
        healthData.put("application", "SmartLMS");
        healthData.put("version", "1.0.0-SNAPSHOT");
        healthData.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(ApiResponse.success("SmartLMS is running", healthData));
    }
}
