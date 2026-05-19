package com.example.phm.kpi.controller;

import java.util.List;
import java.util.Set;

import com.example.phm.kpi.buffer.EquipmentAvailabilityBuffer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kpi")
public class KpiController {

    private final EquipmentAvailabilityBuffer availabilityBuffer;

    public KpiController(EquipmentAvailabilityBuffer availabilityBuffer) {
        this.availabilityBuffer = availabilityBuffer;
    }

    /**
     * 설비별 가동률 (%)
     * windowMinutes 분 동안의 RUNNING 비율을 메모리에서 즉시 계산
     */
    @GetMapping("/availability")
    public List<EquipmentAvailability> availability(
            @RequestParam(defaultValue = "10") int windowMinutes
    ) {
        Set<String> codes = availabilityBuffer.equipmentCodes();
        return codes.stream()
                .map(code -> new EquipmentAvailability(
                        code,
                        availabilityBuffer.availability(code, windowMinutes)
                ))
                .toList();
    }

    public record EquipmentAvailability(String equipmentCode, double availabilityPct) {}
}
