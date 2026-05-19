package com.example.phm.vibration.service;

<<<<<<< HEAD
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.analysis.service.AiAnalysisClient;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.entity.VibrationWindow;
import com.example.phm.vibration.repository.VibrationWindowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
=======
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.analysis.service.AiAnalysisClient;
import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.SensorFrame;
import com.example.phm.vibration.dto.VibrationWindowMessage;
>>>>>>> feature/develop_before
import org.springframework.stereotype.Service;

@Service
public class VibrationIngestionService {

<<<<<<< HEAD
    private static final Duration RAW_WINDOW_SAVE_INTERVAL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, LocalDateTime> lastWindowSavedAt = new ConcurrentHashMap<>();

    private final VibrationWindowRepository vibrationWindowRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AlarmHistoryRepository alarmHistoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final ObjectMapper objectMapper;

    public VibrationIngestionService(
            VibrationWindowRepository vibrationWindowRepository,
            AnalysisResultRepository analysisResultRepository,
            AlarmHistoryRepository alarmHistoryRepository,
            EquipmentRepository equipmentRepository,
            AiAnalysisClient aiAnalysisClient,
            ObjectMapper objectMapper
    ) {
        this.vibrationWindowRepository = vibrationWindowRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.alarmHistoryRepository = alarmHistoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.aiAnalysisClient = aiAnalysisClient;
        this.objectMapper = objectMapper;
    }

    public VibrationIngestionResult ingest(VibrationWindowMessage message, String rawPayload) {
        validate(message);

        LocalDateTime measuredAt = parseMeasuredAt(message.getTimestamp());
        ensureEquipmentExists(message.getEquipmentId());

        AnalyzeResponse analysis = aiAnalysisClient.analyze(message);
        AnalysisResult analysisResultRef = analysisResultRepository.getReferenceById(analysis.getAnalysisResultId());

        boolean alarmCreated = saveAlarmIfNeeded(analysisResultRef, analysis, measuredAt);

        VibrationWindow vibrationWindow = null;
        if (shouldSaveRawWindow(message.getEquipmentId(), measuredAt)) {
            vibrationWindow = saveVibrationWindow(message, measuredAt);
            recordWindowSaveTime(message.getEquipmentId(), measuredAt);
        }

        return new VibrationIngestionResult(vibrationWindow, analysisResultRef, alarmCreated, null, analysis);
    }

    private boolean shouldSaveRawWindow(String equipmentCode, LocalDateTime measuredAt) {
        LocalDateTime last = lastWindowSavedAt.get(equipmentCode);
        return last == null || !measuredAt.isBefore(last.plus(RAW_WINDOW_SAVE_INTERVAL));
    }

