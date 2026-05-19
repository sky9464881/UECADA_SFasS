package com.example.phm.demo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DB 시드(04_demo_metrics) 미적용 환경용 인메모리 데모 KPI.
 * {@link com.example.phm.demo.service.DemoMetricsService} 가 DB 를 우선 조회합니다.
 */
public final class DemoMetricsCatalog {

    public record LineMetrics(
            double balanceRate,
            double uph,
            double upmh,
            double productivity,
            List<Double> stationUtilization
    ) {
    }

    public record EquipmentRuntime(
            double utilizationRate,
            int defectCount,
            String operatorName,
            double cycleTimeSec,
            Double currentAmp,
            Double temperatureC,
            Double humidityPct,
            Double vibrationMmS
    ) {
    }

    private static final Map<String, LineMetrics> LINE = Map.of(
            "LINE-01", new LineMetrics(88, 520, 1240, 96, List.of(72.0, 85.0, 78.0, 90.0, 82.0, 88.0)),
            "LINE-02", new LineMetrics(82, 480, 1180, 92, List.of(70.0, 80.0, 75.0, 86.0, 78.0, 84.0)),
            "LINE-03", new LineMetrics(79, 445, 1100, 88, List.of(68.0, 76.0, 72.0, 82.0, 74.0, 79.0))
    );

    private static final Map<String, EquipmentRuntime> EQUIPMENT = Map.ofEntries(
            entry("LINE-01_CAST-01", 87.5, 2, "김주조", 58.2, 42.3, 211.0, 48.0, 1.23),
            entry("LINE-01_CNC-01", 91.2, 1, "이가공", 45.0, 38.5, 45.0, 42.0, 1.18),
            entry("LINE-01_CNC-02", 62.0, 8, "박가공", 52.0, 55.1, 48.0, 40.0, 12.30),
            entry("LINE-01_CNC-03", 89.0, 0, "최가공", 44.5, 36.2, 44.0, 41.0, 1.05),
            entry("LINE-01_WASH-01", 85.0, 1, "정세척", 38.0, 22.0, 35.0, 55.0, 0.85),
            entry("LINE-01_ASSY-01", 88.0, 2, "한조립", 72.0, 28.5, 32.0, 45.0, 0.92),
            entry("LINE-01_ASSY-02", 55.0, 0, "윤조립", 0.0, 0.0, 25.0, 44.0, 0.10),
            entry("LINE-01_TEST-01", 93.0, 0, "서검사", 28.0, 15.0, 28.0, 50.0, 0.45),
            entry("LINE-01_TEST-02", 90.5, 1, "강검사", 30.0, 16.2, 29.0, 49.0, 0.52),
            entry("LINE-02_CAST-01", 86.0, 1, "김주조", 60.0, 41.0, 208.0, 47.0, 1.15),
            entry("LINE-02_CNC-01", 64.0, 5, "이가공", 48.0, 52.0, 46.0, 41.0, 9.10),
            entry("LINE-02_CNC-02", 88.0, 1, "박가공", 46.0, 37.0, 43.0, 40.0, 1.08),
            entry("LINE-02_CNC-03", 0.0, 0, "최가공", 0.0, 0.0, 22.0, 38.0, 0.05),
            entry("LINE-02_WASH-01", 84.0, 2, "정세척", 39.0, 21.5, 34.0, 54.0, 0.88),
            entry("LINE-02_ASSY-01", 87.0, 3, "한조립", 70.0, 27.0, 31.0, 46.0, 0.95),
            entry("LINE-02_ASSY-02", 89.0, 0, "윤조립", 71.0, 26.5, 30.0, 45.0, 0.90),
            entry("LINE-02_TEST-01", 92.0, 0, "서검사", 27.0, 14.5, 27.0, 48.0, 0.42),
            entry("LINE-02_TEST-02", 58.0, 0, "강검사", 0.0, 0.0, 26.0, 48.0, 0.08),
            entry("LINE-03_CAST-01", 82.0, 1, "오주조", 62.0, 40.0, 205.0, 46.0, 1.10),
            entry("LINE-03_CNC-01", 85.0, 0, "신가공", 47.0, 35.0, 42.0, 39.0, 1.02),
            entry("LINE-03_CNC-02", 86.0, 1, "유가공", 46.5, 34.5, 41.0, 39.0, 0.98),
            entry("LINE-03_CNC-03", 84.0, 2, "임가공", 48.0, 36.0, 43.0, 40.0, 1.12),
            entry("LINE-03_WASH-01", 83.0, 1, "배세척", 40.0, 20.5, 33.0, 53.0, 0.82),
            entry("LINE-03_ASSY-01", 52.0, 0, "조조립", 0.0, 0.0, 24.0, 43.0, 0.06),
            entry("LINE-03_ASSY-02", 88.0, 1, "홍조립", 73.0, 29.0, 32.0, 44.0, 0.94),
            entry("LINE-03_TEST-01", 91.0, 0, "문검사", 29.0, 15.5, 28.0, 47.0, 0.48),
            entry("LINE-03_TEST-02", 90.0, 0, "양검사", 28.5, 15.0, 27.5, 47.0, 0.46)
    );

    private DemoMetricsCatalog() {
    }

    public static Optional<LineMetrics> line(String lineId) {
        return Optional.ofNullable(LINE.get(lineId));
    }

    public static Optional<EquipmentRuntime> equipment(String equipmentCode) {
        return Optional.ofNullable(EQUIPMENT.get(equipmentCode));
    }

    private static Map.Entry<String, EquipmentRuntime> entry(
            String code,
            double rate,
            int defects,
            String operator,
            double cycle,
            double amp,
            double temp,
            double humidity,
            double vib
    ) {
        return Map.entry(code, new EquipmentRuntime(rate, defects, operator, cycle, amp, temp, humidity, vib));
    }
}
