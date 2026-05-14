package com.example.phm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phm.ai-api")
public record AiApiProperties(String baseUrl) {

    public AiApiProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8001" : baseUrl;
    }
}
