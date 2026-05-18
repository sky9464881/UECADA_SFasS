package com.example.phm.equipment.repository;

import java.util.List;

import com.example.phm.equipment.entity.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EquipmentStatusRepository extends JpaRepository<EquipmentStatus, String> {

    List<EquipmentStatus> findByEquipIdIn(List<String> equipIds);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO equipment_status (equip_id, status_code)
            VALUES (:equipId, :statusCode)
            ON DUPLICATE KEY UPDATE status_code = :statusCode
            """, nativeQuery = true)
    void upsert(@Param("equipId") String equipId, @Param("statusCode") String statusCode);
}
