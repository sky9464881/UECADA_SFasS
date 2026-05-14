package com.example.phm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phm.mqtt")
public record MqttProperties(
        String host,
        Integer port,
        String vibrationTopic,
        Integer qos
) {
    public MqttProperties {
        host = host == null || host.isBlank() ? "localhost" : host;
        port = port == null ? 1883 : port;
        vibrationTopic = vibrationTopic == null || vibrationTopic.isBlank()
                ? "factory/motor/1/vibration/window"
                : vibrationTopic;
        qos = qos == null ? 1 : qos;
    }

    public String brokerUri() {
        return "tcp://" + host + ":" + port;
    }
}
