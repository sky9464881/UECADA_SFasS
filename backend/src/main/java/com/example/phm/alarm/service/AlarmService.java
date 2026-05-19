package com.example.phm.alarm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.example.phm.alarm.dto.AlarmCreateRequest;
import com.example.phm.alarm.dto.AlarmResolveRequest;
import com.example.phm.alarm.dto.AlarmResponse;
import com.example.phm.alarm.dto.AlarmStatResponse;
import com.example.phm.alarm.entity.Alarm;
import com.example.phm.alarm.repository.AlarmRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlarmService {

    private final AlarmRepository alarmRepository;

    public AlarmService(AlarmRepository alarmRepository) {
        this.alarmRepository = alarmRepository;
    }

    public AlarmResponse create(AlarmCreateRequest request) {
        Alarm alarm = new Alarm();
        alarm.setEquipmentCode(request.equipmentCode());
        alarm.setAlarmCode(request.alarmCode());
        alarm.setAlarmType(request.alarmType());
        alarm.setAlarmCategory(request.alarmCategory());
        alarm.setSeverity(request.severity());
        alarm.setAlarmMessage(request.alarmMessage());
        alarm.setStatus("OPEN");
        alarm.setOccurredAt(request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now());
        alarm.setSensorSnapshot(request.sensorSnapshot());
        return AlarmResponse.from(alarmRepository.save(alarm));
    }

    public List<AlarmResponse> findByFilters(
            String status, String equipmentCode,
            LocalDateTime from, LocalDateTime to, int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return alarmRepository.findByFilters(status, equipmentCode, from, to, PageRequest.of(0, safeLimit))
                .stream().map(AlarmResponse::from).toList();
    }

    @Transactional
    public AlarmResponse resolve(Long alarmId, AlarmResolveRequest request) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found: " + alarmId));
        alarm.setStatus("RESOLVED");
        alarm.setResolvedBy(request.resolvedBy());
        alarm.setResolvedAt(request.resolvedAt() != null ? request.resolvedAt() : LocalDateTime.now());
        alarm.setComment(request.comment());
        return AlarmResponse.from(alarmRepository.save(alarm));
    }

    public Map<String, Long> getCounts() {
        long total = alarmRepository.count();
        long open = alarmRepository.countByStatus("OPEN");
        long resolved = alarmRepository.countByStatus("RESOLVED");
        long critical = alarmRepository.countBySeverityIn(List.of("CRITICAL", "DANGER"));
        return Map.of("total", total, "open", open, "resolved", resolved, "critical", critical);
    }

    public List<AlarmStatResponse> getStats(LocalDateTime from, LocalDateTime to) {
        return alarmRepository.countByDateAndType(from, to).stream()
                .map(row -> new AlarmStatResponse(
                        row[0].toString(),
                        row[1].toString(),
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }
}
