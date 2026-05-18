package com.example.phm.alarm.dto;

import java.time.LocalDateTime;

public record AlarmResolveRequest(String resolvedBy, LocalDateTime resolvedAt, String comment) {}
