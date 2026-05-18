package com.example.phm.vibration.service;

import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.analysis.service.AiAnalysisClient;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import org.springframework.stereotype.Service;

@Service
public class VibrationIngestionService {

    private final AiAnalysisClient aiAnalysisClient;

    public VibrationIngestionService(AiAnalysisClient aiAnalysisClient) {
        this.aiAnalysisClient = aiAnalysisClient;
    }

    public VibrationIngestionResult ingest(VibrationWindowMessage message) {
        validate(message);

        AnalyzeResponse analysis = aiAnalysisClient.analyze(message);
        return new VibrationIngestionResult(
                analysis.getVibrationWindowId(),
                analysis.getAnalysisResultId(),
                Boolean.TRUE.equals(analysis.getAlarmCreated()),
                Boolean.TRUE.equals(analysis.getRawWindowSaved()),
                analysis
        );
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
