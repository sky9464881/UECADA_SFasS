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

        assertThat(mappings.get("ns=2;s=LINE01.CAST01.InjectionPressure").bufferKeys())
                .containsExactly("LINE01.CAST01:injection_pressure", "CAST01:injection_pressure");
        assertThat(mappings.get("ns=2;s=LINE02.CAST01.MoldTemperature").bufferKeys())
                .containsExactly("LINE02.CAST01:mold_temperature");
        assertThat(mappings.get("ns=2;s=LINE03.CNC01.SpindleSpeed").bufferKeys())
                .containsExactly("LINE03.CNC01:spindle_speed");
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
    }
}
