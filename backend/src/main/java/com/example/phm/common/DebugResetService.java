package com.example.phm.common;

import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.vibration.repository.VibrationWindowRepository;
import com.example.phm.vibration.service.RawWindowFileStorageService;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "docker"})
public class DebugResetService {

    private final AlarmHistoryRepository alarmHistoryRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final VibrationWindowRepository vibrationWindowRepository;
    private final RawWindowFileStorageService rawWindowFileStorageService;

    public DebugResetService(
            AlarmHistoryRepository alarmHistoryRepository,
            AnalysisResultRepository analysisResultRepository,
            VibrationWindowRepository vibrationWindowRepository,
            RawWindowFileStorageService rawWindowFileStorageService
    ) {
        this.alarmHistoryRepository = alarmHistoryRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.vibrationWindowRepository = vibrationWindowRepository;
        this.rawWindowFileStorageService = rawWindowFileStorageService;
    }

    @Transactional
    public DebugResetResponse resetIngestionData() {
        long alarmRows = alarmHistoryRepository.count();
        long analysisRows = analysisResultRepository.count();
        long vibrationRows = vibrationWindowRepository.count();

        alarmHistoryRepository.deleteAllInBatch();
        analysisResultRepository.deleteAllInBatch();
        vibrationWindowRepository.deleteAllInBatch();
        long rawFiles = rawWindowFileStorageService.deleteAll();

        return new DebugResetResponse(alarmRows, analysisRows, vibrationRows, rawFiles);
    }
}
