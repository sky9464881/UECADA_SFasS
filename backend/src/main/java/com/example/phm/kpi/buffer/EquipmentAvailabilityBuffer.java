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

    /** 기록 간격 (초) */
    public static final int INTERVAL_SECONDS = 2;

    /** 보관할 최대 기간 (1시간) */
    private static final int KEEP_SECONDS = 3600;

    /** 최대 레코드 수: 3600 / 2 = 1800 */
    private static final int MAX_RECORDS = KEEP_SECONDS / INTERVAL_SECONDS;

    private final Map<String, Deque<Boolean>> buffer = new ConcurrentHashMap<>();

    public void record(String equipmentCode, boolean running) {
        Deque<Boolean> deque = buffer.computeIfAbsent(equipmentCode, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(running);
            while (deque.size() > MAX_RECORDS) deque.removeFirst();
        }
    }

    /**
     * 최근 windowMinutes 분의 가동률 (0~100)
     * 2초 간격이므로 windowMinutes * 30 개 레코드 사용
     */
    public double availability(String equipmentCode, int windowMinutes) {
        int windowRecords = windowMinutes * (60 / INTERVAL_SECONDS);
        Deque<Boolean> deque = buffer.get(equipmentCode);
        if (deque == null) return 0.0;
        List<Boolean> snapshot;
        synchronized (deque) {
            snapshot = new ArrayList<>(deque);
        }
        int from = Math.max(0, snapshot.size() - windowRecords);
        List<Boolean> window = snapshot.subList(from, snapshot.size());
        if (window.isEmpty()) return 0.0;
        long running = window.stream().filter(b -> b).count();
        return Math.round((running * 100.0 / window.size()) * 10.0) / 10.0;
    }

    /** 지난 1시간 전체 가용성 (hourly DB 저장용) */
    public double hourlyAvailability(String equipmentCode) {
        return availability(equipmentCode, 60);
    }

    public Set<String> equipmentCodes() {
        return buffer.keySet();
    }
}
