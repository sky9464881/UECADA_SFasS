package com.example.phm.alarm.repository;

import java.util.List;
import java.util.Optional;

import com.example.phm.alarm.entity.AlarmHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmHistoryRepository extends JpaRepository<AlarmHistory, Long> {

    @EntityGraph(attributePaths = "analysisResult")
    List<AlarmHistory> findTop100ByEquipmentCodeOrderByOccurredAtDesc(String equipmentCode);

    @EntityGraph(attributePaths = "analysisResult")
    List<AlarmHistory> findTop100ByOrderByOccurredAtDesc();

    Optional<AlarmHistory> findTopByEquipmentCodeAndStatusOrderByOccurredAtDesc(String equipmentCode, String status);
}
