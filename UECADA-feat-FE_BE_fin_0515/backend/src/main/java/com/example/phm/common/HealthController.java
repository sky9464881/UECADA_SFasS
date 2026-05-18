package com.example.phm.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "service", "smart-factory-phm-backend",
                "status", "running",
                "endpoints", Map.of(
                        "health", "/health",
                        "databaseStatus", "/api/database/status",
                        "latestVibrationWindow", "/api/vibration/latest"
                )
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
