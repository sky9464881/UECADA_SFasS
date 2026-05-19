package com.example.phm.common;

import java.time.Instant;
import java.util.List;

import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PipelineStatusController {

    private final SensorBufferRegistry sensorBufferRegistry;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;

    public PipelineStatusController(
            SensorBufferRegistry sensorBufferRegistry,
            VibrationWindowMonitorService vibrationWindowMonitorService
    ) {
        this.sensorBufferRegistry = sensorBufferRegistry;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
    }

    @GetMapping("/api/pipeline/status")
    public PipelineStatusResponse status() {
        List<VibrationRealtimeResponse> vibrationWindows = vibrationWindowMonitorService.latestRealtimeAll();
        return new PipelineStatusResponse(
                Instant.now(),
                sensorBufferRegistry.registeredKeys().stream().sorted().toList(),
                vibrationWindows.size(),
                vibrationWindows
        );
    }

    public record PipelineStatusResponse(
            Instant checkedAt,
            List<String> sensorBufferKeys,
            int realtimeVibrationEquipmentCount,
            List<VibrationRealtimeResponse> realtimeVibrationWindows
    ) {
    }
}
