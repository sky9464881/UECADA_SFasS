package com.example.phm.kpi.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "line_kpi_log")
public class LineKpiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_kpi_id")
    private Long id;

    @Column(name = "line_id", nullable = false, length = 20)
    private String lineId;

    @Column(name = "line_oee")
    private Double lineOee;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public LineKpiLog() {}

    public LineKpiLog(String lineId, Double lineOee, LocalDateTime recordedAt) {
        this.lineId = lineId;
        this.lineOee = lineOee;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public String getLineId() { return lineId; }
    public Double getLineOee() { return lineOee; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
