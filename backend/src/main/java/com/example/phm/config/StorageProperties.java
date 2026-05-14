package com.example.phm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phm.storage")
public record StorageProperties(String rawWindowDir) {

    public StorageProperties {
        rawWindowDir = rawWindowDir == null || rawWindowDir.isBlank() ? "../data/raw_windows" : rawWindowDir;
    }
}
