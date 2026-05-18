package com.example.phm.line.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "`line`")
public class ProductionLine {

    @Id
    @Column(name = "line_id", length = 20)
    private String lineId;

    @Column(name = "factory_id", nullable = false, length = 20)
    private String factoryId;

    @Column(name = "line_name", nullable = false, length = 100)
    private String lineName;

    @Column(name = "line_status", length = 30)
    private String lineStatus;

    public String getLineId() {
        return lineId;
    }

    public String getFactoryId() {
        return factoryId;
    }

    public String getLineName() {
        return lineName;
    }

    public String getLineStatus() {
        return lineStatus;
    }
}
