package com.example.phm.demo.controller;

import java.util.List;

import com.example.phm.demo.dto.DemoMetricsPushRequest;
import com.example.phm.demo.dto.DemoMetricsPushResponse;
import com.example.phm.demo.service.DemoMetricsService;
import com.example.phm.demo.store.DemoMetricsLiveStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 데모 수치 주입 API.
 * 1초마다 POST 하면 GET /api/lines, /api/equipments 응답에 반영됩니다.
 */
@RestController
@RequestMapping("/api/demo/metrics")
public class DemoMetricsController {

    private final DemoMetricsLiveStore liveStore;

    public DemoMetricsController(DemoMetricsLiveStore liveStore) {
        this.liveStore = liveStore;
    }

    @PostMapping("/push")
    public DemoMetricsPushResponse push(@RequestBody DemoMetricsPushRequest request) {
        int lines = 0;
        int equipments = 0;

        if (request.lines() != null) {
            for (DemoMetricsPushRequest.LinePush line : request.lines()) {
                if (line.lineId() == null || line.lineId().isBlank()) {
                    continue;
                }
                liveStore.putLine(
                        line.lineId(),
                        new DemoMetricsService.LineMetricsDto(
                                line.balanceRate(),
                                line.uph(),
                                line.upmh(),
                                line.productivity(),
                                line.stationUtilization() == null ? List.of() : line.stationUtilization()
                        )
                );
                lines++;
            }
        }

        if (request.equipments() != null) {
            for (DemoMetricsPushRequest.EquipmentPush eq : request.equipments()) {
                if (eq.equipmentCode() == null || eq.equipmentCode().isBlank()) {
                    continue;
                }
                liveStore.putEquipment(
                        eq.equipmentCode(),
                        new DemoMetricsService.EquipmentRuntimeDto(
                                eq.utilizationRate(),
                                eq.defectCount(),
                                eq.operatorName(),
                                eq.cycleTimeSec(),
                                eq.currentAmp(),
                                eq.temperatureC(),
                                eq.humidityPct(),
                                eq.vibrationMmS()
                        )
                );
                equipments++;
            }
        }

        return new DemoMetricsPushResponse(lines, equipments, System.currentTimeMillis());
    }

    @GetMapping("/live")
    public Object liveStatus() {
        return java.util.Map.of(
                "lineIds", liveStore.lineIds(),
                "lineCount", liveStore.lineCount(),
                "equipmentCount", liveStore.equipmentCount()
        );
    }

    @DeleteMapping("/live")
    public void clearLive() {
        liveStore.clear();
    }
}
