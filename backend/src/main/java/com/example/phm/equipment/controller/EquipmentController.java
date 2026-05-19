package com.example.phm.equipment.controller;

import java.util.List;

import com.example.phm.equipment.dto.EquipmentResponse;
import com.example.phm.equipment.service.EquipmentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipments")
public class EquipmentController {

    private final EquipmentQueryService equipmentQueryService;

    public EquipmentController(EquipmentQueryService equipmentQueryService) {
        this.equipmentQueryService = equipmentQueryService;
    }

    @GetMapping
    public List<EquipmentResponse> findAll(@RequestParam(required = false) String factoryId) {
        return equipmentQueryService.findAllWithRuntime(factoryId);
    }
}
