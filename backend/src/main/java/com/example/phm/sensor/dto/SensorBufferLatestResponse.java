package com.example.phm.sensor.dto;

import com.example.phm.sensor.SensorFrame;

public record SensorBufferLatestResponse(
        String bufferKey,
        int size,
        int capacity,
        SensorFrame latest
) {}
