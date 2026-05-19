package com.example.phm.line.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.entity.EquipmentStatus;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.equipment.repository.EquipmentStatusRepository;
import com.example.phm.line.dto.LineResponse;
import com.example.phm.line.entity.ProductionLine;
import com.example.phm.line.repository.ProductionLineRepository;
import com.example.phm.sensor.service.RealtimeEquipmentService;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LineAggregationService {

    private final ProductionLineRepository lineRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentStatusRepository statusRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;
    private final RealtimeEquipmentService realtimeEquipmentService;

    public LineAggregationService(
            ProductionLineRepository lineRepository,
            EquipmentRepository equipmentRepository,
            EquipmentStatusRepository statusRepository,
            AnalysisResultRepository analysisResultRepository,
            VibrationWindowMonitorService vibrationWindowMonitorService,
            RealtimeEquipmentService realtimeEquipmentService
    ) {
        this.lineRepository = lineRepository;
        this.equipmentRepository = equipmentRepository;
        this.statusRepository = statusRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
        this.realtimeEquipmentService = realtimeEquipmentService;
    }

    @Transactional(readOnly = true)
    public List<LineResponse> getLines(String factoryId) {
        List<ProductionLine> lines = factoryId == null || factoryId.isBlank()
                ? lineRepository.findAll()
                : lineRepository.findByFactoryId(factoryId);
        List<Equipment> equipments = equipmentRepository.findAll();
        Map<String, EquipmentStatus> statusByEquipment = statusRepository.findAll().stream()
                .collect(Collectors.toMap(EquipmentStatus::getEquipId, Function.identity()));

        // N+1 회피: 모든 설비의 latest analysis 를 한 번에 가져와 Map 으로 캐싱.
        List<String> codes = equipments.stream().map(Equipment::getEquipmentCode).toList();
        Map<String, String> latestAlarmLevelByEquipment = new HashMap<>();
        if (!codes.isEmpty()) {
            List<AnalysisResult> latest = analysisResultRepository.findLatestForEquipmentCodes(codes);
            for (AnalysisResult ar : latest) {
                if (ar.getAlarmLevel() != null) {
                    latestAlarmLevelByEquipment.put(
                            ar.getEquipmentCode(),
                            ar.getAlarmLevel().toLowerCase(Locale.ROOT)
                    );
                }
            }
        }

        return lines.stream()
                .map(line -> toResponse(line, equipments, statusByEquipment, latestAlarmLevelByEquipment))
                .toList();
    }

    private LineResponse toResponse(
            ProductionLine line,
            List<Equipment> allEquipments,
            Map<String, EquipmentStatus> statusByEquipment,
            Map<String, String> latestAlarmLevelByEquipment
    ) {
        List<Equipment> lineEquipments = allEquipments.stream()
                .filter(equipment -> line.getLineId().equals(equipment.getLocation()))
                .toList();
        long total = lineEquipments.size();
        long running = countStatus(lineEquipments, statusByEquipment, latestAlarmLevelByEquipment, "RUNNING");
        long alarm = countStatus(lineEquipments, statusByEquipment, latestAlarmLevelByEquipment, "ALARM");
        long standby = countStatus(lineEquipments, statusByEquipment, latestAlarmLevelByEquipment, "STANDBY");
        long maintenance = countStatus(lineEquipments, statusByEquipment, latestAlarmLevelByEquipment, "MAINTENANCE");
        long openAlarmCount = alarm;
        String lineStatus = alarm > 0 ? "ALARM" : line.getLineStatus();

        return new LineResponse(
                line.getLineId(),
                line.getLineName(),
                lineStatus,
                line.getFactoryId(),
                total,
                running,
                alarm,
                standby,
                maintenance,
                openAlarmCount,
                derivedOee(total, running, standby, maintenance)
        );
    }

    private long countStatus(
            List<Equipment> equipments,
            Map<String, EquipmentStatus> statusByEquipment,
            Map<String, String> latestAlarmLevelByEquipment,
            String status
    ) {
        return equipments.stream()
                .filter(equipment -> status.equals(currentStatus(equipment, statusByEquipment, latestAlarmLevelByEquipment)))
                .count();
    }

    private String currentStatus(
            Equipment equipment,
            Map<String, EquipmentStatus> statusByEquipment,
            Map<String, String> latestAlarmLevelByEquipment
    ) {
        EquipmentStatus status = statusByEquipment.get(equipment.getEquipmentCode());
        String baseStatus = status == null || status.getStatusCode() == null || status.getStatusCode().isBlank()
                ? "RUNNING"
                : status.getStatusCode().toUpperCase(Locale.ROOT);
        String realtimeAlarmLevel = realtimeAlarmLevel(equipment.getEquipmentCode());
        if ("danger".equals(realtimeAlarmLevel) || "warning".equals(realtimeAlarmLevel)) {
            return "ALARM";
        }
        if ("normal".equals(realtimeAlarmLevel) && "ALARM".equals(baseStatus)) {
            return "RUNNING";
        }

        String sensorStatus = realtimeEquipmentService.statusOverride(equipment.getEquipmentCode());
        if (sensorStatus != null) {
            return sensorStatus;
        }

        String latestAlarmLevel = latestAlarmLevelByEquipment.get(equipment.getEquipmentCode());

        if ("danger".equals(latestAlarmLevel) || "warning".equals(latestAlarmLevel)) {
            return "ALARM";
        }
        if ("normal".equals(latestAlarmLevel) && "ALARM".equals(baseStatus)) {
            return "RUNNING";
        }
        return baseStatus;
    }

    private String realtimeAlarmLevel(String equipmentCode) {
        VibrationRealtimeResponse realtime = vibrationWindowMonitorService.latestRealtime(equipmentCode);
        if (!realtime.received() || realtime.analysis() == null || realtime.analysis().getAlarmLevel() == null) {
            return null;
        }
        return realtime.analysis().getAlarmLevel().toLowerCase(Locale.ROOT);
    }

    private Double derivedOee(long total, long running, long standby, long maintenance) {
        if (total <= 0) {
            return null;
        }
        double score = ((running * 1.0) + (standby * 0.35) + (maintenance * 0.15)) / total * 100.0;
        return BigDecimal.valueOf(score)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
