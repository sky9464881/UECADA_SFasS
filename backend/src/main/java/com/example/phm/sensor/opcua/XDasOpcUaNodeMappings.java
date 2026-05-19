package com.example.phm.sensor.opcua;

import java.util.ArrayList;
import java.util.List;

public final class XDasOpcUaNodeMappings {

    private static final List<String> LINES = List.of("LINE01", "LINE02", "LINE03");

    private static final List<EquipmentMapping> EQUIPMENT_MAPPINGS = List.of(
            new EquipmentMapping("CAST01", List.of(
                    new MetricMapping("InjectionPressure", "injection_pressure"),
                    new MetricMapping("MoldTemperature", "mold_temperature"),
                    new MetricMapping("CoolingFlow", "cooling_flow"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("CNC01", List.of(
                    new MetricMapping("SpindleSpeed", "spindle_speed"),
                    new MetricMapping("ToolUsage", "tool_usage"),
                    new MetricMapping("CoolantFlow", "coolant_flow"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("CNC02", List.of(
                    new MetricMapping("SpindleSpeed", "spindle_speed"),
                    new MetricMapping("ToolUsage", "tool_usage"),
                    new MetricMapping("CoolantFlow", "coolant_flow"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("CNC03", List.of(
                    new MetricMapping("SpindleSpeed", "spindle_speed"),
                    new MetricMapping("ToolUsage", "tool_usage"),
                    new MetricMapping("CoolantFlow", "coolant_flow"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("WASH01", List.of(
                    new MetricMapping("CleaningConcentration", "cleaning_concentration"),
                    new MetricMapping("CleaningTemperature", "cleaning_temperature"),
                    new MetricMapping("CleaningPressure", "cleaning_pressure"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("ASSY01", List.of(
                    new MetricMapping("TighteningTorque", "tightening_torque"),
                    new MetricMapping("TighteningAngle", "tightening_angle"),
                    new MetricMapping("PressForce", "press_force"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("ASSY02", List.of(
                    new MetricMapping("TighteningTorque", "tightening_torque"),
                    new MetricMapping("TighteningAngle", "tightening_angle"),
                    new MetricMapping("PressForce", "press_force"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("TEST01", List.of(
                    new MetricMapping("BoreDimension", "bore_dimension"),
                    new MetricMapping("HoleDimension", "hole_dimension"),
                    new MetricMapping("ResultOk", "result_ok"),
                    new MetricMapping("CycleTime", "cycle_time")
            )),
            new EquipmentMapping("TEST02", List.of(
                    new MetricMapping("BoreDimension", "bore_dimension"),
                    new MetricMapping("HoleDimension", "hole_dimension"),
                    new MetricMapping("ResultOk", "result_ok"),
                    new MetricMapping("CycleTime", "cycle_time")
            ))
    );

    private static final List<MetricMapping> COMMON_SENSOR_MAPPINGS = List.of(
            new MetricMapping("SensorVibration", "sensor_vibration"),
            new MetricMapping("SensorCurrent", "sensor_current"),
            new MetricMapping("SensorVoltage", "sensor_voltage"),
            new MetricMapping("SensorTemperature", "sensor_temperature")
    );

    private XDasOpcUaNodeMappings() {
    }

    public static List<XDasOpcUaNodeMapping> defaults(boolean includeLine01AliasBuffers) {
        List<XDasOpcUaNodeMapping> mappings = new ArrayList<>();
        for (String line : LINES) {
            for (EquipmentMapping equipment : EQUIPMENT_MAPPINGS) {
                equipment.metricMappings().forEach(metric ->
                        mappings.add(mapping(line, equipment.equipmentCode(), metric, includeLine01AliasBuffers))
                );
                COMMON_SENSOR_MAPPINGS.forEach(metric ->
                        mappings.add(mapping(line, equipment.equipmentCode(), metric, includeLine01AliasBuffers))
                );
            }
        }
        return List.copyOf(mappings);
    }

    private static XDasOpcUaNodeMapping mapping(
            String line,
            String equipmentCode,
            MetricMapping metric,
            boolean includeLine01AliasBuffers
    ) {
        List<String> bufferKeys = new ArrayList<>();
        bufferKeys.add("%s.%s:%s".formatted(line, equipmentCode, metric.bufferSuffix()));
        if (includeLine01AliasBuffers && "LINE01".equals(line)) {
            bufferKeys.add("%s:%s".formatted(equipmentCode, metric.bufferSuffix()));
        }

        return new XDasOpcUaNodeMapping(
                "ns=2;s=%s.%s.%s".formatted(line, equipmentCode, metric.nodeName()),
                List.copyOf(bufferKeys)
        );
    }

    private record EquipmentMapping(String equipmentCode, List<MetricMapping> metricMappings) {
    }

    private record MetricMapping(String nodeName, String bufferSuffix) {
    }
}
