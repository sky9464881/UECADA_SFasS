package com.example.phm.demo.repository;

import java.util.List;

import com.example.phm.demo.entity.LineStationBalance;
import com.example.phm.demo.entity.LineStationBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineStationBalanceRepository extends JpaRepository<LineStationBalance, LineStationBalanceId> {

    List<LineStationBalance> findByLineIdOrderByStationNoAsc(String lineId);
}
