package com.example.phm.vibration.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import com.example.phm.vibration.entity.VibrationWindow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VibrationWindowRepository extends JpaRepository<VibrationWindow, Long> {

    Optional<VibrationWindow> findTopByEquipmentCodeOrderByMeasuredAtDesc(String equipmentCode);

    Optional<VibrationWindow> findTopByEquipmentCodeOrderByMeasuredAtDescIdDesc(String equipmentCode);

    List<VibrationWindow> findTop100ByEquipmentCodeOrderByMeasuredAtDescIdDesc(String equipmentCode);

    List<VibrationWindow> findByEquipmentCodeAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
            String equipmentCode,
            LocalDateTime start,
            LocalDateTime end
    );
}
