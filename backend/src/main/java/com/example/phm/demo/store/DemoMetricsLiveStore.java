package com.example.phm.demo.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.example.phm.demo.service.DemoMetricsService;
import org.springframework.stereotype.Component;

/**
 * 1초 주기 등 외부에서 POST 로 넣는 실시간 데모 KPI (메모리).
 * DB 시드보다 우선 적용됩니다.
 */
@Component
public class DemoMetricsLiveStore {

    private final Map<String, DemoMetricsService.LineMetricsDto> lines = new ConcurrentHashMap<>();
    private final Map<String, DemoMetricsService.EquipmentRuntimeDto> equipments = new ConcurrentHashMap<>();

    public void putLine(String lineId, DemoMetricsService.LineMetricsDto metrics) {
        if (lineId == null || lineId.isBlank() || metrics == null) {
            return;
        }
        lines.put(lineId, metrics);
    }

    public void putEquipment(String equipmentCode, DemoMetricsService.EquipmentRuntimeDto runtime) {
        if (equipmentCode == null || equipmentCode.isBlank() || runtime == null) {
            return;
        }
        equipments.put(equipmentCode, runtime);
    }

    public Optional<DemoMetricsService.LineMetricsDto> line(String lineId) {
        return Optional.ofNullable(lines.get(lineId));
    }

    public Optional<DemoMetricsService.EquipmentRuntimeDto> equipment(String equipmentCode) {
        return Optional.ofNullable(equipments.get(equipmentCode));
    }

    public void clear() {
        lines.clear();
        equipments.clear();
    }

    public int lineCount() {
        return lines.size();
    }

    public int equipmentCount() {
        return equipments.size();
    }

    public List<String> lineIds() {
        return List.copyOf(lines.keySet());
    }
}
