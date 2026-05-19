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

    /** OEE = 가용성 × 성능 × 품질 / 10000 */
    @Column(name = "line_oee")
    private Double lineOee;

    @Column(name = "line_availability")
    private Double lineAvailability;

    @Column(name = "line_performance")
    private Double linePerformance;

    @Column(name = "line_quality")
    private Double lineQuality;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public LineKpiLog() {}

    public LineKpiLog(String lineId, Double availability, Double performance, Double quality, LocalDateTime recordedAt) {
        this.lineId = lineId;
        this.lineAvailability = availability;
        this.linePerformance = performance;
        this.lineQuality = quality;
        this.recordedAt = recordedAt;
        if (availability != null && performance != null && quality != null) {
            this.lineOee = Math.round((availability * performance * quality / 10000.0) * 10.0) / 10.0;
        }
    }

    public Long getId() { return id; }
    public String getLineId() { return lineId; }
    public Double getLineOee() { return lineOee; }
    public Double getLineAvailability() { return lineAvailability; }
    public Double getLinePerformance() { return linePerformance; }
    public Double getLineQuality() { return lineQuality; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