    private void recordWindowSaveTime(String equipmentCode, LocalDateTime measuredAt) {
        lastWindowSavedAt.put(equipmentCode, measuredAt);
=======
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

        AnalyzeResponse analysis = aiAnalysisClient.analyze(message, true, latestSensorSnapshot(message.getEquipmentId()));
        return new VibrationIngestionResult(
                analysis.getVibrationWindowId(),
                analysis.getAnalysisResultId(),
                Boolean.TRUE.equals(analysis.getAlarmCreated()),
                Boolean.TRUE.equals(analysis.getRawWindowSaved()),
                analysis
        );
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
>>>>>>> feature/develop_before
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
<<<<<<< HEAD

    private void ensureEquipmentExists(String equipmentCode) {
        if (equipmentRepository.existsByEquipmentCode(equipmentCode)) {
            return;
        }

        Equipment equipment = new Equipment();
        equipment.setEquipmentCode(equipmentCode);
        equipment.setEquipmentName(equipmentCode);
        equipment.setLocation("auto-registered");
        equipmentRepository.save(equipment);
    }

    private VibrationWindow saveVibrationWindow(VibrationWindowMessage message, LocalDateTime measuredAt) {
        VibrationWindow vibrationWindow = new VibrationWindow();
        vibrationWindow.setEquipmentCode(message.getEquipmentId());
        vibrationWindow.setMeasuredAt(measuredAt);
        vibrationWindow.setSamplingRate(message.getSamplingRate());
        vibrationWindow.setRpm(message.getRpm());
        vibrationWindow.setWindowSize(message.getWindowSize());
        vibrationWindow.setWindowIndex(message.getWindowIndex().longValue());
        vibrationWindow.setValuesJson(serializeValues(message));
        return vibrationWindowRepository.save(vibrationWindow);
    }

    private String serializeValues(VibrationWindowMessage message) {
        try {
            return objectMapper.writeValueAsString(message.getValues());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize vibration values", e);
        }
    }

    private boolean saveAlarmIfNeeded(AnalysisResult analysisResultRef, AnalyzeResponse analysis, LocalDateTime measuredAt) {
        if (isAlarmLevel(analysis.getAlarmLevel())) {
            return openOrUpdateAlarm(analysisResultRef, analysis, measuredAt);
        }
        closeOpenAlarmIfNeeded(analysis.getEquipmentId(), measuredAt);
        return false;
    }

    private boolean openOrUpdateAlarm(AnalysisResult analysisResultRef, AnalyzeResponse analysis, LocalDateTime measuredAt) {
        return alarmHistoryRepository
                .findTopByEquipmentCodeAndStatusOrderByOccurredAtDesc(analysis.getEquipmentId(), "open")
                .map(alarm -> {
                    alarm.setAlarmLevel(worseAlarmLevel(alarm.getAlarmLevel(), analysis.getAlarmLevel()));
                    alarm.setAnalysisResult(analysisResultRef);
                    alarm.setMessage(buildAlarmMessage(analysis, alarm.getOccurredAt()));
                    alarmHistoryRepository.save(alarm);
                    return false;
                })
                .orElseGet(() -> {
                    AlarmHistory alarm = new AlarmHistory();
                    alarm.setEquipmentCode(analysis.getEquipmentId());
                    alarm.setAnalysisResult(analysisResultRef);
                    alarm.setAlarmLevel(analysis.getAlarmLevel());
                    alarm.setStatus("open");
                    alarm.setOccurredAt(measuredAt);
                    alarm.setMessage(buildAlarmMessage(analysis, measuredAt));
                    alarmHistoryRepository.save(alarm);
                    return true;
                });
    }

    private void closeOpenAlarmIfNeeded(String equipmentCode, LocalDateTime measuredAt) {
        alarmHistoryRepository
                .findTopByEquipmentCodeAndStatusOrderByOccurredAtDesc(equipmentCode, "open")
                .ifPresent(alarm -> {
                    long durationSeconds = Math.max(0L, Duration.between(alarm.getOccurredAt(), measuredAt).getSeconds());
                    alarm.setStatus("closed");
                    alarm.setEndedAt(measuredAt);
                    alarm.setDurationSeconds(durationSeconds);
                    alarm.setMessage(buildAlarmClosedMessage(alarm, durationSeconds));
                    alarmHistoryRepository.save(alarm);
                });
    }

    private boolean isAlarmLevel(String alarmLevel) {
        if (alarmLevel == null) {
            return false;
        }
        String normalized = alarmLevel.toLowerCase(Locale.ROOT);
        return normalized.equals("warning") || normalized.equals("danger");
    }

    private String worseAlarmLevel(String currentLevel, String nextLevel) {
        if ("danger".equalsIgnoreCase(currentLevel) || "danger".equalsIgnoreCase(nextLevel)) {
            return "danger";
        }
        return "warning";
    }

    private String buildAlarmMessage(AnalyzeResponse analysis, LocalDateTime startedAt) {
        return "Vibration anomaly active: equipmentCode=%s, alarmLevel=%s, anomalyScore=%s, prediction=%s, startedAt=%s"
                .formatted(
                        analysis.getEquipmentId(),
                        analysis.getAlarmLevel(),
                        analysis.getAnomalyScore(),
                        analysis.getPrediction(),
                        startedAt
                );
    }

    private String buildAlarmClosedMessage(AlarmHistory alarm, long durationSeconds) {
        return "Vibration anomaly closed: equipmentCode=%s, peakAlarmLevel=%s, durationSeconds=%d"
                .formatted(alarm.getEquipmentCode(), alarm.getAlarmLevel(), durationSeconds);
    }

    private LocalDateTime parseMeasuredAt(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
=======
>>>>>>> feature/develop_before
}
