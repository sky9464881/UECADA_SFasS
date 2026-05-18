package com.example.phm.demo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.example.phm.demo.DemoMetricsCatalog;
import com.example.phm.demo.entity.EquipmentRuntimeDemo;
import com.example.phm.demo.entity.LineKpiLog;
import com.example.phm.demo.entity.LineStationBalance;
import com.example.phm.demo.repository.EquipmentRuntimeDemoRepository;
import com.example.phm.demo.repository.LineKpiLogRepository;
import com.example.phm.demo.repository.LineStationBalanceRepository;
import com.example.phm.demo.store.DemoMetricsLiveStore;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoMetricsService {

    private final LineKpiLogRepository lineKpiLogRepository;
    private final LineStationBalanceRepository stationBalanceRepository;
    private final EquipmentRuntimeDemoRepository runtimeDemoRepository;
    private final DemoMetricsLiveStore liveStore;

    public DemoMetricsService(
            LineKpiLogRepository lineKpiLogRepository,
            LineStationBalanceRepository stationBalanceRepository,
            EquipmentRuntimeDemoRepository runtimeDemoRepository,
            DemoMetricsLiveStore liveStore
    ) {
        this.lineKpiLogRepository = lineKpiLogRepository;
        this.stationBalanceRepository = stationBalanceRepository;
        this.runtimeDemoRepository = runtimeDemoRepository;
        this.liveStore = liveStore;
    }

    public record LineMetricsDto(
            Double balanceRate,
            Double uph,
            Double upmh,
            Double productivity,
            List<Double> stationUtilization
    ) {
    }

    public record EquipmentRuntimeDto(
            Double utilizationRate,
            Integer defectCount,
            String operatorName,
            Double cycleTimeSec,
            Double currentAmp,
            Double temperatureC,
            Double humidityPct,
            Double vibrationMmS
    ) {
    }

    @Transactional(readOnly = true)
    public Map<String, LineMetricsDto> lineMetricsFor(List<String> lineIds) {
        Map<String, LineMetricsDto> result = new HashMap<>();
        for (String lineId : lineIds) {
            result.put(lineId, lineMetrics(lineId));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public LineMetricsDto lineMetrics(String lineId) {
        Optional<LineMetricsDto> live = liveStore.line(lineId);
        if (live.isPresent()) {
            return live.get();
        }
        try {
            Optional<LineKpiLog> kpi = lineKpiLogRepository.findTopByLineIdOrderByRecordedAtDesc(lineId);
            List<LineStationBalance> stations = stationBalanceRepository.findByLineIdOrderByStationNoAsc(lineId);
            if (kpi.isPresent() || !stations.isEmpty()) {
                LineKpiLog log = kpi.orElse(null);
                double uph = bd(log == null ? null : log.getLineUph(), 0);
                double upmh = bd(log == null ? null : log.getLineUpmh(), 0);
                double balance = bd(log == null ? null : log.getLineBalanceRate(), 0);
                List<Double> stationPct = stations.stream()
                        .map(s -> bd(s.getUtilizationPct(), 0))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (stationPct.isEmpty()) {
                    stationPct = fallbackStations(lineId);
                }
                double productivity = productivityFromUpmh(upmh);
                return new LineMetricsDto(balance, uph, upmh, productivity, stationPct);
            }
        } catch (DataAccessException ignored) {
            // 테이블 미적용 시 인메모리 폴백
        }
        return catalogLine(lineId);
    }

    @Transactional(readOnly = true)
    public Map<String, EquipmentRuntimeDto> equipmentRuntimeFor(List<String> equipmentCodes) {
        Map<String, EquipmentRuntimeDto> result = new HashMap<>();
        for (String code : equipmentCodes) {
            liveStore.equipment(code).ifPresent(rt -> result.put(code, rt));
        }
        List<String> missing = equipmentCodes.stream().filter(c -> !result.containsKey(c)).toList();
        if (missing.isEmpty()) {
            return result;
        }
        try {
            List<EquipmentRuntimeDemo> rows = runtimeDemoRepository.findByEquipmentCodeIn(missing);
            for (EquipmentRuntimeDemo row : rows) {
                result.put(row.getEquipmentCode(), fromEntity(row));
            }
        } catch (DataAccessException ignored) {
            // fall through
        }
        for (String code : missing) {
            result.computeIfAbsent(code, this::catalogEquipment);
        }
        return result;
    }

    private EquipmentRuntimeDto catalogEquipment(String code) {
        return DemoMetricsCatalog.equipment(code)
                .map(this::fromCatalog)
                .orElse(new EquipmentRuntimeDto(0.0, 0, "-", 0.0, null, null, null, null));
    }

    private LineMetricsDto catalogLine(String lineId) {
        return DemoMetricsCatalog.line(lineId)
                .map(m -> new LineMetricsDto(
                        m.balanceRate(),
                        m.uph(),
                        m.upmh(),
                        m.productivity(),
                        m.stationUtilization()
                ))
                .orElse(new LineMetricsDto(0.0, 0.0, 0.0, 0.0, List.of()));
    }

    private List<Double> fallbackStations(String lineId) {
        return DemoMetricsCatalog.line(lineId)
                .map(DemoMetricsCatalog.LineMetrics::stationUtilization)
                .orElse(List.of());
    }

    private double productivityFromUpmh(double upmh) {
        if (upmh <= 0) {
            return 0;
        }
        return Math.min(100, Math.round(upmh / 13.0));
    }

    private EquipmentRuntimeDto fromEntity(EquipmentRuntimeDemo row) {
        return new EquipmentRuntimeDto(
                bd(row.getUtilizationRate(), 0),
                row.getDefectCount() == null ? 0 : row.getDefectCount(),
                row.getOperatorName() == null ? "-" : row.getOperatorName(),
                bd(row.getCycleTimeSec(), 0),
                bdOrNull(row.getCurrentAmp()),
                bdOrNull(row.getTemperatureC()),
                bdOrNull(row.getHumidityPct()),
                bdOrNull(row.getVibrationMmS())
        );
    }

    private EquipmentRuntimeDto fromCatalog(DemoMetricsCatalog.EquipmentRuntime m) {
        return new EquipmentRuntimeDto(
                m.utilizationRate(),
                m.defectCount(),
                m.operatorName(),
                m.cycleTimeSec(),
                m.currentAmp(),
                m.temperatureC(),
                m.humidityPct(),
                m.vibrationMmS()
        );
    }

    private static double bd(BigDecimal value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }

    private static Double bdOrNull(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
