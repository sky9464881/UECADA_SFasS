package com.example.phm.vibration.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vibration_window")
public class VibrationWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_code", nullable = false, length = 50)
    private String equipmentCode;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "sampling_rate", nullable = false)
    private Integer samplingRate;

    @Column(name = "rpm")
    private Integer rpm;

    @Column(name = "window_size", nullable = false)
    private Integer windowSize;

    @Column(name = "window_index", nullable = false)
    private Long windowIndex;

    @Column(name = "values_json", columnDefinition = "LONGTEXT")
    private String valuesJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public LocalDateTime getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(LocalDateTime measuredAt) {
        this.measuredAt = measuredAt;
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

    public Long getWindowIndex() {
        return windowIndex;
    }

    public void setWindowIndex(Long windowIndex) {
        this.windowIndex = windowIndex;
    }

    public String getValuesJson() {
        return valuesJson;
    }

    public void setValuesJson(String valuesJson) {
        this.valuesJson = valuesJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
