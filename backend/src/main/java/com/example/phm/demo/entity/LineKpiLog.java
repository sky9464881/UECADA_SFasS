package com.example.phm.demo.entity;

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

    @Column(name = "line_id")
    private String lineId;

    @Column(name = "line_oee")
    private BigDecimal lineOee;

    @Column(name = "line_uph")
    private BigDecimal lineUph;

    @Column(name = "line_upmh")
    private BigDecimal lineUpmh;

    @Column(name = "line_balance_rate")
    private BigDecimal lineBalanceRate;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    public String getLineId() {
        return lineId;
    }

    public BigDecimal getLineOee() {
        return lineOee;
    }

    public BigDecimal getLineUph() {
        return lineUph;
    }

    public BigDecimal getLineUpmh() {
        return lineUpmh;
    }

    public BigDecimal getLineBalanceRate() {
        return lineBalanceRate;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
