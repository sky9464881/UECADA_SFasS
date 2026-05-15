package com.example.phm.line.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.stereotype.Service;

@Service
public class LineAggregationService {

    private final ProductionLineRepository lineRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentStatusRepository statusRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;

    public LineAggregationService(
            ProductionLineRepository lineRepository,
            EquipmentRepository equipmentRepository,
            EquipmentStatusRepository statusRepository,
            AnalysisResultRepository analysisResultRepository,
            VibrationWindowMonitorService vibrationWindowMonitorService
    ) {
        this.lineRepository = lineRepository;
        this.equipmentRepository = equipmentRepository;
        this.statusRepository = statusRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
    }

    public List<LineResponse> getLines(String factoryId) {
        List<ProductionLine> lines = factoryId == null || factoryId.isBlank()
                ? lineRepository.findAll()
                : lineRepository.findByFactoryId(factoryId);
        List<Equipment> equipments = equipmentRepository.findAll();
        Map<String, EquipmentStatus> statusByEquipment = statusRepository.findAll().stream()
                .collect(Collectors.toMap(EquipmentStatus::getEquipId, Function.identity()));

        return lines.stream()
                .map(line -> toResponse(line, equipments, statusByEquipment))
                .toList();
    }

    private LineResponse toResponse(
            ProductionLine line,
            List<Equipment> allEquipments,
            Map<String, EquipmentStatus> statusByEquipment
    ) {
        List<Equipment> lineEquipments = allEquipments.stream()
                .filter(equipment -> line.getLineId().equals(equipment.getLocation()))
                .toList();
        long total = lineEquipments.size();
        long running = countStatus(lineEquipments, statusByEquipment, "RUNNING");
        long alarm = countStatus(lineEquipments, statusByEquipment, "ALARM");
        long standby = countStatus(lineEquipments, statusByEquipment, "STANDBY");
        long maintenance = countStatus(lineEquipments, statusByEquipment, "MAINTENANCE");
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
            String status
    ) {
        return equipments.stream()
                .filter(equipment -> status.equals(currentStatus(equipment, statusByEquipment)))
                .count();
    }

    private String currentStatus(Equipment equipment, Map<String, EquipmentStatus> statusByEquipment) {
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

        String latestAlarmLevel = analysisResultRepository
                .findTopByEquipmentCodeOrderByCreatedAtDesc(equipment.getEquipmentCode())
                .map(AnalysisResult::getAlarmLevel)
                .map(level -> level.toLowerCase(Locale.ROOT))
                .orElse(null);

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
