package com.example.phm.demo.repository;

import java.util.Collection;
import java.util.List;

import com.example.phm.demo.entity.EquipmentRuntimeDemo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRuntimeDemoRepository extends JpaRepository<EquipmentRuntimeDemo, String> {

    List<EquipmentRuntimeDemo> findByEquipmentCodeIn(Collection<String> equipmentCodes);
}
