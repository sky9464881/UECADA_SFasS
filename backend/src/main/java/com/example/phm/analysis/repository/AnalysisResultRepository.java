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
}
