package com.example.phm.dashboard.entity;

import java.math.BigDecimal;
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
    private Long lineKpiId;

    @Column(name = "line_id", nullable = false, length = 20)
    private String lineId;

    @Column(name = "line_oee", precision = 5, scale = 2)
    private BigDecimal lineOee;

    @Column(name = "line_uph", precision = 10, scale = 2)
    private BigDecimal lineUph;

    @Column(name = "line_upmh", precision = 10, scale = 2)
    private BigDecimal lineUpmh;

    @Column(name = "line_availability", precision = 5, scale = 2)
    private BigDecimal lineAvailability;

    @Column(name = "line_performance", precision = 5, scale = 2)
    private BigDecimal linePerformance;

    @Column(name = "line_quality", precision = 5, scale = 2)
    private BigDecimal lineQuality;

    @Column(name = "line_balance_rate", precision = 5, scale = 2)
    private BigDecimal lineBalanceRate;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public Long getLineKpiId() { return lineKpiId; }
    public String getLineId() { return lineId; }
    public BigDecimal getLineOee() { return lineOee; }
    public BigDecimal getLineUph() { return lineUph; }
    public BigDecimal getLineUpmh() { return lineUpmh; }
    public BigDecimal getLineAvailability() { return lineAvailability; }
    public BigDecimal getLinePerformance() { return linePerformance; }
    public BigDecimal getLineQuality() { return lineQuality; }
    public BigDecimal getLineBalanceRate() { return lineBalanceRate; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
