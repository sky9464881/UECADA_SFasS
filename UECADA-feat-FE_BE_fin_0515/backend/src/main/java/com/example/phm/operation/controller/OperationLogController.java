package com.example.phm.operation.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.operation.dto.OperationLogCreateRequest;
import com.example.phm.operation.dto.OperationLogResponse;
import com.example.phm.operation.entity.OperationLog;
import com.example.phm.operation.repository.OperationLogRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogRepository operationLogRepository;

    public OperationLogController(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OperationLogResponse create(@Valid @RequestBody OperationLogCreateRequest request) {
        OperationLog log = new OperationLog();
        log.setEquipmentCode(request.equipmentCode());
        log.setStatusCode(request.statusCode());
        log.setStartAt(request.startAt());
        log.setEndAt(request.endAt());
        return OperationLogResponse.from(operationLogRepository.save(log));
    }

    @GetMapping
    public List<OperationLogResponse> findByEquipment(
            @RequestParam String equipmentCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return operationLogRepository
                .findByEquipmentCodeAndStartAtBetweenOrderByStartAtAsc(equipmentCode, from, to)
                .stream().map(OperationLogResponse::from).toList();
    }
}
