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

    private final SensorBufferRegistry registry;

    public RealtimeEquipmentService(SensorBufferRegistry registry) {
        this.registry = registry;
    }

    public String statusOverride(String equipmentCode) {
        Long latestTimestamp = latestTimestamp(equipmentCode);
        if (latestTimestamp == null) {
            return null;
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
