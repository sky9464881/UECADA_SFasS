package com.example.phm.analysis.dto;

import java.util.List;
import java.util.Map;

import com.example.phm.vibration.dto.VibrationWindowMessage;

public class AnalyzeRequest {

    private String equipmentId;
    private String timestamp;
    private Integer samplingRate;
    private Integer rpm;
    private Integer windowSize;
    private Integer windowIndex;
    private List<Double> values;
    private Map<String, Object> sensorSnapshot;
    private Boolean persist;

    public static AnalyzeRequest from(VibrationWindowMessage message) {
        AnalyzeRequest request = new AnalyzeRequest();
        request.setEquipmentId(message.getEquipmentId());
        request.setTimestamp(message.getTimestamp());
        request.setSamplingRate(message.getSamplingRate());
        request.setRpm(message.getRpm());
        request.setWindowSize(message.getWindowSize());
        request.setWindowIndex(message.getWindowIndex());
        request.setValues(message.getValues());
        request.setPersist(true);
        return request;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(String equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(Integer samplingRate) {
        this.samplingRate = samplingRate;
    }

    public Integer getRpm() {
        return rpm;
    }

    public void setRpm(Integer rpm) {
        this.rpm = rpm;
    }

    public Integer getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(Integer windowSize) {
        this.windowSize = windowSize;
    }

    public Integer getWindowIndex() {
        return windowIndex;
    }

    public void setWindowIndex(Integer windowIndex) {
        this.windowIndex = windowIndex;
    }

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values;
    }

    public Map<String, Object> getSensorSnapshot() {
        return sensorSnapshot;
    }

    public void setSensorSnapshot(Map<String, Object> sensorSnapshot) {
        this.sensorSnapshot = sensorSnapshot;
    }

    public Boolean getPersist() {
        return persist;
    }

    public void setPersist(Boolean persist) {
        this.persist = persist;
    }
}
