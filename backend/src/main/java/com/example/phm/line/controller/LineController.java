package com.example.phm.line.controller;

import java.util.List;

import com.example.phm.line.dto.LineResponse;
import com.example.phm.line.service.LineAggregationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LineController {

    private final LineAggregationService lineAggregationService;

    public LineController(LineAggregationService lineAggregationService) {
        this.lineAggregationService = lineAggregationService;
    }

    @GetMapping("/api/lines")
    public List<LineResponse> findLines(@RequestParam(required = false) String factoryId) {
        return lineAggregationService.getLines(factoryId);
    }
}
