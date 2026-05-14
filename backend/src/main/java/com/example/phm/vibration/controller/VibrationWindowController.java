package com.example.phm.vibration.controller;

import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
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
}
