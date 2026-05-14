package com.example.phm.vibration.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import com.example.phm.analysis.dto.AnalysisFeatures;
import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.service.VibrationIngestionResult;
import com.example.phm.vibration.service.VibrationIngestionService;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class VibrationWindowMqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(VibrationWindowMqttSubscriber.class);
    private static final int LOG_PAYLOAD_LIMIT = 500;

    private final ObjectMapper objectMapper;
    private final VibrationWindowMonitorService monitorService;
    private final VibrationIngestionService ingestionService;

    public VibrationWindowMqttSubscriber(
            ObjectMapper objectMapper,
            VibrationWindowMonitorService monitorService,
            VibrationIngestionService ingestionService
    ) {
        this.objectMapper = objectMapper;
        this.monitorService = monitorService;
        this.ingestionService = ingestionService;
    }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handleMessage(Message<?> message) {
        String payload = payloadAsString(message.getPayload());

        try {
            VibrationWindowMessage vibrationWindow = parseVibrationWindow(payload);
            monitorService.record(vibrationWindow);

            log.info(
                    "Received MQTT message: equipmentId={}, windowIndex={}, samplingRate={}, rpm={}, windowSize={}, valuesLength={}",
                    vibrationWindow.getEquipmentId(),
                    vibrationWindow.getWindowIndex(),
                    vibrationWindow.getSamplingRate(),
                    vibrationWindow.getRpm(),
                    vibrationWindow.getWindowSize(),
                    vibrationWindow.valuesLength()
            );

            if (vibrationWindow.getWindowSize() != null && vibrationWindow.valuesLength() != vibrationWindow.getWindowSize()) {
                log.warn(
                        "Vibration values length mismatch: equipmentId={}, windowIndex={}, windowSize={}, valuesLength={}",
                        vibrationWindow.getEquipmentId(),
                        vibrationWindow.getWindowIndex(),
                        vibrationWindow.getWindowSize(),
                        vibrationWindow.valuesLength()
                );
            }

            VibrationIngestionResult ingestionResult = ingestionService.ingest(vibrationWindow, payload);
            AnalyzeResponse analysis = ingestionResult.analysis();
            AnalysisFeatures features = analysis.getFeatures();
            log.info(
                    "Persisted vibration pipeline: vibrationWindowId={}, analysisResultId={}, alarmCreated={}, rawFilePath={}",
                    ingestionResult.vibrationWindow() != null ? ingestionResult.vibrationWindow().getId() : null,
                    ingestionResult.analysisResult().getId(),
                    ingestionResult.alarmCreated(),
                    ingestionResult.rawFilePath()
            );
            log.info(
                    "FastAPI response: equipmentId={}, windowIndex={}, rms={}, peakFrequency={}, peakToPeak={}, crestFactor={}, kurtosis={}, prediction={}, confidence={}, modelVersion={}, modelInputStrategy={}, modelStatus={}, anomalyScore={}, alarmLevel={}",
                    analysis.getEquipmentId(),
                    analysis.getWindowIndex(),
                    features == null ? null : features.getRms(),
                    features == null ? null : features.getPeakFrequency(),
                    features == null ? null : features.getPeakToPeak(),
                    features == null ? null : features.getCrestFactor(),
                    features == null ? null : features.getKurtosis(),
                    analysis.getPrediction(),
                    analysis.getConfidence(),
                    analysis.getModelVersion(),
                    analysis.getModelInputStrategy(),
                    analysis.getModelStatus(),
                    analysis.getAnomalyScore(),
                    analysis.getAlarmLevel()
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse MQTT vibration payload: {}", abbreviate(payload), exception);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "FastAPI /analyze returned status {} body={}",
                    exception.getStatusCode(),
                    abbreviate(exception.getResponseBodyAsString()),
                    exception
            );
        } catch (RestClientException exception) {
            log.warn("Failed to call FastAPI /analyze: {}", exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            log.warn("Failed to persist vibration MQTT message: {}", exception.getMessage(), exception);
        }
    }

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }

    private String abbreviate(String value) {
        if (value.length() <= LOG_PAYLOAD_LIMIT) {
            return value;
        }
        return value.substring(0, LOG_PAYLOAD_LIMIT) + "...";
    }

    private VibrationWindowMessage parseVibrationWindow(String payload) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(payload);
        if (isDasCommonVibrationWindow(root)) {
            return fromDasCommonVibrationWindow(root);
        }
        return objectMapper.treeToValue(root, VibrationWindowMessage.class);
    }

    private boolean isDasCommonVibrationWindow(JsonNode root) {
        return "VIB-01".equals(root.path("sensor_id").asText())
                && root.path("values").path("vibration_raw").isArray();
    }

    private VibrationWindowMessage fromDasCommonVibrationWindow(JsonNode root) {
        JsonNode window = root.path("window");
        JsonNode valuesNode = root.path("values").path("vibration_raw");
        List<Double> values = objectMapper.convertValue(valuesNode, new TypeReference<>() {});

        String lineId = root.path("line_id").asText("LINE-UNKNOWN");
        String equipmentId = root.path("equipment_id").asText("EQUIPMENT-UNKNOWN");
        String timestamp = firstText(root.path("timestamp"), root.path("sample").path("timestamp"), window.path("started_at"));
        int samplingRate = firstPositiveInt(window.path("sample_rate_hz"), 16000);
        int windowSize = firstPositiveInt(window.path("window_size"), firstPositiveInt(window.path("sample_count"), values.size()));
        int windowIndex = firstNonNegativeInt(window.path("seq"), firstNonNegativeInt(root.path("sample").path("seq"), 0));

        return new VibrationWindowMessage(
                lineId + "_" + equipmentId,
                timestamp,
                samplingRate,
                null,
                windowSize,
                windowIndex,
                values
        );
    }

    private String firstText(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isTextual() && !candidate.asText().isBlank()) {
                return candidate.asText();
            }
        }
        return OffsetDateTime.now().toString();
    }

    private int firstPositiveInt(JsonNode candidate, int fallback) {
        if (candidate != null && candidate.canConvertToInt() && candidate.asInt() > 0) {
            return candidate.asInt();
        }
        return fallback;
    }

    private int firstNonNegativeInt(JsonNode candidate, int fallback) {
        if (candidate != null && candidate.canConvertToInt() && candidate.asInt() >= 0) {
            return candidate.asInt();
        }
        return fallback;
    }
}
