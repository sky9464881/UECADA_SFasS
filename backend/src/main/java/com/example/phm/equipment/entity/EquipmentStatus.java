package com.example.phm.equipment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment_status")
public class EquipmentStatus {

    @Id
    @Column(name = "equip_id", length = 50)
    private String equipId;

    @Column(name = "status_code", nullable = false, length = 30)
    private String statusCode;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public String getEquipId() { return equipId; }
    public void setEquipId(String equipId) { this.equipId = equipId; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
