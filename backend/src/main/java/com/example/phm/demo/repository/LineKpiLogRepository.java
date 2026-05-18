package com.example.phm.demo.repository;

import java.util.Optional;

import com.example.phm.demo.entity.LineKpiLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineKpiLogRepository extends JpaRepository<LineKpiLog, Long> {

    Optional<LineKpiLog> findTopByLineIdOrderByRecordedAtDesc(String lineId);
}
