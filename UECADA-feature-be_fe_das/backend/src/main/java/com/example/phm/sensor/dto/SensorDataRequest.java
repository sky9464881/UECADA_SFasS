package com.example.phm.sensor.dto;

import java.util.List;

import com.example.phm.sensor.SensorFrame;
import jakarta.validation.constraints.NotEmpty;

public record SensorDataRequest(@NotEmpty List<SensorFrame> frames) {}
