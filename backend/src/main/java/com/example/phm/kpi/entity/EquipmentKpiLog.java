package com.example.phm.kpi.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment_kpi_log")
public class EquipmentKpiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_kpi_id")
    private Long id;

    @Column(name = "equipment_code", nullable = false, length = 50)
    private String equipmentCode;

    @Column(name = "equipment_oee")
    private Double equipmentOee;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public EquipmentKpiLog() {}

    public EquipmentKpiLog(String equipmentCode, Double equipmentOee, LocalDateTime recordedAt) {
        this.equipmentCode = equipmentCode;
        this.equipmentOee = equipmentOee;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public String getEquipmentCode() { return equipmentCode; }
    public Double getEquipmentOee() { return equipmentOee; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
