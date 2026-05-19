package com.example.phm.kpi.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.kpi.buffer.EquipmentAvailabilityBuffer;
import org.springframework.stereotype.Service;

@Service
public class KpiRealtimeService {

    private static final int REALTIME_WINDOW_MINUTES = 10;

    private final EquipmentAvailabilityBuffer availabilityBuffer;
    private final EquipmentRepository equipmentRepository;

    public KpiRealtimeService(
            EquipmentAvailabilityBuffer availabilityBuffer,
            EquipmentRepository equipmentRepository
    ) {
        this.availabilityBuffer = availabilityBuffer;
        this.equipmentRepository = equipmentRepository;
    }

    /**
     * 라인별 실시간 OEE (10분 슬라이딩 윈도우)
     * OEE = 가용성 × 성능 × 품질 / 10000
     */
    public Map<String, Double> lineOeeMap() {
        List<Equipment> equipments = equipmentRepository.findAll();

        // 라인별 설비 그룹
        Map<String, List<Equipment>> byLine = new java.util.HashMap<>();
        for (Equipment e : equipments) {
            if (e.getLocation() == null) continue;
            byLine.computeIfAbsent(e.getLocation(), k -> new java.util.ArrayList<>()).add(e);
        }

        Map<String, Double> result = new java.util.HashMap<>();
        byLine.forEach((lineId, lineEquipments) -> {
            double avail = lineEquipments.stream()
                    .mapToDouble(e -> availabilityBuffer.availability(e.getEquipmentCode(), REALTIME_WINDOW_MINUTES))
                    .average().orElse(0.0);

            double perf = lineEquipments.stream()
                    .mapToDouble(e -> availabilityBuffer.performance(
                            e.getEquipmentCode(),
                            REALTIME_WINDOW_MINUTES,
                            nominalCycleSec(e.getEquipmentCode())))
                    .average().orElse(100.0);

            List<Equipment> testEquipments = lineEquipments.stream()
                    .filter(e -> isTest(e.getEquipmentCode()))
                    .toList();
            double quality = testEquipments.isEmpty() ? 100.0 :
                    testEquipments.stream()
                            .mapToDouble(e -> availabilityBuffer.quality(e.getEquipmentCode(), REALTIME_WINDOW_MINUTES))
                            .average().orElse(100.0);

            double oee = Math.round((avail * perf * quality / 10000.0) * 10.0) / 10.0;
            result.put(lineId, oee);
        });
        return result;
    }

    public double factoryOee(Map<String, Double> lineOeeMap) {
        if (lineOeeMap.isEmpty()) return 0.0;
        double avg = lineOeeMap.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        return Math.round(avg * 10.0) / 10.0;
    }

    private int nominalCycleSec(String equipmentCode) {
        if (equipmentCode == null) return 60;
        String upper = equipmentCode.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, Integer> entry : EquipmentAvailabilityBuffer.NOMINAL_CYCLE_SEC.entrySet()) {
            if (upper.contains(entry.getKey())) return entry.getValue();
        }
        return 60;
    }

    private boolean isTest(String equipmentCode) {
        return equipmentCode != null && equipmentCode.toUpperCase(Locale.ROOT).contains("TEST");
    }
}
