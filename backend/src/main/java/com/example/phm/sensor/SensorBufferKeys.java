package com.example.phm.sensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensorBufferKeys {

    public static final List<String> MONITORING_METRICS = List.of(
            "sensor_vibration",
            "sensor_current",
            "sensor_voltage",
            "sensor_temperature",
            "cycle_time",
            "injection_pressure",
            "mold_temperature",
            "cooling_flow",
            "spindle_speed",
            "tool_usage",
            "coolant_flow",
            "cleaning_concentration",
            "cleaning_temperature",
            "cleaning_pressure",
            "tightening_torque",
            "tightening_angle",
            "press_force",
            "bore_dimension",
            "hole_dimension",
            "result_ok",
            "temperature",
            "pressure",
            "spindle_load",
            "spindle_rpm",
            "feed_rate",
            "water_temp",
            "flow_rate",
            "torque",
            "leak_pressure"
    );

    private static final Pattern EQUIPMENT_CODE_PATTERN =
            Pattern.compile("^(LINE[-_]?\\d{2})[_.](.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE01_SCOPED_BUFFER_PATTERN =
            Pattern.compile("^LINE01\\.([^:]+):(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIAS_BUFFER_PATTERN =
            Pattern.compile("^([^.:]+):(.+)$", Pattern.CASE_INSENSITIVE);

    private SensorBufferKeys() {
    }

    public static Optional<String> lineScopedKey(String equipmentCode, String metric) {
        if (equipmentCode == null || equipmentCode.isBlank() || metric == null || metric.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = EQUIPMENT_CODE_PATTERN.matcher(equipmentCode.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String line = matcher.group(1).replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
        String equipment = matcher.group(2).replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
        String suffix = metric.startsWith(":") ? metric.substring(1) : metric;
        return Optional.of("%s.%s:%s".formatted(line, equipment, suffix));
    }

    public static List<String> lookupKeys(String equipmentCode, String metric) {
        Optional<String> lineScoped = lineScopedKey(equipmentCode, metric);
        if (lineScoped.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        keys.add(lineScoped.get());
        line01Alias(lineScoped.get()).ifPresent(keys::add);
        return List.copyOf(keys);
    }

    public static List<String> alternateKeys(String bufferKey) {
        if (bufferKey == null || bufferKey.isBlank()) {
            return List.of();
        }
        String normalized = bufferKey.trim();
        List<String> keys = new ArrayList<>();
        line01Alias(normalized).ifPresent(keys::add);
        line01ScopedFromAlias(normalized).ifPresent(keys::add);
        return List.copyOf(keys);
    }

    public static Optional<String> line01Alias(String bufferKey) {
        Matcher matcher = LINE01_SCOPED_BUFFER_PATTERN.matcher(bufferKey);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of("%s:%s".formatted(matcher.group(1), matcher.group(2)));
    }

    private static Optional<String> line01ScopedFromAlias(String bufferKey) {
        Matcher matcher = ALIAS_BUFFER_PATTERN.matcher(bufferKey);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of("LINE01.%s:%s".formatted(matcher.group(1), matcher.group(2)));
    }
}
