package com.example.phm.common;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.line.service.LineAggregationService;
import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.SensorFrame;
import com.example.phm.vibration.service.VibrationWindowMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/swmp")
public class SwmpIntegrationController {

    private final EquipmentRepository equipmentRepository;
    private final LineAggregationService lineAggregationService;
    private final SensorBufferRegistry sensorBufferRegistry;
    private final VibrationWindowMonitorService vibrationWindowMonitorService;

    public SwmpIntegrationController(
            EquipmentRepository equipmentRepository,
            LineAggregationService lineAggregationService,
            SensorBufferRegistry sensorBufferRegistry,
            VibrationWindowMonitorService vibrationWindowMonitorService
    ) {
        this.equipmentRepository = equipmentRepository;
        this.lineAggregationService = lineAggregationService;
        this.sensorBufferRegistry = sensorBufferRegistry;
        this.vibrationWindowMonitorService = vibrationWindowMonitorService;
    }

    @GetMapping("/equipment/{equipmentCode}")
    public Map<String, Object> equipment(@PathVariable String equipmentCode) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("equipmentCode", equipmentCode);
        response.put("equipment", equipmentRepository.findByEquipmentCode(equipmentCode).orElse(null));
        response.put("sensors", latestSensors(equipmentCode));
        response.put("vibration", vibrationWindowMonitorService.latestRealtime(equipmentCode));
        response.put("popupUrl", "/#/equipment?equipmentId=" + equipmentCode + "&popup=1");
        return response;
    }

    @GetMapping("/lines/{lineId}")
    public Map<String, Object> line(@PathVariable String lineId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("lineId", lineId);
        response.put("line", lineAggregationService.getLines("FACTORY-01").stream()
                .filter(item -> lineId.equals(item.lineId()))
                .findFirst()
                .orElse(null));
        response.put("popupUrl", "/#/layout?lineId=" + lineId + "&popup=1");
        return response;
    }

    private Map<String, Object> latestSensors(String equipmentCode) {
        Map<String, Object> sensors = new LinkedHashMap<>();
        for (String metric : SensorBufferKeys.MONITORING_METRICS) {
            SensorFrame frame = latestFrame(equipmentCode, metric);
            if (frame == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("timestampMs", frame.timestampMs());
            item.put("value", frame.value());
            sensors.put(metric, item);
        }
        return sensors;
    }

    private SensorFrame latestFrame(String equipmentCode, String metric) {
        SensorFrame latest = null;
        for (String key : SensorBufferKeys.lookupKeys(equipmentCode, metric)) {
            SensorBuffer buffer = sensorBufferRegistry.get(key);
            if (buffer != null && buffer.latest() != null) {
                SensorFrame frame = buffer.latest();
                if (latest == null || frame.timestampMs() > latest.timestampMs()) {
                    latest = frame;
                }
            }
        }
        return latest;
    }
}
