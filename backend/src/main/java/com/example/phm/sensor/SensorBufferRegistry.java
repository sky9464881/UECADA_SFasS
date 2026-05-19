package com.example.phm.sensor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class SensorBufferRegistry {

    private static final int DEFAULT_CAPACITY = 1000;

<<<<<<< HEAD
    // 센서 타입 접미사 → 버퍼 크기 (BE버퍼 시트 기준)
    private static final Map<String, Integer> SUFFIX_CAPACITY = Map.ofEntries(
            Map.entry(":vibration_x",    36000),
            Map.entry(":vibration_y",    36000),
            Map.entry(":vibration_z",    36000),
            Map.entry(":spindle_load",   7200),
            Map.entry(":spindle_rpm",    7200),
            Map.entry(":current",        7200),
            Map.entry(":feed_rate",      3600),
            Map.entry(":temperature",    600),   // ENV:temperature 는 아래서 override
=======
    // 센서 타입 접미사 -> 버퍼 크기 (DAS/X_DAS 실제 수집 항목 기준)
    private static final Map<String, Integer> SUFFIX_CAPACITY = Map.ofEntries(
            Map.entry(":sensor_vibration", 600),
            Map.entry(":sensor_current", 600),
            Map.entry(":sensor_voltage", 600),
            Map.entry(":sensor_temperature", 600),
            Map.entry(":spindle_load",   7200),
            Map.entry(":spindle_rpm",    7200),
            Map.entry(":current",        7200),
            Map.entry(":voltage",        7200),
            Map.entry(":feed_rate",      3600),
            Map.entry(":temperature",    600),
>>>>>>> feature/develop_before
            Map.entry(":pressure",       600),
            Map.entry(":water_temp",     300),
            Map.entry(":flow_rate",      300),
            Map.entry(":torque",         500),
            Map.entry(":cycle_time",     100),
            Map.entry(":leak_pressure",  200)
    );

<<<<<<< HEAD
    // ENV: 접두사 버퍼는 크기가 다른 것들
    private static final Map<String, Integer> EXACT_CAPACITY = Map.of(
            "ENV:temperature", 720
    );

=======
>>>>>>> feature/develop_before
    private final ConcurrentHashMap<String, SensorBuffer> buffers = new ConcurrentHashMap<>();

    public SensorBuffer getOrCreate(String bufferKey) {
        return buffers.computeIfAbsent(bufferKey, k -> new SensorBuffer(resolveCapacity(k)));
    }

    public SensorBuffer get(String bufferKey) {
        return buffers.get(bufferKey);
    }

    public Set<String> registeredKeys() {
        return buffers.keySet();
    }

    public void push(String bufferKey, SensorFrame frame) {
        getOrCreate(bufferKey).push(frame);
    }

    public List<SensorFrame> snapshot(String bufferKey) {
        SensorBuffer buf = buffers.get(bufferKey);
        return buf != null ? buf.snapshot() : List.of();
    }

    private int resolveCapacity(String bufferKey) {
<<<<<<< HEAD
        Integer exact = EXACT_CAPACITY.get(bufferKey);
        if (exact != null) return exact;
=======
>>>>>>> feature/develop_before
        for (var entry : SUFFIX_CAPACITY.entrySet()) {
            if (bufferKey.endsWith(entry.getKey())) return entry.getValue();
        }
        return DEFAULT_CAPACITY;
    }
}
