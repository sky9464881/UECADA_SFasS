package com.example.phm.dashboard.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.dashboard.dto.DashboardFrontendResponse;
import com.example.phm.dashboard.dto.DashboardSummaryResponse;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.line.dto.LineResponse;
import com.example.phm.line.service.LineAggregationService;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final EquipmentRepository equipmentRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AlarmHistoryRepository alarmHistoryRepository;
    private final LineAggregationService lineAggregationService;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;

    public DashboardController(
            EquipmentRepository equipmentRepository,
            AnalysisResultRepository analysisResultRepository,
            AlarmHistoryRepository alarmHistoryRepository,
            LineAggregationService lineAggregationService,
            VibrationWindowMonitorService vibrationWindowMonitorService
    ) {
        this.equipmentRepository = equipmentRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.alarmHistoryRepository = alarmHistoryRepository;
        this.lineAggregationService = lineAggregationService;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
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

    @GetMapping("/api/dashboard/frontend")
    public DashboardFrontendResponse frontendSummary() {
        List<LineResponse> lines = lineAggregationService.getLines("FACTORY-01");

        DashboardFrontendResponse.StatusDonut statusDonut = statusDonut(lines);
        DashboardFrontendResponse.AlarmSummary alarmSummary = realtimeAlarmSummary();
        List<DashboardFrontendResponse.LineStat> lineStats = lines.stream()
                .map(line -> new DashboardFrontendResponse.LineStat(
                        line.lineId(),
                        line.lineName(),
                        line.latestOee()
                ))
                .toList();
        List<DashboardFrontendResponse.OeeHourlySeries> hourlySeries = lines.stream()
                .map(this::hourlySeries)
                .toList();

        return new DashboardFrontendResponse(
                averageOee(lines),
                statusDonut,
                alarmSummary,
                lineStats,
                hourlySeries
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

    private DashboardFrontendResponse.StatusDonut statusDonut(List<LineResponse> lines) {
        long running = lines.stream().mapToLong(LineResponse::equipmentRunning).sum();
        long standby = lines.stream().mapToLong(LineResponse::equipmentStandby).sum();
        long alarm = lines.stream().mapToLong(LineResponse::equipmentAlarm).sum();
        long maintenance = lines.stream().mapToLong(LineResponse::equipmentMaintenance).sum();
        return new DashboardFrontendResponse.StatusDonut(
                running,
                standby,
                alarm,
                maintenance,
                running + standby + alarm + maintenance
        );
    }

    private DashboardFrontendResponse.AlarmSummary realtimeAlarmSummary() {
        List<VibrationRealtimeResponse> realtime = vibrationWindowMonitorService.latestRealtimeAll();
        long critical = realtime.stream().filter(item -> realtimeAlarmLevelEquals(item, "danger")).count();
        long warning = realtime.stream().filter(item -> realtimeAlarmLevelEquals(item, "warning")).count();
        long open = critical + warning;
        return new DashboardFrontendResponse.AlarmSummary(open, critical, warning, 0, open);
    }

    private DashboardFrontendResponse.OeeHourlySeries hourlySeries(LineResponse line) {
        List<String> labels = List.of(
                "00:00", "02:00", "04:00", "06:00", "08:00", "10:00", "12:00",
                "14:00", "16:00", "18:00", "20:00", "22:00", "24:00"
        );
        double base = line.latestOee() == null ? 0.0 : line.latestOee();
        List<DashboardFrontendResponse.OeePoint> points = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            double value = clamp(base - 2.4 + (i * 0.4));
            points.add(new DashboardFrontendResponse.OeePoint(labels.get(i), round1(value)));
        }
        return new DashboardFrontendResponse.OeeHourlySeries(line.lineId(), line.lineName(), points);
    }

    private Double averageOee(List<LineResponse> lines) {
        return lines.stream()
                .map(LineResponse::latestOee)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .map(this::round1)
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private boolean realtimeAlarmLevelEquals(VibrationRealtimeResponse item, String level) {
        return item.analysis() != null
                && item.analysis().getAlarmLevel() != null
                && item.analysis().getAlarmLevel().equalsIgnoreCase(level);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
