package com.example.phm.analysis.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import com.example.phm.analysis.entity.AnalysisResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findTopByEquipmentCodeOrderByCreatedAtDesc(String equipmentCode);

    @EntityGraph(attributePaths = "vibrationWindow")
    List<AnalysisResult> findTop100ByEquipmentCodeOrderByCreatedAtDesc(String equipmentCode);

    @EntityGraph(attributePaths = "vibrationWindow")
    List<AnalysisResult> findByEquipmentCodeOrderByCreatedAtDesc(String equipmentCode, Pageable pageable);

    @EntityGraph(attributePaths = "vibrationWindow")
    List<AnalysisResult> findByEquipmentCodeAndAnalysisTypeOrderByCreatedAtDesc(
            String equipmentCode, String analysisType, Pageable pageable);

    List<AnalysisResult> findTop100ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "vibrationWindow")
    @Query("""
            select a
            from AnalysisResult a
            join a.vibrationWindow w
            where a.equipmentCode = :equipmentCode
              and w.measuredAt between :start and :end
            order by w.measuredAt asc, a.id asc
            """)
    List<AnalysisResult> findByEquipmentCodeAndMeasuredAtBetween(
            @Param("equipmentCode") String equipmentCode,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 설비 코드 목록에 대해 각 설비의 최신 1건씩만 가져온다.
     * N+1 회피용: 라인/대시보드 집계에서 설비당 1회 findTop 호출하던 패턴을 한 번의 쿼리로 대체.
     * MySQL window function (ROW_NUMBER) 을 사용.
     */
    @Query(value = """
            SELECT a.*
            FROM (
                SELECT a.*,
                       ROW_NUMBER() OVER (PARTITION BY a.equipment_code ORDER BY a.created_at DESC, a.id DESC) AS rn
                FROM analysis_result a
                WHERE a.equipment_code IN (:equipmentCodes)
            ) a
            WHERE a.rn = 1
            """, nativeQuery = true)
    List<AnalysisResult> findLatestForEquipmentCodes(@Param("equipmentCodes") List<String> equipmentCodes);
}
