package com.example.phm.kpi.scheduler;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.kpi.buffer.EquipmentAvailabilityBuffer;
import com.example.phm.kpi.entity.EquipmentKpiLog;
import com.example.phm.kpi.entity.LineKpiLog;
import com.example.phm.kpi.repository.EquipmentKpiLogRepository;
import com.example.phm.kpi.repository.LineKpiLogRepository;
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

    public KpiSnapshotScheduler(
            EquipmentRepository equipmentRepository,
            EquipmentKpiLogRepository equipmentKpiLogRepository,
            LineKpiLogRepository lineKpiLogRepository,
            RealtimeEquipmentService realtimeEquipmentService,
            VibrationWindowMonitorService vibrationWindowMonitorService,
            EquipmentAvailabilityBuffer availabilityBuffer
    ) {
        this.equipmentRepository = equipmentRepository;
        this.equipmentKpiLogRepository = equipmentKpiLogRepository;
        this.lineKpiLogRepository = lineKpiLogRepository;
        this.realtimeEquipmentService = realtimeEquipmentService;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
        this.availabilityBuffer = availabilityBuffer;
    }

    /** 2초마다: 메모리 버퍼에만 기록 (DB 쓰기 없음) */
    @Scheduled(fixedDelay = 2000)
    public void recordStatus() {
        List<Equipment> equipments = equipmentRepository.findAll();
        for (Equipment equipment : equipments) {
            String status = resolveStatus(equipment.getEquipmentCode());
            availabilityBuffer.record(equipment.getEquipmentCode(), "RUNNING".equals(status));
        }
    }

    /** 매 정각: 메모리 → equipment_kpi_log → line_kpi_log DB 저장 */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void takeHourlySnapshot() {
        LocalDateTime snapshotHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        log.info("KPI hourly snapshot started: {}", snapshotHour);

        List<Equipment> equipments = equipmentRepository.findAll();

        // 1. equipment_kpi_log: 지난 1시간 가용성
        List<EquipmentKpiLog> equipmentLogs = new ArrayList<>();
        for (Equipment equipment : equipments) {
            double hourlyAvail = availabilityBuffer.hourlyAvailability(equipment.getEquipmentCode());
            equipmentLogs.add(new EquipmentKpiLog(equipment.getEquipmentCode(), hourlyAvail, snapshotHour));
        }
        equipmentKpiLogRepository.saveAll(equipmentLogs);

        // 2. line_kpi_log: 라인별 equipment_kpi_log 평균
        Map<String, List<EquipmentKpiLog>> byLine = equipmentLogs.stream()
                .filter(l -> {
                    Equipment eq = equipments.stream()
                            .filter(e -> e.getEquipmentCode().equals(l.getEquipmentCode()))
                            .findFirst().orElse(null);
                    return eq != null && eq.getLocation() != null;
                })
                .collect(Collectors.groupingBy(l -> {
                    return equipments.stream()
                            .filter(e -> e.getEquipmentCode().equals(l.getEquipmentCode()))
                            .findFirst().map(Equipment::getLocation).orElse("UNKNOWN");
                }));

        List<LineKpiLog> lineLogs = new ArrayList<>();
        byLine.forEach((lineId, logs) -> {
            if ("UNKNOWN".equals(lineId)) return;
            double lineAvail = logs.stream()
                    .mapToDouble(EquipmentKpiLog::getEquipmentOee)
                    .average().orElse(0.0);
            lineLogs.add(new LineKpiLog(lineId, Math.round(lineAvail * 10.0) / 10.0, snapshotHour));
        });
        lineKpiLogRepository.saveAll(lineLogs);

        log.info("KPI hourly snapshot saved: {} equipment, {} lines", equipmentLogs.size(), lineLogs.size());
    }

    private String resolveStatus(String equipmentCode) {
        VibrationRealtimeResponse realtime = vibrationWindowMonitorService.latestRealtime(equipmentCode);
        if (realtime.received() && realtime.analysis() != null && realtime.analysis().getAlarmLevel() != null) {
            String level = realtime.analysis().getAlarmLevel().toLowerCase();
            if ("danger".equals(level) || "warning".equals(level)) return "ALARM";
        }
        String sensorStatus = realtimeEquipmentService.statusOverride(equipmentCode);
        if (sensorStatus != null) return sensorStatus;
        return "RUNNING";
    }
}
