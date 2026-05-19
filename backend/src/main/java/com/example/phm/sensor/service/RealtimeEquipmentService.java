package com.example.phm.sensor.service;

import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.SensorFrame;
import org.springframework.stereotype.Service;

@Service
public class RealtimeEquipmentService {

    private static final long ACTIVE_MAX_AGE_MS = 30_000L;
    private static final long STALE_MAX_AGE_MS = 120_000L;

    // 장비 on/off와 무관하게 0이 아닌 값을 유지하는 센서 → 꺼짐 판단에서 제외
    // sensor_*: das-simulator가 항상 전송
    // cycle_time: 꺼져도 마지막 사이클 시간이 남아있음
    private static final java.util.Set<String> DAS_COMMON_METRICS = java.util.Set.of(
            "sensor_vibration", "sensor_current", "sensor_voltage", "sensor_temperature", "cycle_time"
    );

    private final SensorBufferRegistry registry;

    public RealtimeEquipmentService(SensorBufferRegistry registry) {
        this.registry = registry;
    }

    public String statusOverride(String equipmentCode) {
        Long latestTimestamp = latestTimestamp(equipmentCode);
        if (latestTimestamp == null) {
            return null;
        }

        // 데이터가 들어오고 있지만 모든 센서값이 0.0 → 장비가 명시적으로 꺼진 상태
        if (isAllSensorsZero(equipmentCode)) {
            return "MAINTENANCE";
        }

        long ageMs = Math.max(0L, System.currentTimeMillis() - latestTimestamp);
        if (ageMs <= ACTIVE_MAX_AGE_MS) {
            return "RUNNING";
        }
        if (ageMs <= STALE_MAX_AGE_MS) {
            return "STANDBY";
        }
        return "MAINTENANCE";
    }

    public Long latestTimestamp(String equipmentCode) {
        Long latest = null;
        for (String metric : SensorBufferKeys.MONITORING_METRICS) {
            SensorFrame frame = latestFrame(equipmentCode, metric);
            if (frame != null && (latest == null || frame.timestampMs() > latest)) {
                latest = frame.timestampMs();
            }
        }
        return latest;
    }

    /**
     * 모니터링 중인 센서 중 2개 이상 데이터가 있고 전부 0.0이면 장비가 꺼진 것으로 판단.
     * nodered는 장비 off 시 통신을 끊지 않고 0.0 값을 전송하므로 이 방식으로 감지.
     */
    private boolean isAllSensorsZero(String equipmentCode) {
        int checked = 0;
        int zeros = 0;
        for (String metric : SensorBufferKeys.MONITORING_METRICS) {
            if (DAS_COMMON_METRICS.contains(metric)) continue; // 공통 DAS 센서 제외
            SensorFrame frame = latestFrame(equipmentCode, metric);
            if (frame != null) {
                checked++;
                if (frame.value() == 0.0) zeros++;
            }
        }
        return checked >= 2 && checked == zeros;
    }

    private SensorFrame latestFrame(String equipmentCode, String metric) {
        SensorFrame latest = null;
        for (String key : SensorBufferKeys.lookupKeys(equipmentCode, metric)) {
            SensorBuffer buffer = registry.get(key);
            if (buffer != null && buffer.latest() != null) {
                SensorFrame frame = buffer.latest();
                if (latest == null || frame.timestampMs() > latest.timestampMs()) {
                    latest = frame;
                }
            }
        }
        return latest;
    }
}
