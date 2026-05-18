package com.example.phm.common;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
@Profile({"local", "docker"})
public class DebugResetController {

    private final DebugResetService debugResetService;

    public DebugResetController(DebugResetService debugResetService) {
        this.debugResetService = debugResetService;
    }

    @PostMapping("/reset-data")
    public DebugResetResponse resetData() {
        return debugResetService.resetIngestionData();
    }
}
