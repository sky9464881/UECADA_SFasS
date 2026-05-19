package com.example.phm.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "line_station_balance")
@IdClass(LineStationBalanceId.class)
public class LineStationBalance {

    @Id
    @Column(name = "line_id")
    private String lineId;

    @Id
    @Column(name = "station_no")
    private Integer stationNo;

    @Column(name = "utilization_pct")
    private BigDecimal utilizationPct;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    public String getLineId() {
        return lineId;
    }

    public Integer getStationNo() {
        return stationNo;
    }

    public BigDecimal getUtilizationPct() {
        return utilizationPct;
    }
}
