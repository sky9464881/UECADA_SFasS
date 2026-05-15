package com.example.phm.alarm.controller;

import com.example.phm.alarm.dto.AlarmFocusAnalysisResponse;
import com.example.phm.alarm.dto.AlarmFocusSelectionResponse;
import com.example.phm.alarm.service.AlarmFocusAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlarmFocusAnalysisController {

    private final AlarmFocusAnalysisService alarmFocusAnalysisService;

    public AlarmFocusAnalysisController(AlarmFocusAnalysisService alarmFocusAnalysisService) {
        this.alarmFocusAnalysisService = alarmFocusAnalysisService;
    }

    @GetMapping("/api/alarm-histories/{alarmId}/focus-analysis")
    public AlarmFocusAnalysisResponse findFocusAnalysis(
            @PathVariable Long alarmId,
            @RequestParam(defaultValue = "10") int paddingSeconds,
            @RequestParam(defaultValue = "40000") int maxPoints
    ) {
        return alarmFocusAnalysisService.findFocusAnalysis(alarmId, paddingSeconds, maxPoints);
    }

    @GetMapping("/api/alarm-histories/{alarmId}/focus-analysis/selection")
    public AlarmFocusSelectionResponse analyzeSelection(
            @PathVariable Long alarmId,
            @RequestParam long startMillis,
            @RequestParam long endMillis,
            @RequestParam(defaultValue = "64000") int maxSamples
    ) {
        return alarmFocusAnalysisService.analyzeSelection(alarmId, startMillis, endMillis, maxSamples);
    }
}
