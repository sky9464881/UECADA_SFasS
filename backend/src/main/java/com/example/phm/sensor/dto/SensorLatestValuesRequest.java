package com.example.phm.sensor.dto;

import java.util.List;

public record SensorLatestValuesRequest(List<String> bufferKeys) {
}
