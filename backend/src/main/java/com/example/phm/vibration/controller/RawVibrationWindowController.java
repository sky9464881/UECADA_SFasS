package com.example.phm.vibration.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.phm.vibration.dto.RawVibrationSeriesResponse;
import com.example.phm.vibration.dto.RawVibrationWindowResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.entity.VibrationWindow;
import com.example.phm.vibration.repository.VibrationWindowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RawVibrationWindowController {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final VibrationWindowRepository vibrationWindowRepository;
    private final ObjectMapper objectMapper;

    public RawVibrationWindowController(
            VibrationWindowRepository vibrationWindowRepository,
            ObjectMapper objectMapper
    ) {
        this.vibrationWindowRepository = vibrationWindowRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/equipments/{equipmentCode}/vibration-windows/latest/raw")
    public ResponseEntity<RawVibrationWindowResponse> latestRaw(
            @PathVariable String equipmentCode,
            @RequestParam(defaultValue = "true") boolean includeValues
    ) {
        return vibrationWindowRepository.findTopByEquipmentCodeOrderByMeasuredAtDescIdDesc(equipmentCode)
                .map(window -> readRawWindow(window, includeValues))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/equipments/{equipmentCode}/vibration-windows/raw-series")
    public ResponseEntity<RawVibrationSeriesResponse> rawSeries(
            @PathVariable String equipmentCode,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "8000") int maxPoints
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        int safeMaxPoints = Math.max(1000, Math.min(maxPoints, 20000));
        List<VibrationWindow> windows = vibrationWindowRepository
                .findTop100ByEquipmentCodeOrderByMeasuredAtDescIdDesc(equipmentCode)
                .stream()
                .limit(safeLimit)
                .toList();

        if (windows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<VibrationWindow> orderedWindows = new ArrayList<>(windows);
        Collections.reverse(orderedWindows);

        List<RawVibrationSeriesResponse.RawVibrationPoint> points = new ArrayList<>();
        Integer samplingRate = orderedWindows.get(0).getSamplingRate();

        for (VibrationWindow window : orderedWindows) {
            VibrationWindowMessage message = readRawWindowMessage(window);
            List<Double> values = message.getValues() == null ? List.of() : message.getValues();
            double baseTimestamp = measuredTimestampMillis(window);
            double intervalMillis = window.getSamplingRate() == null ? 1.0 : 1000.0 / window.getSamplingRate();

            for (int index = 0; index < values.size(); index++) {
                points.add(new RawVibrationSeriesResponse.RawVibrationPoint(
                        baseTimestamp + index * intervalMillis,
                        values.get(index),
                        window.getWindowIndex()
                ));
            }
        }

        int originalSampleCount = points.size();
        List<RawVibrationSeriesResponse.RawVibrationPoint> displayPoints = downsampleMinMax(points, safeMaxPoints);
        VibrationWindow first = orderedWindows.get(0);
        VibrationWindow last = orderedWindows.get(orderedWindows.size() - 1);
        return ResponseEntity.ok(new RawVibrationSeriesResponse(
                equipmentCode,
                orderedWindows.size(),
                displayPoints.size(),
                originalSampleCount,
                displayPoints.size() < originalSampleCount,
                samplingRate,
                first.getWindowIndex(),
                last.getWindowIndex(),
                displayPoints
        ));
    }

    private RawVibrationWindowResponse readRawWindow(VibrationWindow vibrationWindow, boolean includeValues) {
        VibrationWindowMessage message = readRawWindowMessage(vibrationWindow);
        return RawVibrationWindowResponse.from(vibrationWindow, message, includeValues);
    }

    private VibrationWindowMessage readRawWindowMessage(VibrationWindow window) {
        try {
            String json = window.getValuesJson();
            List<Double> values = json != null
                    ? objectMapper.readValue(json, new TypeReference<List<Double>>() {})
                    : List.of();
            return new VibrationWindowMessage(
                    window.getEquipmentCode(),
                    window.getMeasuredAt() != null ? window.getMeasuredAt().toString() : null,
                    window.getSamplingRate(),
                    window.getRpm(),
                    window.getWindowSize(),
                    window.getWindowIndex() != null ? window.getWindowIndex().intValue() : 0,
                    values
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse vibration values_json", exception);
        }
    }

    private double measuredTimestampMillis(VibrationWindow window) {
        LocalDateTime base = window.getMeasuredAt() == null ? window.getCreatedAt() : window.getMeasuredAt();
        return base.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    private List<RawVibrationSeriesResponse.RawVibrationPoint> downsampleMinMax(
            List<RawVibrationSeriesResponse.RawVibrationPoint> points,
            int maxPoints
    ) {
        if (points.size() <= maxPoints) {
            return points;
        }

        int bucketCount = Math.max(1, maxPoints / 2);
        double bucketSize = points.size() / (double) bucketCount;
        List<RawVibrationSeriesResponse.RawVibrationPoint> downsampled = new ArrayList<>(maxPoints);

        for (int bucket = 0; bucket < bucketCount; bucket++) {
            int start = (int) Math.floor(bucket * bucketSize);
            int end = Math.min(points.size(), (int) Math.floor((bucket + 1) * bucketSize));
            if (end <= start) {
                continue;
            }

            RawVibrationSeriesResponse.RawVibrationPoint minPoint = points.get(start);
            RawVibrationSeriesResponse.RawVibrationPoint maxPoint = points.get(start);
            for (int index = start + 1; index < end; index++) {
                RawVibrationSeriesResponse.RawVibrationPoint point = points.get(index);
                if (point.value() < minPoint.value()) {
                    minPoint = point;
                }
                if (point.value() > maxPoint.value()) {
                    maxPoint = point;
                }
            }

            if (minPoint.timestamp() <= maxPoint.timestamp()) {
                downsampled.add(minPoint);
                if (maxPoint.timestamp() != minPoint.timestamp()) {
                    downsampled.add(maxPoint);
                }
            } else {
                downsampled.add(maxPoint);
                downsampled.add(minPoint);
            }
        }

        return downsampled;
    }
}
