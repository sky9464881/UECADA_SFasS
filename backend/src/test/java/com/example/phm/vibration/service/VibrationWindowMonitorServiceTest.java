package com.example.phm.vibration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import org.junit.jupiter.api.Test;

class VibrationWindowMonitorServiceTest {

    private final VibrationWindowMonitorService monitorService = new VibrationWindowMonitorService();

    @Test
    void returnsEmptyWhenNoMessageWasReceived() {
        VibrationWindowLatestResponse response = monitorService.latest();

        assertThat(response.received()).isFalse();
        assertThat(response.receivedCount()).isZero();
        assertThat(response.lastReceivedAt()).isNull();
        assertThat(response.latest()).isNull();
    }

    @Test
    void recordsLatestMessageSummary() {
        VibrationWindowMessage message = new VibrationWindowMessage(
                "MOTOR_001",
                "2026-05-06T12:00:00.000Z",
                8000,
                1200,
                3,
                7,
                List.of(0.1, 0.2, 0.3)
        );

        monitorService.record(message);
        VibrationWindowLatestResponse response = monitorService.latest();

        assertThat(response.received()).isTrue();
        assertThat(response.receivedCount()).isEqualTo(1);
        assertThat(response.lastReceivedAt()).isNotNull();
        assertThat(response.latest().equipmentId()).isEqualTo("MOTOR_001");
        assertThat(response.latest().windowIndex()).isEqualTo(7);
        assertThat(response.latest().valuesLength()).isEqualTo(3);
    }
}
