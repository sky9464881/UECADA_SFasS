package com.example.phm.kpi.scheduler;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.kpi.buffer.EquipmentAvailabilityBuffer;
import com.example.phm.kpi.entity.EquipmentKpiLog;
import com.example.phm.kpi.entity.LineKpiLog;
import com.example.phm.kpi.repository.EquipmentKpiLogRepository;
import com.example.phm.kpi.repository.LineKpiLogRepository;
import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.service.RealtimeEquipmentService;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KpiSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(KpiSnapshotScheduler.class);

    private final EquipmentRepository equipmentRepository;
    private final EquipmentKpiLogRepository equipmentKpiLogRepository;
    private final LineKpiLogRepository lineKpiLogRepository;
    private final RealtimeEquipmentService realtimeEquipmentService;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;
    private final EquipmentAvailabilityBuffer availabilityBuffer;
    private final SensorBufferRegistry sensorBufferRegistry;

    public KpiSnapshotScheduler(
            EquipmentRepository equipmentRepository,
            EquipmentKpiLogRepository equipmentKpiLogRepository,
            LineKpiLogRepository lineKpiLogRepository,
            RealtimeEquipmentService realtimeEquipmentService,
            VibrationWindowMonitorService vibrationWindowMonitorService,
            EquipmentAvailabilityBuffer availabilityBuffer,
            SensorBufferRegistry sensorBufferRegistry
    ) {
        this.equipmentRepository = equipmentRepository;
        this.equipmentKpiLogRepository = equipmentKpiLogRepository;
        this.lineKpiLogRepository = lineKpiLogRepository;
        this.realtimeEquipmentService = realtimeEquipmentService;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
        this.availabilityBuffer = availabilityBuffer;
        this.sensorBufferRegistry = sensorBufferRegistry;
    }

    /** 2초마다: 가용성 + 품질 + 사이클타임 메모리 버퍼 기록 (DB 쓰기 없음) */
    @Scheduled(fixedDelay = 2000)
    public void recordStatus() {
        List<Equipment> equipments = equipmentRepository.findAll();
        for (Equipment equipment : equipments) {
            String code = equipment.getEquipmentCode();

            // 가용성
            String status = resolveStatus(code);
            boolean running = "RUNNING".equals(status);
            availabilityBuffer.record(code, running);

            // 품질: TEST 설비만 result_ok 기록
            if (isTestEquipment(code)) {
                availabilityBuffer.recordQuality(code, readResultOk(code));
            }

            // 성능: 가동 중인 설비만 cycle_time 기록
            if (running) {
                double ct = readCycleTime(code);
                if (ct > 0) {
                    availabilityBuffer.recordCycleTime(code, ct);
                }
            }
        }
    }

    /** 매 정각: 메모리 → equipment_kpi_log → line_kpi_log DB 저장 */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void takeHourlySnapshot() {
        LocalDateTime snapshotHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        log.info("KPI hourly snapshot started: {}", snapshotHour);

        List<Equipment> equipments = equipmentRepository.findAll();

        // 1. equipment_kpi_log
        List<EquipmentKpiLog> equipmentLogs = new ArrayList<>();
        for (Equipment equipment : equipments) {
            double avail = availabilityBuffer.hourlyAvailability(equipment.getEquipmentCode());
            equipmentLogs.add(new EquipmentKpiLog(equipment.getEquipmentCode(), avail, snapshotHour));
        }
        equipmentKpiLogRepository.saveAll(equipmentLogs);

        // 2. line_kpi_log: 가용성 + 성능 + 품질 집계
        Map<String, List<Equipment>> byLine = equipments.stream()
                .filter(e -> e.getLocation() != null)
                .collect(Collectors.groupingBy(Equipment::getLocation));

        List<LineKpiLog> lineLogs = new ArrayList<>();
        byLine.forEach((lineId, lineEquipments) -> {
            // 가용성: 라인 전체 설비 평균
            double lineAvail = lineEquipments.stream()
                    .mapToDouble(e -> availabilityBuffer.hourlyAvailability(e.getEquipmentCode()))
                    .average().orElse(0.0);

            // 성능: 설비 타입별 표준 사이클타임 기준, 가동 이력 있는 설비만
            double linePerf = lineEquipments.stream()
                    .mapToDouble(e -> {
                        int nominal = nominalCycleSec(e.getEquipmentCode());
                        return availabilityBuffer.hourlyPerformance(e.getEquipmentCode(), nominal);
                    })
                    .average().orElse(100.0);

            // 품질: TEST 설비만 평균
            List<Equipment> testEquipments = lineEquipments.stream()
                    .filter(e -> isTestEquipment(e.getEquipmentCode()))
                    .toList();
            double lineQuality = testEquipments.isEmpty() ? 100.0 :
                    testEquipments.stream()
                            .mapToDouble(e -> availabilityBuffer.hourlyQuality(e.getEquipmentCode()))
                            .average().orElse(100.0);

            lineLogs.add(new LineKpiLog(
                    lineId,
                    round1(lineAvail),
                    round1(linePerf),
                    round1(lineQuality),
                    snapshotHour
            ));
        });
        lineKpiLogRepository.saveAll(lineLogs);

        log.info("KPI hourly snapshot saved: {} equipment, {} lines", equipmentLogs.size(), lineLogs.size());
    }

    private int nominalCycleSec(String equipmentCode) {
        if (equipmentCode == null) return 60;
        String upper = equipmentCode.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, Integer> entry : EquipmentAvailabilityBuffer.NOMINAL_CYCLE_SEC.entrySet()) {
            if (upper.contains(entry.getKey())) return entry.getValue();
        }
        return 60;
    }

    private boolean isTestEquipment(String equipmentCode) {
        return equipmentCode != null && equipmentCode.toUpperCase(Locale.ROOT).contains("TEST");
    }

    private boolean readResultOk(String equipmentCode) {
        for (String key : SensorBufferKeys.lookupKeys(equipmentCode, "result_ok")) {
            SensorBuffer buffer = sensorBufferRegistry.get(key);
            if (buffer != null && buffer.latest() != null) {
                return buffer.latest().value() >= 0.5;
            }
        }
        return true;
    }

    private double readCycleTime(String equipmentCode) {
        for (String key : SensorBufferKeys.lookupKeys(equipmentCode, "cycle_time")) {
            SensorBuffer buffer = sensorBufferRegistry.get(key);
            if (buffer != null && buffer.latest() != null) {
                return buffer.latest().value();
            }
        }
        return 0.0;
    }

    private String resolveStatus(String equipmentCode) {
        VibrationRealtimeResponse realtime = vibrationWindowMonitorService.latestRealtime(equipmentCode);
        if (realtime.received() && realtime.analysis() != null && realtime.analysis().getAlarmLevel() != null) {
            String level = realtime.analysis().getAlarmLevel().toLowerCase(Locale.ROOT);
            if ("danger".equals(level) || "warning".equals(level)) return "ALARM";
        }
        String sensorStatus = realtimeEquipmentService.statusOverride(equipmentCode);
        if (sensorStatus != null) return sensorStatus;
        return "RUNNING";
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
