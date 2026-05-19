package com.example.phm.vibration.controller;

<<<<<<< HEAD
import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
=======
import java.util.List;

import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
>>>>>>> feature/develop_before
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vibration")
public class VibrationWindowController {

    private final VibrationWindowMonitorService monitorService;

    public VibrationWindowController(VibrationWindowMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/latest")
    public VibrationWindowLatestResponse latest() {
        return monitorService.latest();
    }
<<<<<<< HEAD
=======

    @GetMapping("/realtime")
    public List<VibrationRealtimeResponse> realtimeAll() {
        return monitorService.latestRealtimeAll();
    }

    @GetMapping("/realtime/{equipmentCode}")
    public VibrationRealtimeResponse realtime(@PathVariable String equipmentCode) {
        return monitorService.latestRealtime(equipmentCode);
    }
>>>>>>> feature/develop_before
}
