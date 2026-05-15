package com.example.phm.dashboard.controller;

import com.example.phm.dashboard.dto.FrontendDashboardResponse;
import com.example.phm.dashboard.service.FrontendDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class FrontendDashboardController {

    private final FrontendDashboardService service;

    public FrontendDashboardController(FrontendDashboardService service) {
        this.service = service;
    }

    /**
     * 프론트엔드 대시보드 전용 집계 API
     * 기존 /api/dashboard/summary 와 별도로 동작
     */
    @GetMapping("/frontend")
    public FrontendDashboardResponse frontend() {
        return service.build();
    }
}
