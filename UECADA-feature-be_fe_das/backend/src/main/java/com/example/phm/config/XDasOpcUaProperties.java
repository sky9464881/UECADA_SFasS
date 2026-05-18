package com.example.phm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phm.opcua.x-das")
public record XDasOpcUaProperties(
        Boolean enabled,
        String endpointUrl,
        Double publishingIntervalMs,
        Integer queueSize,
        Long reconnectDelayMs,
        Boolean includeLine01AliasBuffers
) {

    public XDasOpcUaProperties {
        enabled = enabled == null ? true : enabled;
        endpointUrl = endpointUrl == null || endpointUrl.isBlank()
                ? "opc.tcp://localhost:54880/UA/X_DAS/"
                : endpointUrl;
        publishingIntervalMs = publishingIntervalMs == null ? 1000.0 : publishingIntervalMs;
        queueSize = queueSize == null ? 10 : queueSize;
        reconnectDelayMs = reconnectDelayMs == null ? 5000L : reconnectDelayMs;
        includeLine01AliasBuffers = includeLine01AliasBuffers == null ? true : includeLine01AliasBuffers;
    }
}
