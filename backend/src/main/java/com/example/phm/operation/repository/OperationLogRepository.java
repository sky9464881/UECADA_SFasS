package com.example.phm.operation.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.operation.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findByEquipmentCodeAndStartAtBetweenOrderByStartAtAsc(
            String equipmentCode, LocalDateTime from, LocalDateTime to);
}
