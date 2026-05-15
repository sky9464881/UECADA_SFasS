package com.example.phm.analysis.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.analysis.dto.AnalysisResultResponse;
import com.example.phm.analysis.dto.AnalysisResultSaveRequest;
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalysisResultController {

    private final AnalysisResultRepository analysisResultRepository;

    public AnalysisResultController(AnalysisResultRepository analysisResultRepository) {
        this.analysisResultRepository = analysisResultRepository;
    }

    @GetMapping("/api/equipments/{equipmentCode}/analysis-results")
    public List<AnalysisResultResponse> findByEquipment(
            @PathVariable String equipmentCode,
            @RequestParam(required = false) String analysisType,
            @RequestParam(defaultValue = "100") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        if (analysisType != null && !analysisType.isBlank()) {
            return analysisResultRepository
                    .findByEquipmentCodeAndAnalysisTypeOrderByCreatedAtDesc(
                            equipmentCode, analysisType, PageRequest.of(0, safeLimit))
                    .stream()
                    .map(AnalysisResultResponse::from)
                    .toList();
        }
        return analysisResultRepository
                .findByEquipmentCodeOrderByCreatedAtDesc(equipmentCode, PageRequest.of(0, safeLimit))
                .stream()
                .map(AnalysisResultResponse::from)
                .toList();
    }

    @PostMapping("/api/analysis-results")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResultResponse save(@RequestBody AnalysisResultSaveRequest request) {
        AnalysisResult result = new AnalysisResult();
        result.setEquipmentCode(request.equipmentCode());
        result.setAnalysisType(request.analysisType());
        result.setResultJson(request.resultJson());
        return AnalysisResultResponse.from(analysisResultRepository.save(result));
    }
}
