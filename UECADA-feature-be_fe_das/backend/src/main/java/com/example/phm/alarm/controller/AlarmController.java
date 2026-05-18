package com.example.phm.alarm.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.alarm.dto.AlarmCreateRequest;
import com.example.phm.alarm.dto.AlarmResolveRequest;
import com.example.phm.alarm.dto.AlarmResponse;
import com.example.phm.alarm.dto.AlarmStatResponse;
import com.example.phm.alarm.service.AlarmService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlarmResponse create(@Valid @RequestBody AlarmCreateRequest request) {
        return alarmService.create(request);
    }

    @GetMapping
    public List<AlarmResponse> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String equipmentCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return alarmService.findByFilters(status, equipmentCode, from, to);
    }

    @PatchMapping("/{alarmId}/resolve")
    public AlarmResponse resolve(
            @PathVariable Long alarmId,
            @RequestBody AlarmResolveRequest request
    ) {
        return alarmService.resolve(alarmId, request);
    }

    @GetMapping("/stats")
    public List<AlarmStatResponse> stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return alarmService.getStats(from, to);
    }
}
