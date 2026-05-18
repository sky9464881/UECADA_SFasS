package com.example.phm.common;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/database")
public class DatabaseStatusController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseStatusController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Integer connected = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        Long equipmentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM equipment", Long.class);

        return Map.of(
                "connected", connected != null && connected == 1,
                "database", "smart_factory",
                "equipmentCount", equipmentCount == null ? 0 : equipmentCount
        );
    }
}
