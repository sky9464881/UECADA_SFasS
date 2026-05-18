package com.example.phm.sensor.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class XDasOpcUaNodeMappingsTest {

    @Test
    void mapsXDasPlcNodesToLineScopedAndCompatibilityBufferKeys() {
        Map<String, XDasOpcUaNodeMapping> mappings = XDasOpcUaNodeMappings
                .defaults(true)
                .stream()
                .collect(Collectors.toMap(XDasOpcUaNodeMapping::nodeId, Function.identity()));

        assertThat(mappings.get("ns=2;s=LINE01.CAST01.Temperature").bufferKeys())
                .containsExactly("LINE01.CAST01:temperature", "CAST01:temperature");
        assertThat(mappings.get("ns=2;s=LINE02.CAST01.Temperature").bufferKeys())
                .containsExactly("LINE02.CAST01:temperature");
        assertThat(mappings.get("ns=2;s=LINE03.CNC01.SpindleLoad").bufferKeys())
                .containsExactly("LINE03.CNC01:spindle_load");
        assertThat(mappings.get("ns=2;s=LINE03.CNC01.CycleTime").bufferKeys())
                .containsExactly("LINE03.CNC01:cycle_time");
    }

    @Test
    void mapsDasCommonSensorNodesForEveryEquipment() {
        Map<String, XDasOpcUaNodeMapping> mappings = XDasOpcUaNodeMappings
                .defaults(true)
                .stream()
                .collect(Collectors.toMap(XDasOpcUaNodeMapping::nodeId, Function.identity()));

        assertThat(mappings.get("ns=2;s=LINE01.CAST01.SensorVoltage").bufferKeys())
                .containsExactly("LINE01.CAST01:sensor_voltage", "CAST01:sensor_voltage");
        assertThat(mappings.get("ns=2;s=LINE03.TEST02.SensorVibration").bufferKeys())
                .containsExactly("LINE03.TEST02:sensor_vibration");
        assertThat(mappings.get("ns=2;s=LINE02.TEST02.CycleTime").bufferKeys())
                .containsExactly("LINE02.TEST02:cycle_time");
    }
}
