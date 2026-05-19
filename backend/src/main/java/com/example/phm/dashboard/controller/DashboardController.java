package com.example.phm.dashboard.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.example.phm.alarm.entity.Alarm;
import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.alarm.repository.AlarmRepository;
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
    private final AlarmRepository alarmRepository;
    private final LineAggregationService lineAggregationService;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;

    public DashboardController(
            EquipmentRepository equipmentRepository,
            AnalysisResultRepository analysisResultRepository,
            AlarmHistoryRepository alarmHistoryRepository,
            AlarmRepository alarmRepository,
            LineAggregationService lineAggregationService,
            VibrationWindowMonitorService vibrationWindowMonitorService
    ) {
        this.equipmentRepository = equipmentRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.alarmHistoryRepository = alarmHistoryRepository;
        this.alarmRepository = alarmRepository;
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
        if (equipments.isEmpty()) {
            return toDistributionItems(counts);
        }
        // N+1 회피: 설비별 latest analysis 를 한 번에 묶어서 조회.
        List<String> codes = equipments.stream().map(Equipment::getEquipmentCode).toList();
        Map<String, String> latestLevelByCode = new HashMap<>();
        for (AnalysisResult ar : analysisResultRepository.findLatestForEquipmentCodes(codes)) {
            latestLevelByCode.put(ar.getEquipmentCode(), ar.getAlarmLevel());
        }
        for (Equipment equipment : equipments) {
            String level = latestLevelByCode.getOrDefault(equipment.getEquipmentCode(), "normal");
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

        // 진동 모니터 기반 카운트가 모두 0 이면 알람 테이블 기반 폴백 적용.
        // 응답 필드 시그니처는 그대로 유지하되 0 만 노출되던 문제 해소.
        if (open == 0) {
            List<Alarm> openAlarms = alarmRepository.findByFilters("OPEN", null, null, null);
            critical = openAlarms.stream()
                    .filter(a -> isSeverity(a, "CRITICAL", "DANGER"))
                    .count();
            warning = openAlarms.stream()
                    .filter(a -> isSeverity(a, "WARNING", "WARN"))
                    .count();
            open = openAlarms.size();
        }

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

    private boolean isSeverity(Alarm a, String... levels) {
        if (a.getSeverity() == null) return false;
        String s = a.getSeverity().toUpperCase(Locale.ROOT);
        for (String lv : levels) {
            if (s.equals(lv)) return true;
        }
        return false;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
