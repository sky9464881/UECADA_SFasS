package com.example.phm.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment_runtime_demo")
public class EquipmentRuntimeDemo {

    @Id
    @Column(name = "equipment_code")
    private String equipmentCode;

    @Column(name = "utilization_rate")
    private BigDecimal utilizationRate;

    @Column(name = "defect_count")
    private Integer defectCount;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "cycle_time_sec")
    private BigDecimal cycleTimeSec;

    @Column(name = "current_amp")
    private BigDecimal currentAmp;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "humidity_pct")
    private BigDecimal humidityPct;

    @Column(name = "vibration_mm_s")
    private BigDecimal vibrationMmS;

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public BigDecimal getUtilizationRate() {
        return utilizationRate;
    }

    public Integer getDefectCount() {
        return defectCount;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public BigDecimal getCycleTimeSec() {
        return cycleTimeSec;
    }

    public BigDecimal getCurrentAmp() {
        return currentAmp;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public BigDecimal getHumidityPct() {
        return humidityPct;
    }

    public BigDecimal getVibrationMmS() {
        return vibrationMmS;
    }
}
