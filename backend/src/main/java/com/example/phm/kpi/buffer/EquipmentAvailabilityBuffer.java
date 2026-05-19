package com.example.phm.kpi.buffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class EquipmentAvailabilityBuffer {

    public static final int INTERVAL_SECONDS = 2;
    private static final int KEEP_SECONDS = 3600;
    private static final int MAX_RECORDS = KEEP_SECONDS / INTERVAL_SECONDS; // 1800

    /** 설비 타입별 표준 사이클타임 (초) */
    public static final Map<String, Integer> NOMINAL_CYCLE_SEC = Map.of(
            "CAST", 60,
            "CNC",  180,
            "WASH", 60,
            "ASSY", 120,
            "TEST", 120
    );

    /** 설비별 가동 여부 (true=RUNNING) */
    private final Map<String, Deque<Boolean>> availBuffer = new ConcurrentHashMap<>();

    /** TEST 설비별 품질 여부 (true=result_ok=1) */
    private final Map<String, Deque<Boolean>> qualityBuffer = new ConcurrentHashMap<>();

    /** 설비별 실제 사이클타임 (초, 0 이상 유효값만 저장) */
    private final Map<String, Deque<Double>> cycleTimeBuffer = new ConcurrentHashMap<>();

    // ── 가용성 ──────────────────────────────────────────────

    public void record(String equipmentCode, boolean running) {
        pushBool(availBuffer, equipmentCode, running);
    }

    public double availability(String equipmentCode, int windowMinutes) {
        return calcBoolPct(availBuffer, equipmentCode, windowMinutes);
    }

    public double hourlyAvailability(String equipmentCode) {
        return availability(equipmentCode, 60);
    }

    public Set<String> equipmentCodes() {
        return availBuffer.keySet();
    }

    // ── 품질 (TEST 설비 전용) ────────────────────────────────

    public void recordQuality(String equipmentCode, boolean ok) {
        pushBool(qualityBuffer, equipmentCode, ok);
    }

    public double quality(String equipmentCode, int windowMinutes) {
        return calcBoolPct(qualityBuffer, equipmentCode, windowMinutes);
    }

    public double hourlyQuality(String equipmentCode) {
        return quality(equipmentCode, 60);
    }

    // ── 성능 (사이클타임 기반) ───────────────────────────────

    public void recordCycleTime(String equipmentCode, double cycleTimeSec) {
        if (cycleTimeSec <= 0) return; // 유효하지 않은 값 제외
        Deque<Double> deque = cycleTimeBuffer.computeIfAbsent(equipmentCode, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(cycleTimeSec);
            while (deque.size() > MAX_RECORDS) deque.removeFirst();
        }
    }

    /**
     * 성능(%) = 표준 사이클타임 / 평균 실제 사이클타임 × 100
     * 100% 초과 시 100%로 cap
     */
    public double performance(String equipmentCode, int windowMinutes, int nominalCycleSec) {
        int windowRecords = windowMinutes * (60 / INTERVAL_SECONDS);
        Deque<Double> deque = cycleTimeBuffer.get(equipmentCode);
        if (deque == null) return 100.0; // 데이터 없으면 100%
        List<Double> snapshot;
        synchronized (deque) {
            snapshot = new ArrayList<>(deque);
        }
        int from = Math.max(0, snapshot.size() - windowRecords);
        List<Double> window = snapshot.subList(from, snapshot.size());
        if (window.isEmpty()) return 100.0;
        double avgCycle = window.stream().mapToDouble(Double::doubleValue).average().orElse(nominalCycleSec);
        return Math.min(100.0, Math.round((nominalCycleSec / avgCycle * 100.0) * 10.0) / 10.0);
    }

    public double hourlyPerformance(String equipmentCode, int nominalCycleSec) {
        return performance(equipmentCode, 60, nominalCycleSec);
    }

    // ── 공통 ────────────────────────────────────────────────

    private void pushBool(Map<String, Deque<Boolean>> map, String code, boolean value) {
        Deque<Boolean> deque = map.computeIfAbsent(code, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(value);
            while (deque.size() > MAX_RECORDS) deque.removeFirst();
        }
    }

    private double calcBoolPct(Map<String, Deque<Boolean>> map, String code, int windowMinutes) {
        int windowRecords = windowMinutes * (60 / INTERVAL_SECONDS);
        Deque<Boolean> deque = map.get(code);
        if (deque == null) return 0.0;
        List<Boolean> snapshot;
        synchronized (deque) {
            snapshot = new ArrayList<>(deque);
        }
        int from = Math.max(0, snapshot.size() - windowRecords);
        List<Boolean> window = snapshot.subList(from, snapshot.size());
        if (window.isEmpty()) return 0.0;
        long trueCount = window.stream().filter(b -> b).count();
        return Math.round((trueCount * 100.0 / window.size()) * 10.0) / 10.0;
    }
}
