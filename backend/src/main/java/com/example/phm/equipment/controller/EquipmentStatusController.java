package com.example.phm.equipment.controller;

import java.util.List;

import com.example.phm.equipment.dto.EquipmentStatusResponse;
import com.example.phm.equipment.dto.EquipmentStatusUpdateRequest;
import com.example.phm.equipment.entity.EquipmentStatus;
import com.example.phm.equipment.repository.EquipmentStatusRepository;
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

    public EquipmentStatusController(EquipmentStatusRepository equipmentStatusRepository) {
        this.equipmentStatusRepository = equipmentStatusRepository;
    }

    @GetMapping
    public List<EquipmentStatusResponse> findByEquipIds(@RequestParam List<String> equipIds) {
        return equipmentStatusRepository.findByEquipIdIn(equipIds).stream()
                .map(EquipmentStatusResponse::from)
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
}
