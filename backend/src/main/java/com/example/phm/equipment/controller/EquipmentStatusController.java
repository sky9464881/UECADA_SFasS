package com.example.phm.equipment.controller;

import java.util.List;
<<<<<<< HEAD

=======
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
>>>>>>> feature/develop_before
import com.example.phm.equipment.dto.EquipmentStatusResponse;
import com.example.phm.equipment.dto.EquipmentStatusUpdateRequest;
import com.example.phm.equipment.entity.EquipmentStatus;
import com.example.phm.equipment.repository.EquipmentStatusRepository;
<<<<<<< HEAD
=======
import com.example.phm.sensor.service.RealtimeEquipmentService;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
>>>>>>> feature/develop_before
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment-status")
public class EquipmentStatusController {

    private final EquipmentStatusRepository equipmentStatusRepository;
<<<<<<< HEAD

    public EquipmentStatusController(EquipmentStatusRepository equipmentStatusRepository) {
        this.equipmentStatusRepository = equipmentStatusRepository;
=======
    private final AnalysisResultRepository analysisResultRepository;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;
    private final RealtimeEquipmentService realtimeEquipmentService;

    public EquipmentStatusController(
            EquipmentStatusRepository equipmentStatusRepository,
            AnalysisResultRepository analysisResultRepository,
            VibrationWindowMonitorService vibrationWindowMonitorService,
            RealtimeEquipmentService realtimeEquipmentService
    ) {
        this.equipmentStatusRepository = equipmentStatusRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
        this.realtimeEquipmentService = realtimeEquipmentService;
>>>>>>> feature/develop_before
    }

    @GetMapping
    public List<EquipmentStatusResponse> findByEquipIds(@RequestParam List<String> equipIds) {
<<<<<<< HEAD
        return equipmentStatusRepository.findByEquipIdIn(equipIds).stream()
                .map(EquipmentStatusResponse::from)
=======
        Map<String, EquipmentStatus> statusById = equipmentStatusRepository.findByEquipIdIn(equipIds).stream()
                .collect(Collectors.toMap(EquipmentStatus::getEquipId, Function.identity()));
        return equipIds.stream()
                .distinct()
                .map(equipId -> toEffectiveResponse(equipId, statusById.get(equipId)))
>>>>>>> feature/develop_before
                .toList();
    }

    @PutMapping("/{equipId}")
    public EquipmentStatusResponse upsert(
            @PathVariable String equipId,
            @Valid @RequestBody EquipmentStatusUpdateRequest request
    ) {
        equipmentStatusRepository.upsert(equipId, request.statusCode());
        EquipmentStatus status = equipmentStatusRepository.findById(equipId)
                .orElseThrow();
        return EquipmentStatusResponse.from(status);
    }
<<<<<<< HEAD
=======

    private EquipmentStatusResponse toEffectiveResponse(String equipId, EquipmentStatus status) {
        String baseStatus = status == null ? "RUNNING" : status.getStatusCode();
        String effectiveStatus = applyLatestAnalysisStatus(equipId, baseStatus);
        return new EquipmentStatusResponse(
                equipId,
                effectiveStatus,
                status == null ? null : status.getUpdatedAt()
        );
    }

    private String applyLatestAnalysisStatus(String equipId, String baseStatus) {
        String normalizedBase = baseStatus == null || baseStatus.isBlank()
                ? "RUNNING"
                : baseStatus.toUpperCase(Locale.ROOT);
        String realtimeAlarmLevel = realtimeAlarmLevel(equipId);
        if ("danger".equals(realtimeAlarmLevel) || "warning".equals(realtimeAlarmLevel)) {
            return "ALARM";
        }
        if ("normal".equals(realtimeAlarmLevel) && "ALARM".equals(normalizedBase)) {
            return "RUNNING";
        }

        String sensorStatus = realtimeEquipmentService.statusOverride(equipId);
        if (sensorStatus != null) {
            return sensorStatus;
        }

        String latestAlarmLevel = analysisResultRepository
                .findTopByEquipmentCodeOrderByCreatedAtDesc(equipId)
                .map(AnalysisResult::getAlarmLevel)
                .map(level -> level.toLowerCase(Locale.ROOT))
                .orElse(null);
        if ("danger".equals(latestAlarmLevel) || "warning".equals(latestAlarmLevel)) {
            return "ALARM";
        }
        if ("normal".equals(latestAlarmLevel) && "ALARM".equals(normalizedBase)) {
            return "RUNNING";
        }
        return normalizedBase;
    }

    private String realtimeAlarmLevel(String equipId) {
        VibrationRealtimeResponse realtime = vibrationWindowMonitorService.latestRealtime(equipId);
        if (!realtime.received() || realtime.analysis() == null || realtime.analysis().getAlarmLevel() == null) {
            return null;
        }
        return realtime.analysis().getAlarmLevel().toLowerCase(Locale.ROOT);
    }
>>>>>>> feature/develop_before
}
