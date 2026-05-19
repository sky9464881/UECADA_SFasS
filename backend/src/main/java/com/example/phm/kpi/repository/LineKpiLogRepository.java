package com.example.phm.kpi.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.kpi.entity.LineKpiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineKpiLogRepository extends JpaRepository<LineKpiLog, Long> {

    @Query("""
            select l from LineKpiLog l
            where l.lineId = :lineId
              and l.recordedAt >= :from
              and l.recordedAt < :to
            order by l.recordedAt asc
            """)
    List<LineKpiLog> findByLineIdAndRecordedAtBetween(
            @Param("lineId") String lineId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
