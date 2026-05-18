package com.example.phm.alarm.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alarm")
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alarm_id")
    private Long alarmId;

    @Column(name = "equipment_code", nullable = false, length = 50)
    private String equipmentCode;

    @Column(name = "alarm_code", length = 50)
    private String alarmCode;

    @Column(name = "alarm_type_name", length = 100)
    private String alarmType;

    @Column(name = "alarm_category", length = 50)
    private String alarmCategory;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "alarm_message", length = 255)
    private String alarmMessage;

    @Column(name = "alarm_status", nullable = false, length = 30)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "sensor_snapshot", columnDefinition = "json")
    private String sensorSnapshot;

    @Column(name = "resolved_by", length = 20)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "comment", length = 500)
    private String comment;

    public Long getAlarmId() { return alarmId; }

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }

    public String getAlarmCode() { return alarmCode; }
    public void setAlarmCode(String alarmCode) { this.alarmCode = alarmCode; }

    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }

    public String getAlarmCategory() { return alarmCategory; }
    public void setAlarmCategory(String alarmCategory) { this.alarmCategory = alarmCategory; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getAlarmMessage() { return alarmMessage; }
    public void setAlarmMessage(String alarmMessage) { this.alarmMessage = alarmMessage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public String getSensorSnapshot() { return sensorSnapshot; }
    public void setSensorSnapshot(String sensorSnapshot) { this.sensorSnapshot = sensorSnapshot; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
