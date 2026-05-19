package com.example.phm.alarm.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.alarm.entity.Alarm;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    @Query("""
            SELECT a FROM Alarm a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:equipmentCode IS NULL OR a.equipmentCode = :equipmentCode)
              AND (:from IS NULL OR a.occurredAt >= :from)
              AND (:to IS NULL OR a.occurredAt <= :to)
            ORDER BY a.occurredAt DESC
            """)
    List<Alarm> findByFilters(
            @Param("status") String status,
            @Param("equipmentCode") String equipmentCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    long countByStatus(String status);

    long countBySeverityIn(java.util.List<String> severities);

    @Query(value = """
            SELECT DATE(occurred_at) AS stat_date,
                   COALESCE(alarm_type_name, 'UNKNOWN') AS alarm_type,
                   COUNT(*) AS cnt
            FROM alarm
            WHERE occurred_at BETWEEN :from AND :to
            GROUP BY DATE(occurred_at), alarm_type_name
            ORDER BY DATE(occurred_at)
            """, nativeQuery = true)
    List<Object[]> countByDateAndType(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
