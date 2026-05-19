package com.example.phm.alarm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.phm.alarm.entity.Alarm;
import com.example.phm.alarm.repository.AlarmRepository;
import com.example.phm.alarm.threshold.EquipmentThresholdConfig;
import com.example.phm.alarm.threshold.SensorThreshold;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.service.RealtimeEquipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 10초 주기로 센서 버퍼 값을 임계값과 비교하여 alarm 테이블에 알람을 생성.
 * 알람 해소는 담당자가 직접 처리. 중복 방지: in-memory map으로 설비+센서별 활성 알람을 추적.
 */
@Component
public class ThresholdAlarmMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ThresholdAlarmMonitorService.class);

    private static final Set<String> COMMON_SENSORS = Set.of(
            "sensor_vibration", "sensor_current", "sensor_voltage", "sensor_temperature"
    );

    private static final Map<String, String> SENSOR_LABEL = Map.ofEntries(
            Map.entry("sensor_vibration",       "진동"),
            Map.entry("sensor_current",         "전류"),
            Map.entry("sensor_voltage",         "전압"),
            Map.entry("sensor_temperature",     "주변온도"),
            Map.entry("injection_pressure",     "사출압력"),
            Map.entry("mold_temperature",       "금형온도"),
            Map.entry("cooling_flow",           "냉각수유량"),
            Map.entry("spindle_speed",          "스핀들속도"),
            Map.entry("tool_usage",             "공구사용률"),
            Map.entry("coolant_flow",           "절삭유유량"),
            Map.entry("cleaning_concentration", "세척농도"),
            Map.entry("cleaning_temperature",   "세척온도"),
            Map.entry("cleaning_pressure",      "세척압력"),
            Map.entry("tightening_torque",      "체결토크"),
            Map.entry("tightening_angle",       "체결각도"),
            Map.entry("press_force",            "압입력"),
            Map.entry("bore_dimension",         "보어치수"),
            Map.entry("hole_dimension",         "홀치수")
    );

    private static final Map<String, String> SENSOR_UNIT = Map.ofEntries(
            Map.entry("sensor_vibration",       "mm/s"),
            Map.entry("sensor_current",         "A"),
            Map.entry("sensor_voltage",         "V"),
            Map.entry("sensor_temperature",     "℃"),
            Map.entry("injection_pressure",     "MPa"),
            Map.entry("mold_temperature",       "℃"),
            Map.entry("cooling_flow",           "L/min"),
            Map.entry("spindle_speed",          "rpm"),
            Map.entry("tool_usage",             "%"),
            Map.entry("coolant_flow",           "L/min"),
            Map.entry("cleaning_concentration", "%"),
            Map.entry("cleaning_temperature",   "℃"),
            Map.entry("cleaning_pressure",      "bar"),
            Map.entry("tightening_torque",      "Nm"),
            Map.entry("tightening_angle",       "deg"),
            Map.entry("press_force",            "N"),
            Map.entry("bore_dimension",         "mm"),
            Map.entry("hole_dimension",         "mm")
    );

    private final EquipmentRepository equipmentRepository;
    private final AlarmRepository alarmRepository;
    private final SensorBufferRegistry sensorBufferRegistry;
    private final RealtimeEquipmentService realtimeEquipmentService;

    /** 활성 알람 추적: key = "equipmentCode:sensorKey", value = alarmId */
    private final ConcurrentHashMap<String, Long> activeAlarmIds = new ConcurrentHashMap<>();
    /** 현재 심각도 추적 (등급 변경 감지용) */
    private final ConcurrentHashMap<String, String> activeSeverities = new ConcurrentHashMap<>();
    /** 마지막 해소 시각 추적 (쿨다운: 30초 내 재발생 알람 억제) */
    private final ConcurrentHashMap<String, Long> lastResolvedAt = new ConcurrentHashMap<>();

    private static final long COOLDOWN_MS = 30_000L;

    public ThresholdAlarmMonitorService(
            EquipmentRepository equipmentRepository,
            AlarmRepository alarmRepository,
            SensorBufferRegistry sensorBufferRegistry,
            RealtimeEquipmentService realtimeEquipmentService
    ) {
        this.equipmentRepository = equipmentRepository;
        this.alarmRepository = alarmRepository;
        this.sensorBufferRegistry = sensorBufferRegistry;
        this.realtimeEquipmentService = realtimeEquipmentService;
    }

    @Scheduled(fixedDelay = 10_000)
    public void checkAll() {
        List<Equipment> equipments = equipmentRepository.findAll();
        for (Equipment equipment : equipments) {
            try {
                checkEquipment(equipment);
            } catch (Exception e) {
                log.warn("Threshold check error [{}]: {}", equipment.getEquipmentCode(), e.getMessage());
            }
        }
    }

    private void checkEquipment(Equipment equipment) {
        String code = equipment.getEquipmentCode();
        String processType = equipment.getProcessType();

        Map<String, SensorThreshold> thresholds = EquipmentThresholdConfig.forProcessType(processType);
        if (thresholds.isEmpty()) return;

        // 설비가 꺼진 상태(MAINTENANCE)이면 공정 센서 임계값 체크 스킵
        // (통신은 유지되지만 모든 공정 값이 0.0으로 전송되므로 오탐 방지)
        boolean equipmentOff = "MAINTENANCE".equals(realtimeEquipmentService.statusOverride(code));

        for (Map.Entry<String, SensorThreshold> entry : thresholds.entrySet()) {
            String sensorKey = entry.getKey();
            SensorThreshold threshold = entry.getValue();

            // 설비 꺼짐 시 공정 전용 센서는 체크 스킵 (공통 센서는 전압 등 체크 필요할 수 있어 유지)
            if (equipmentOff && !COMMON_SENSORS.contains(sensorKey)) continue;

            Double value = readLatestValue(code, sensorKey);
            if (value == null || !Double.isFinite(value)) continue;

            String level = threshold.evaluate(value);
            String trackingKey = code + ":" + sensorKey;

            if ("DANGER".equals(level) || "WARNING".equals(level)) {
                String prevSeverity = activeSeverities.get(trackingKey);

                if (prevSeverity == null) {
                    // 쿨다운 중이면 새 알람 생성 스킵
                    Long lastResolved = lastResolvedAt.get(trackingKey);
                    if (lastResolved != null && System.currentTimeMillis() - lastResolved < COOLDOWN_MS) continue;
                    // 새 알람 생성
                    createAlarm(code, sensorKey, level, value);
                } else if ("WARNING".equals(prevSeverity) && "DANGER".equals(level)) {
                    // WARNING → DANGER 심각도 상승: 새 DANGER 알람 추가 (기존 WARNING은 담당자가 처리)
                    createAlarm(code, sensorKey, level, value);
                }
                // 동일 심각도 또는 DANGER→WARNING 완화: 이미 활성 알람이 있으므로 아무 작업 없음

            } else {
                // 정상 복귀: 추적 맵에서 제거만 (알람은 담당자가 직접 처리)
                // 단, 쿨다운을 위해 해소 시각은 기록
                if (activeAlarmIds.containsKey(trackingKey)) {
                    activeAlarmIds.remove(trackingKey);
                    activeSeverities.remove(trackingKey);
                    lastResolvedAt.put(trackingKey, System.currentTimeMillis());
                }
            }
        }
    }

    private void createAlarm(String equipmentCode, String sensorKey, String severity, double value) {
        String label = SENSOR_LABEL.getOrDefault(sensorKey, sensorKey);
        String unit = SENSOR_UNIT.getOrDefault(sensorKey, "");
        String category = COMMON_SENSORS.contains(sensorKey) ? "공통" : "공정";
        String levelKo = "DANGER".equals(severity) ? "위험" : "경고";

        Alarm alarm = new Alarm();
        alarm.setEquipmentCode(equipmentCode);
        alarm.setAlarmCode(sensorKey);
        alarm.setAlarmType(label + " 이상");
        alarm.setAlarmCategory(category);
        alarm.setSeverity(severity);
        alarm.setAlarmMessage(
                "[" + equipmentCode + "] " + label + " " + levelKo + ": "
                + String.format(Locale.ROOT, "%.2f", value) + " " + unit
        );
        alarm.setStatus("OPEN");
        alarm.setOccurredAt(LocalDateTime.now());
        alarm.setSensorSnapshot(
                "{\"sensor\":\"" + sensorKey + "\",\"value\":"
                + String.format(Locale.ROOT, "%.4f", value)
                + ",\"unit\":\"" + unit + "\"}"
        );

        Alarm saved = alarmRepository.save(alarm);
        String trackingKey = equipmentCode + ":" + sensorKey;
        activeAlarmIds.put(trackingKey, saved.getAlarmId());
        activeSeverities.put(trackingKey, severity);

        log.info("[AlarmMonitor] {} {} - {} = {} {}", severity, equipmentCode, label, String.format(Locale.ROOT, "%.2f", value), unit);
    }

    private Double readLatestValue(String equipmentCode, String sensorKey) {
        for (String key : SensorBufferKeys.lookupKeys(equipmentCode, sensorKey)) {
            SensorBuffer buffer = sensorBufferRegistry.get(key);
            if (buffer != null && buffer.latest() != null) {
                return buffer.latest().value();
            }
        }
        return null;
    }
}
