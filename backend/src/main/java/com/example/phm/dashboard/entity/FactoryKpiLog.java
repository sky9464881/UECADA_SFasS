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
@Table(name = "factory_kpi_log")
public class FactoryKpiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "factory_kpi_id")
    private Long factoryKpiId;

    @Column(name = "factory_id", nullable = false, length = 20)
    private String factoryId;

    @Column(name = "factory_oee", precision = 5, scale = 2)
    private BigDecimal factoryOee;

    @Column(name = "factory_uph", precision = 10, scale = 2)
    private BigDecimal factoryUph;

    @Column(name = "factory_upmh", precision = 10, scale = 2)
    private BigDecimal factoryUpmh;

    @Column(name = "factory_availability", precision = 5, scale = 2)
    private BigDecimal factoryAvailability;

    @Column(name = "factory_performance", precision = 5, scale = 2)
    private BigDecimal factoryPerformance;

    @Column(name = "factory_quality", precision = 5, scale = 2)
    private BigDecimal factoryQuality;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public Long getFactoryKpiId() { return factoryKpiId; }
    public String getFactoryId() { return factoryId; }
    public BigDecimal getFactoryOee() { return factoryOee; }
    public BigDecimal getFactoryUph() { return factoryUph; }
    public BigDecimal getFactoryUpmh() { return factoryUpmh; }
    public BigDecimal getFactoryAvailability() { return factoryAvailability; }
    public BigDecimal getFactoryPerformance() { return factoryPerformance; }
    public BigDecimal getFactoryQuality() { return factoryQuality; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
