package com.example.phm.line.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.entity.EquipmentStatus;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.equipment.repository.EquipmentStatusRepository;
import com.example.phm.line.entity.ProductionLine;
import com.example.phm.line.repository.ProductionLineRepository;
import com.example.phm.demo.service.DemoMetricsService;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.junit.jupiter.api.Test;

class LineAggregationServiceTest {

    /**
     * N+1 회피 검증: 설비 N 대에 대해 analysisResultRepository.findTopBy... 가
     * 설비 수 만큼이 아니라, 새로 추가된 batch 메서드 findLatestForEquipmentCodes 가
     * "딱 1번" 호출되는지 확인한다.
     */
    @Test
    void getLines_doesNotIssueN1QueriesForAnalysisLatest() throws Exception {
        ProductionLine line = lineOf("LINE-A", "FACTORY-01", "라인 A", null);

        Equipment e1 = new Equipment();
        e1.setEquipmentCode("EQ-1");
        e1.setLocation("LINE-A");

        Equipment e2 = new Equipment();
        e2.setEquipmentCode("EQ-2");
        e2.setLocation("LINE-A");

        EquipmentStatus s1 = new EquipmentStatus();
        s1.setEquipId("EQ-1");
        s1.setStatusCode("RUNNING");

        ProductionLineRepository lineRepo = mock(ProductionLineRepository.class);
        EquipmentRepository equipmentRepo = mock(EquipmentRepository.class);
        EquipmentStatusRepository statusRepo = mock(EquipmentStatusRepository.class);
        AnalysisResultRepository analysisRepo = mock(AnalysisResultRepository.class);
        VibrationWindowMonitorService monitor = mock(VibrationWindowMonitorService.class);
        DemoMetricsService demoMetrics = mock(DemoMetricsService.class);

        when(lineRepo.findByFactoryId("FACTORY-01")).thenReturn(List.of(line));
        when(equipmentRepo.findAll()).thenReturn(List.of(e1, e2));
        when(statusRepo.findAll()).thenReturn(List.of(s1));
        when(analysisRepo.findLatestForEquipmentCodes(any())).thenReturn(List.<AnalysisResult>of());
        when(monitor.latestRealtime(anyString())).thenReturn(VibrationRealtimeResponse.empty("any"));
        when(demoMetrics.lineMetrics(anyString()))
                .thenReturn(new DemoMetricsService.LineMetricsDto(0.0, 0.0, 0.0, 0.0, List.of()));

        LineAggregationService service = new LineAggregationService(
                lineRepo, equipmentRepo, statusRepo, analysisRepo, monitor, demoMetrics
        );

        var result = service.getLines("FACTORY-01");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lineId()).isEqualTo("LINE-A");

        verify(analysisRepo, times(1)).findLatestForEquipmentCodes(any());
        verify(analysisRepo, never()).findTopByEquipmentCodeOrderByCreatedAtDesc(anyString());
    }

    private ProductionLine lineOf(String id, String factory, String name, String status) throws Exception {
        ProductionLine line = ProductionLine.class.getDeclaredConstructor().newInstance();
        for (var field : ProductionLine.class.getDeclaredFields()) {
            field.setAccessible(true);
            switch (field.getName()) {
                case "lineId" -> field.set(line, id);
                case "factoryId" -> field.set(line, factory);
                case "lineName" -> field.set(line, name);
                case "lineStatus" -> field.set(line, status);
                default -> { /* no-op */ }
            }
        }
        return line;
    }
}
