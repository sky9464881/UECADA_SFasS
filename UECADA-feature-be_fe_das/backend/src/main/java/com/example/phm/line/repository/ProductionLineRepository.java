package com.example.phm.line.repository;

import java.util.List;

import com.example.phm.line.entity.ProductionLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionLineRepository extends JpaRepository<ProductionLine, String> {

    List<ProductionLine> findByFactoryId(String factoryId);
}
