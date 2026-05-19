package com.example.phm.demo.entity;

import java.io.Serializable;
import java.util.Objects;

public class LineStationBalanceId implements Serializable {

    private String lineId;
    private Integer stationNo;

    public LineStationBalanceId() {
    }

    public LineStationBalanceId(String lineId, Integer stationNo) {
        this.lineId = lineId;
        this.stationNo = stationNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LineStationBalanceId that)) {
            return false;
        }
        return Objects.equals(lineId, that.lineId) && Objects.equals(stationNo, that.stationNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineId, stationNo);
    }
}
