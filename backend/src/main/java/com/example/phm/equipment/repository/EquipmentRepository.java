package com.example.phm.equipment.repository;

import java.util.List;
import java.util.Optional;

import com.example.phm.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByEquipmentCode(String equipmentCode);

    boolean existsByEquipmentCode(String equipmentCode);

    // equipment.location 컬럼에 line_id 가 들어있는 구조 활용
    @Query(value = """
            SELECT e.* FROM equipment e
            JOIN line l ON e.location = l.line_id
            WHERE l.factory_id = :factoryId
            """, nativeQuery = true)
    List<Equipment> findByFactoryId(@Param("factoryId") String factoryId);
}
