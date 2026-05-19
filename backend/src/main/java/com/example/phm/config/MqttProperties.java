package com.example.phm.config;

<<<<<<< HEAD
=======
import java.util.Arrays;

>>>>>>> feature/develop_before
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
<<<<<<< HEAD
=======

    public String[] vibrationTopics() {
        return Arrays.stream(vibrationTopic.split(","))
                .map(String::trim)
                .filter(topic -> !topic.isBlank())
                .toArray(String[]::new);
    }
>>>>>>> feature/develop_before
}
