package com.example.phm.factory.repository;

import java.util.List;

import com.example.phm.factory.entity.Line;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineRepository extends JpaRepository<Line, String> {
    List<Line> findByFactoryId(String factoryId);
}
