package com.example.phm.vibration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VibrationWindowMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesMqttPayload() throws Exception {
        String payload = """
                {
                  "equipmentId": "MOTOR_001",
                  "timestamp": "2026-05-06T12:00:00.000Z",
                  "samplingRate": 8000,
                  "rpm": 1200,
                  "windowSize": 3,
                  "windowIndex": 0,
                  "values": [0.1, 0.2, 0.3]
                }
                """;

        VibrationWindowMessage message = objectMapper.readValue(payload, VibrationWindowMessage.class);

        assertThat(message.getEquipmentId()).isEqualTo("MOTOR_001");
        assertThat(message.getTimestamp()).isEqualTo("2026-05-06T12:00:00.000Z");
        assertThat(message.getSamplingRate()).isEqualTo(8000);
        assertThat(message.getRpm()).isEqualTo(1200);
        assertThat(message.getWindowSize()).isEqualTo(3);
        assertThat(message.getWindowIndex()).isZero();
        assertThat(message.valuesLength()).isEqualTo(3);
    }
}
