package com.example.phm.dashboard.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardNativeRepository {

    @PersistenceContext
    private EntityManager em;

    public record AlarmSummaryData(long total, long critical, long warning, long resolved, long open) {}
    public record StatusCount(String statusCode, long count) {}
    public record OeePoint(String recordedAt, BigDecimal oee) {}

    /** alarm 테이블 집계 */
    public AlarmSummaryData getAlarmSummary() {
        Object[] row = (Object[]) em.createNativeQuery("""
                SELECT
                  COUNT(*),
                  SUM(CASE WHEN severity   = 'CRITICAL'  THEN 1 ELSE 0 END),
                  SUM(CASE WHEN severity   = 'WARNING'   THEN 1 ELSE 0 END),
                  SUM(CASE WHEN alarm_status = 'RESOLVED' THEN 1 ELSE 0 END),
                  SUM(CASE WHEN alarm_status = 'OPEN'     THEN 1 ELSE 0 END)
                FROM alarm
                """).getSingleResult();
        return new AlarmSummaryData(
                toLong(row[0]), toLong(row[1]), toLong(row[2]), toLong(row[3]), toLong(row[4])
        );
    }

    /** equipment_status 테이블 집계 */
    @SuppressWarnings("unchecked")
    public List<StatusCount> getEquipmentStatusCounts() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT status_code, COUNT(*) FROM equipment_status GROUP BY status_code
                """).getResultList();
        return rows.stream()
                .map(r -> new StatusCount((String) r[0], toLong(r[1])))
                .toList();
    }

    /** 최신 공장 OEE */
    @SuppressWarnings("unchecked")
    public BigDecimal getLatestFactoryOee(String factoryId) {
        List<Object> result = em.createNativeQuery("""
                SELECT factory_oee FROM factory_kpi_log
                WHERE factory_id = :factoryId ORDER BY recorded_at DESC LIMIT 1
                """)
                .setParameter("factoryId", factoryId)
                .getResultList();
        return result.isEmpty() ? null : (BigDecimal) result.get(0);
    }

    /** 최신 라인별 OEE */
    @SuppressWarnings("unchecked")
    public BigDecimal getLatestLineOee(String lineId) {
        List<Object> result = em.createNativeQuery("""
                SELECT line_oee FROM line_kpi_log
                WHERE line_id = :lineId ORDER BY recorded_at DESC LIMIT 1
                """)
                .setParameter("lineId", lineId)
                .getResultList();
        return result.isEmpty() ? null : (BigDecimal) result.get(0);
    }

    /** 시간별 라인 OEE (최근 24시간) */
    @SuppressWarnings("unchecked")
    public List<OeePoint> getHourlyLineOee(String lineId, LocalDateTime from) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT DATE_FORMAT(recorded_at, '%H:00'), AVG(line_oee)
                FROM line_kpi_log
                WHERE line_id = :lineId AND recorded_at >= :from
                GROUP BY DATE_FORMAT(recorded_at, '%H:00')
                ORDER BY DATE_FORMAT(recorded_at, '%H:00')
                """)
                .setParameter("lineId", lineId)
                .setParameter("from", from)
                .getResultList();
        return rows.stream()
                .map(r -> new OeePoint((String) r[0], (BigDecimal) r[1]))
                .toList();
    }

    private long toLong(Object val) {
        return val == null ? 0L : ((Number) val).longValue();
    }
}
