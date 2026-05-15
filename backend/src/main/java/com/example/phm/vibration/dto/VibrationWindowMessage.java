package com.example.phm.vibration.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VibrationWindowMessage {

    private String equipmentId;
    private String timestamp;
    private Integer samplingRate;
    private Integer rpm;
    private Integer windowSize;
    private Integer windowIndex;
    private List<Double> values;

    public VibrationWindowMessage() {
    }

    public VibrationWindowMessage(
            String equipmentId,
            String timestamp,
            Integer samplingRate,
            Integer rpm,
            Integer windowSize,
            Integer windowIndex,
            List<Double> values
    ) {
        this.equipmentId = equipmentId;
        this.timestamp = timestamp;
        this.samplingRate = samplingRate;
        this.rpm = rpm;
        this.windowSize = windowSize;
        this.windowIndex = windowIndex;
        this.values = values;
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

    public int valuesLength() {
        return values == null ? 0 : values.size();
    }
}
