package com.example.phm.equipment.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_code", nullable = false, unique = true, length = 50)
    private String equipmentCode;

    @Column(name = "equipment_name", nullable = false, length = 100)
    private String equipmentName;

    @Column(name = "process_type", length = 50)
    private String processType;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "install_date")
    private LocalDate installDate;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "location_x", precision = 10, scale = 4)
    private BigDecimal locationX;

    @Column(name = "location_y", precision = 10, scale = 4)
    private BigDecimal locationY;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }

    public String getProcessType() { return processType; }
    public void setProcessType(String processType) { this.processType = processType; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public LocalDate getInstallDate() { return installDate; }
    public void setInstallDate(LocalDate installDate) { this.installDate = installDate; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public BigDecimal getLocationX() { return locationX; }
    public void setLocationX(BigDecimal locationX) { this.locationX = locationX; }

    public BigDecimal getLocationY() { return locationY; }
    public void setLocationY(BigDecimal locationY) { this.locationY = locationY; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
