package com.example.phm.dashboard.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.example.phm.alarm.entity.Alarm;
import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.alarm.repository.AlarmRepository;
import org.springframework.data.domain.PageRequest;
import com.example.phm.kpi.entity.LineKpiLog;
import com.example.phm.kpi.repository.LineKpiLogRepository;
import com.example.phm.kpi.service.KpiRealtimeService;
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

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");

    private final EquipmentRepository equipmentRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AlarmHistoryRepository alarmHistoryRepository;
    private final AlarmRepository alarmRepository;
    private final LineAggregationService lineAggregationService;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;
    private final LineKpiLogRepository lineKpiLogRepository;
    private final KpiRealtimeService kpiRealtimeService;

    public DashboardController(
            EquipmentRepository equipmentRepository,
            AnalysisResultRepository analysisResultRepository,
            AlarmHistoryRepository alarmHistoryRepository,
            AlarmRepository alarmRepository,
            LineAggregationService lineAggregationService,
            VibrationWindowMonitorService vibrationWindowMonitorService,
            LineKpiLogRepository lineKpiLogRepository,
            KpiRealtimeService kpiRealtimeService
    ) {
        this.equipmentRepository = equipmentRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.alarmHistoryRepository = alarmHistoryRepository;
        this.alarmRepository = alarmRepository;
        this.lineAggregationService = lineAggregationService;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
        this.lineKpiLogRepository = lineKpiLogRepository;
        this.kpiRealtimeService = kpiRealtimeService;
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

        // 실시간 OEE: 가용성 × 성능 × 품질 (10분 슬라이딩 윈도우)
        Map<String, Double> lineOeeMap = kpiRealtimeService.lineOeeMap();
        double factoryOee = kpiRealtimeService.factoryOee(lineOeeMap);

        DashboardFrontendResponse.StatusDonut statusDonut = statusDonut(lines);
        DashboardFrontendResponse.AlarmSummary alarmSummary = realtimeAlarmSummary();
        List<DashboardFrontendResponse.LineStat> lineStats = lines.stream()
                .map(line -> new DashboardFrontendResponse.LineStat(
                        line.lineId(),
                        line.lineName(),
                        lineOeeMap.getOrDefault(line.lineId(), line.latestOee())
                ))
                .toList();
        List<DashboardFrontendResponse.OeeHourlySeries> hourlySeries = lines.stream()
                .map(this::hourlySeries)
                .toList();

        return new DashboardFrontendResponse(
                factoryOee,
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
        // alarm 테이블 기반으로 통일 (대시보드와 알람 탭 카운트 일치)
        List<Alarm> recent = alarmRepository.findByFilters(null, null, null, null, PageRequest.of(0, 1000));
        long total = recent.size();
        long critical = recent.stream().filter(a -> isSeverity(a, "CRITICAL", "DANGER")).count();
        long resolved = recent.stream().filter(a -> "RESOLVED".equals(a.getStatus())).count();
        long open = recent.stream().filter(a -> "OPEN".equals(a.getStatus())).count();
        return new DashboardFrontendResponse.AlarmSummary(total, critical, 0, resolved, open);
    }

    private DashboardFrontendResponse.OeeHourlySeries hourlySeries(LineResponse line) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // 오늘 데이터 조회 후 시(hour) 단위 평균
        List<LineKpiLog> logs = lineKpiLogRepository.findByLineIdAndRecordedAtBetween(
                line.lineId(), todayStart, now.plusSeconds(1)
        );
        Map<Integer, Double> oeeByHour = logs.stream()
                .filter(log -> log.getLineOee() != null)
                .collect(Collectors.groupingBy(
                        log -> log.getRecordedAt().getHour(),
                        Collectors.averagingDouble(LineKpiLog::getLineOee)
                ));

        // 하루 전체 1시간 슬롯 24개 (00:00 ~ 23:00), 미기록은 null
        List<DashboardFrontendResponse.OeePoint> points = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d:00", h);
            Double oee = oeeByHour.containsKey(h) ? round1(oeeByHour.get(h)) : null;
            points.add(new DashboardFrontendResponse.OeePoint(label, oee));
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
