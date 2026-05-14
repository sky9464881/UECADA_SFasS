package com.example.phm.sensor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensorBufferRegistryTest {

    private final SensorBufferRegistry registry = new SensorBufferRegistry();

    @Test
    void resolvesConfiguredCapacitiesForLineScopedBuffers() {
        assertThat(registry.getOrCreate("LINE01.CAST01:temperature").capacity()).isEqualTo(600);
        assertThat(registry.getOrCreate("LINE02.CNC01:spindle_load").capacity()).isEqualTo(7200);
        assertThat(registry.getOrCreate("LINE03.ASSY01:torque").capacity()).isEqualTo(500);
    }

    @Test
    void resolvesConfiguredCapacitiesForDasEquipmentSensorBuffers() {
        assertThat(registry.getOrCreate("LINE01.CAST01:sensor_vibration").capacity()).isEqualTo(600);
        assertThat(registry.getOrCreate("LINE01.CAST01:sensor_current").capacity()).isEqualTo(600);
        assertThat(registry.getOrCreate("LINE01.CAST01:sensor_voltage").capacity()).isEqualTo(600);
        assertThat(registry.getOrCreate("LINE01.CAST01:sensor_temperature").capacity()).isEqualTo(600);
        assertThat(registry.getOrCreate("LINE02.CNC03:sensor_vibration").capacity()).isEqualTo(600);
        assertThat(registry.getOrCreate("LINE03.TEST02:sensor_temperature").capacity()).isEqualTo(600);
    }
}
