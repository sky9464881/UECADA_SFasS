package com.example.phm.alarm.threshold;

import java.util.Map;

/**
 * 설비 유형별 센서 임계값 정의.
 * 기준: 설비별 태그값 스펙 (WARNING/DANGER 범위)
 */
public final class EquipmentThresholdConfig {

    private EquipmentThresholdConfig() {}

    public static final Map<String, Map<String, SensorThreshold>> BY_PROCESS_TYPE = Map.of(
            "주조", castThresholds(),
            "가공", cncThresholds(),
            "세척", washThresholds(),
            "조립", assyThresholds(),
            "검사", testThresholds()
    );

    /** 주조기 (CAST) 임계값 */
    private static Map<String, SensorThreshold> castThresholds() {
        return Map.ofEntries(
                // 공통 센서
                Map.entry("sensor_vibration",   SensorThreshold.highOnly(2.8, 4.5)),     // WARNING >2.8, DANGER >4.5 mm/s
                Map.entry("sensor_current",     SensorThreshold.highOnly(60.0, 75.0)),   // WARNING 60~75A, DANGER >75A
                Map.entry("sensor_voltage",     SensorThreshold.band(200.0, 342.0, 418.0, Double.NaN)), // WARNING <342 or >418V, DANGER <200V
                Map.entry("sensor_temperature", SensorThreshold.highOnly(40.0, 45.0)),   // WARNING >40℃, DANGER >45℃
                // 공정 센서
                Map.entry("injection_pressure", SensorThreshold.highOnly(138.0, 150.0)), // WARNING >138MPa (120*1.15), DANGER >150MPa (120*1.25)
                Map.entry("mold_temperature",   SensorThreshold.band(150.0, 170.0, 260.0, 280.0)), // WARNING <170 or >260℃, DANGER <150 or >280℃
                Map.entry("cooling_flow",       SensorThreshold.lowOnly(16.0, 12.0))     // WARNING <16 L/min (-20%), DANGER <12 L/min (-40%)
        );
    }

    /** 가공기 (CNC) 임계값 */
    private static Map<String, SensorThreshold> cncThresholds() {
        return Map.ofEntries(
                // 공통 센서
                Map.entry("sensor_vibration",   SensorThreshold.highOnly(2.8, 4.5)),     // WARNING >2.8, DANGER >4.5 mm/s
                Map.entry("sensor_current",     SensorThreshold.highOnly(35.0, 45.0)),   // WARNING 35~45A, DANGER >45A
                Map.entry("sensor_voltage",     SensorThreshold.band(200.0, 342.0, 418.0, Double.NaN)),
                Map.entry("sensor_temperature", SensorThreshold.highOnly(35.0, 40.0)),   // WARNING >35℃, DANGER >40℃
                // 공정 센서
                Map.entry("tool_usage",         SensorThreshold.highOnly(80.0, 100.0)),  // WARNING 80~100%, DANGER >100%
                Map.entry("coolant_flow",       SensorThreshold.lowOnly(8.0, 6.0))       // WARNING <8 L/min (-20%), DANGER <6 L/min (-40%)
        );
    }

    /** 세척기 (WASH) 임계값 */
    private static Map<String, SensorThreshold> washThresholds() {
        return Map.ofEntries(
                // 공통 센서
                Map.entry("sensor_vibration",       SensorThreshold.highOnly(2.8, 4.5)),
                Map.entry("sensor_current",         SensorThreshold.highOnly(40.0, 50.0)),  // WARNING 40~50A, DANGER >50A
                Map.entry("sensor_voltage",         SensorThreshold.band(200.0, 342.0, 418.0, Double.NaN)),
                Map.entry("sensor_temperature",     SensorThreshold.highOnly(38.0, 42.0)),  // WARNING >38℃, DANGER >42℃
                // 공정 센서
                Map.entry("cleaning_concentration", SensorThreshold.band(1.0, 1.5, 7.0, 10.0)), // WARNING <1.5 or >7%, DANGER <1 or >10%
                Map.entry("cleaning_temperature",   SensorThreshold.band(40.0, 45.0, 80.0, 85.0)), // WARNING <45 or >80℃, DANGER <40 or >85℃
                Map.entry("cleaning_pressure",      SensorThreshold.lowOnly(1.6, 1.2))     // WARNING <1.6 bar (-20%), DANGER <1.2 bar (-40%)
        );
    }

    /** 조립기 (ASSY) 임계값 */
    private static Map<String, SensorThreshold> assyThresholds() {
        return Map.ofEntries(
                // 공통 센서
                Map.entry("sensor_vibration",   SensorThreshold.highOnly(2.8, 4.5)),
                Map.entry("sensor_current",     SensorThreshold.highOnly(10.0, 15.0)),   // WARNING 10~15A, DANGER >15A
                Map.entry("sensor_voltage",     SensorThreshold.band(200.0, 342.0, 418.0, Double.NaN)),
                Map.entry("sensor_temperature", SensorThreshold.highOnly(35.0, 40.0)),
                // 공정 센서 (설정값 기준 ±10% WARNING, ±20% DANGER)
                Map.entry("tightening_torque",  SensorThreshold.band(24.0, 27.0, 55.0, 60.0)), // 30Nm*0.9~50Nm*1.1 기준
                Map.entry("press_force",        SensorThreshold.band(350.0, 425.0, 3450.0, 3900.0)) // 500N*0.85~3000N*1.15 기준
        );
    }

    /** 검사기 (TEST) 임계값 */
    private static Map<String, SensorThreshold> testThresholds() {
        return Map.ofEntries(
                // 공통 센서 (검사기는 진동에 더 민감)
                Map.entry("sensor_vibration",   SensorThreshold.highOnly(1.5, 2.8)),     // 검사기는 기준 더 엄격
                Map.entry("sensor_current",     SensorThreshold.highOnly(8.0, 12.0)),    // WARNING 8~12A, DANGER >12A
                Map.entry("sensor_voltage",     SensorThreshold.band(100.0, 198.0, 242.0, Double.NaN)), // 220V 기준: WARNING <198 or >242V, DANGER <100V
                Map.entry("sensor_temperature", SensorThreshold.highOnly(35.0, 40.0)),
                // 공정 센서 (치수 공차)
                Map.entry("bore_dimension",     SensorThreshold.band(39.980, 39.982, 40.018, 40.020)), // 40.000±0.020mm, 90% 접근 시 WARNING (±0.018), 이탈 시 DANGER (±0.020)
                Map.entry("hole_dimension",     SensorThreshold.band(10.150, 10.155, 10.245, 10.250))  // 10.200±0.050mm, 90% 접근 시 WARNING (±0.045), 이탈 시 DANGER (±0.050)
        );
    }

    public static Map<String, SensorThreshold> forProcessType(String processType) {
        if (processType == null) return Map.of();
        return BY_PROCESS_TYPE.getOrDefault(processType, Map.of());
    }
}
