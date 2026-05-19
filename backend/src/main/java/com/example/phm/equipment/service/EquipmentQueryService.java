package com.example.phm.equipment.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.phm.demo.service.DemoMetricsService;
import com.example.phm.equipment.dto.EquipmentResponse;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentQueryService {

    private final EquipmentRepository equipmentRepository;
    private final DemoMetricsService demoMetricsService;

    public EquipmentQueryService(EquipmentRepository equipmentRepository, DemoMetricsService demoMetricsService) {
        this.equipmentRepository = equipmentRepository;
        this.demoMetricsService = demoMetricsService;
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> findAllWithRuntime(String factoryId) {
        List<Equipment> equipments = factoryId == null || factoryId.isBlank()
                ? equipmentRepository.findAll()
                : equipmentRepository.findByFactoryId(factoryId);

        List<String> codes = equipments.stream().map(Equipment::getEquipmentCode).toList();
        Map<String, DemoMetricsService.EquipmentRuntimeDto> runtimeByCode =
                demoMetricsService.equipmentRuntimeFor(codes);

        return equipments.stream()
                .map(equipment -> {
                    EquipmentResponse base = EquipmentResponse.from(equipment);
                    DemoMetricsService.EquipmentRuntimeDto rt =
                            runtimeByCode.get(equipment.getEquipmentCode());
                    if (rt == null) {
                        return base;
                    }
                    return base.withRuntime(
                            rt.utilizationRate(),
                            rt.defectCount(),
                            rt.operatorName(),
                            rt.cycleTimeSec(),
                            rt.currentAmp(),
                            rt.temperatureC(),
                            rt.humidityPct(),
                            rt.vibrationMmS()
                    );
                })
                .collect(Collectors.toList());
    }
}
