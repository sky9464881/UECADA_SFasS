package com.example.phm.alarm.controller;

import java.util.List;

import com.example.phm.alarm.dto.AlarmHistoryResponse;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlarmHistoryController {

    private final AlarmHistoryRepository alarmHistoryRepository;

    public AlarmHistoryController(AlarmHistoryRepository alarmHistoryRepository) {
        this.alarmHistoryRepository = alarmHistoryRepository;
    }

    @GetMapping("/api/alarm-histories")
    public List<AlarmHistoryResponse> findRecent(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return alarmHistoryRepository.findTop100ByOrderByOccurredAtDesc().stream()
                .limit(safeLimit)
                .map(AlarmHistoryResponse::from)
                .toList();
    }
}
