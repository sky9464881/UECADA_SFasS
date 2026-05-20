package com.example.phm.vibration.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.analysis.service.AiAnalysisClient;
import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.SensorFrame;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import org.springframework.stereotype.Service;

@Service
public class VibrationIngestionService {

    private final AiAnalysisClient aiAnalysisClient;
    private final SensorBufferRegistry sensorBufferRegistry;

    public VibrationIngestionService(
            AiAnalysisClient aiAnalysisClient,
            SensorBufferRegistry sensorBufferRegistry
    ) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.sensorBufferRegistry = sensorBufferRegistry;
    }

    public VibrationIngestionResult ingest(VibrationWindowMessage message) {
        validate(message);

        recordVibrationRms(message);
        AnalyzeResponse analysis = aiAnalysisClient.analyze(message, true, latestSensorSnapshot(message.getEquipmentId()));
        return new VibrationIngestionResult(
                analysis.getVibrationWindowId(),
                analysis.getAnalysisResultId(),
                Boolean.TRUE.equals(analysis.getAlarmCreated()),
                Boolean.TRUE.equals(analysis.getRawWindowSaved()),
                analysis
        );
    }

    public void recordVibrationRms(VibrationWindowMessage message) {
        Double providedRms = message.getProvidedRms();
        if (providedRms != null && Double.isFinite(providedRms)) {
            pushVibrationFrame(message, providedRms);
            return;
        }

        double sumSquares = 0.0;
        int count = 0;
        for (Double value : message.getValues()) {
            if (value == null || !Double.isFinite(value)) continue;
            sumSquares += value * value;
            count++;
        }
        if (count == 0) return;

        pushVibrationFrame(message, Math.sqrt(sumSquares / count));
    }

    private void pushVibrationFrame(VibrationWindowMessage message, double rms) {
        SensorFrame frame = new SensorFrame(timestampMs(message), rms);
        for (String key : SensorBufferKeys.lookupKeys(message.getEquipmentId(), "sensor_vibration")) {
            sensorBufferRegistry.push(key, frame);
        }
    }

    private long timestampMs(VibrationWindowMessage message) {
        String timestamp = message.getTimestamp();
        if (timestamp == null || timestamp.isBlank()) {
            return System.currentTimeMillis();
        }
        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return System.currentTimeMillis();
        }
    }

    private Map<String, Object> latestSensorSnapshot(String equipmentId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("equipment_id", equipmentId);
        putLatest(snapshot, equipmentId, "sensor_temperature");
        putLatest(snapshot, equipmentId, "sensor_current");
        putLatest(snapshot, equipmentId, "sensor_voltage");
        putLatest(snapshot, equipmentId, "sensor_vibration");
        return snapshot;
    }

    private void putLatest(Map<String, Object> snapshot, String equipmentId, String metric) {
        SensorFrame latest = null;
        String latestKey = null;
        for (String key : SensorBufferKeys.lookupKeys(equipmentId, metric)) {
            SensorBuffer buffer = sensorBufferRegistry.get(key);
            if (buffer != null && buffer.latest() != null) {
                SensorFrame frame = buffer.latest();
                if (latest == null || frame.timestampMs() > latest.timestampMs()) {
                    latest = frame;
                    latestKey = key;
                }
            }
        }
        if (latest == null) {
            snapshot.put(metric, null);
            return;
        }
        snapshot.put(metric, latest.value());
        snapshot.put(metric + "_timestamp_ms", latest.timestampMs());
        snapshot.put(metric + "_buffer_key", latestKey);
    }

    private void validate(VibrationWindowMessage message) {
        if (message.getEquipmentId() == null || message.getEquipmentId().isBlank()) {
            throw new IllegalArgumentException("equipmentId is required");
        }
        if (message.getSamplingRate() == null) {
            throw new IllegalArgumentException("samplingRate is required");
        }
        if (message.getWindowSize() == null) {
            throw new IllegalArgumentException("windowSize is required");
        }
        if (message.getWindowIndex() == null) {
            throw new IllegalArgumentException("windowIndex is required");
        }
        if (message.getValues() == null) {
            throw new IllegalArgumentException("values is required");
        }
    }
}
