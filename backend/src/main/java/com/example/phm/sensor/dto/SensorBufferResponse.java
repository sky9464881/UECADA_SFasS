package com.example.phm.sensor.dto;

import java.util.List;

import com.example.phm.sensor.SensorFrame;

public record SensorBufferResponse(
        String bufferKey,
        int size,
        int capacity,
        SensorFrame latest,
        List<SensorFrame> frames
) {}
