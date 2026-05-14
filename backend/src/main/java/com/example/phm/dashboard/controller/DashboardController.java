package com.example.phm.dashboard.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.dashboard.dto.DashboardSummaryResponse;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final EquipmentRepository equipmentRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AlarmHistoryRepository alarmHistoryRepository;

    public DashboardController(
            EquipmentRepository equipmentRepository,
            AnalysisResultRepository analysisResultRepository,
            AlarmHistoryRepository alarmHistoryRepository
    ) {
        this.equipmentRepository = equipmentRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.alarmHistoryRepository = alarmHistoryRepository;
    }

    @GetMapping("/api/dashboard/summary")
    public DashboardSummaryResponse summary() {
        List<Equipment> equipments = equipmentRepository.findAll();
        List<AnalysisResult> recentResults = analysisResultRepository.findTop100ByOrderByCreatedAtDesc();
        List<AlarmHistory> recentAlarms = alarmHistoryRepository.findTop100ByOrderByOccurredAtDesc();

        return new DashboardSummaryResponse(
                equipments.size(),
                recentResults.size(),
                alarmHistoryRepository.count(),
                statusDistribution(equipments),
                alarmDistribution(recentAlarms)
        );
    }

    private List<DashboardSummaryResponse.DistributionItem> statusDistribution(List<Equipment> equipments) {
        Map<String, Long> counts = defaultAlarmCounts();
        for (Equipment equipment : equipments) {
            String level = analysisResultRepository
                    .findTopByEquipmentCodeOrderByCreatedAtDesc(equipment.getEquipmentCode())
                    .map(AnalysisResult::getAlarmLevel)
                    .orElse("normal");
            counts.merge(normalizeLevel(level), 1L, Long::sum);
        }
        return toDistributionItems(counts);
    }

    private List<DashboardSummaryResponse.DistributionItem> alarmDistribution(List<AlarmHistory> recentAlarms) {
        Map<String, Long> counts = defaultAlarmCounts();
        for (AlarmHistory alarm : recentAlarms) {
            counts.merge(normalizeLevel(alarm.getAlarmLevel()), 1L, Long::sum);
        }
        return toDistributionItems(counts);
    }

    private Map<String, Long> defaultAlarmCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("normal", 0L);
        counts.put("warning", 0L);
        counts.put("danger", 0L);
        return counts;
    }

    private List<DashboardSummaryResponse.DistributionItem> toDistributionItems(Map<String, Long> counts) {
        List<DashboardSummaryResponse.DistributionItem> items = new ArrayList<>();
        counts.forEach((name, value) -> items.add(new DashboardSummaryResponse.DistributionItem(name, value)));
        return items;
    }

    private String normalizeLevel(String alarmLevel) {
        if (alarmLevel == null || alarmLevel.isBlank()) {
            return "normal";
        }
        return alarmLevel.toLowerCase(Locale.ROOT);
    }
}
