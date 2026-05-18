package com.example.phm.sensor.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.SensorFrame;
import com.example.phm.sensor.dto.SensorLatestValuesRequest;
import org.junit.jupiter.api.Test;

class SensorBufferControllerTest {

    @Test
    void latestValuesSplitsCommaSeparatedKeysAndFallsBackToLine01Alias() {
        SensorBufferRegistry registry = new SensorBufferRegistry();
        registry.push("CAST01:sensor_current", new SensorFrame(123L, 42.5));

        SensorBufferController controller = new SensorBufferController(registry);

        var result = controller.latestValues(List.of(
                "LINE01.CAST01:sensor_current,LINE02.CNC01:sensor_current"
        ));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bufferKey()).isEqualTo("LINE01.CAST01:sensor_current");
        assertThat(result.get(0).latest()).isEqualTo(new SensorFrame(123L, 42.5));
        assertThat(result.get(1).bufferKey()).isEqualTo("LINE02.CNC01:sensor_current");
        assertThat(result.get(1).latest()).isNull();
    }

    @Test
    void latestValuesPostBodyUsesSameLookupRules() {
        SensorBufferRegistry registry = new SensorBufferRegistry();
        registry.push("LINE01.CAST01:sensor_voltage", new SensorFrame(456L, 381.2));

        SensorBufferController controller = new SensorBufferController(registry);

        var result = controller.latestValues(new SensorLatestValuesRequest(List.of(
                "CAST01:sensor_voltage",
                "LINE02.CNC01:sensor_voltage"
        )));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).latest()).isEqualTo(new SensorFrame(456L, 381.2));
        assertThat(result.get(1).latest()).isNull();
    }
}
