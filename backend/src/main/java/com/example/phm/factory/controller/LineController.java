package com.example.phm.factory.controller;

import java.math.BigDecimal;
import java.util.List;

import com.example.phm.factory.dto.LineResponse;
import com.example.phm.factory.entity.Line;
import com.example.phm.factory.repository.LineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lines")
public class LineController {

    private final LineRepository lineRepository;

    @PersistenceContext
    private EntityManager em;

    public LineController(LineRepository lineRepository) {
        this.lineRepository = lineRepository;
    }

    @GetMapping
    public List<LineResponse> findAll(@RequestParam(required = false) String factoryId) {
        List<Line> lines = factoryId != null
                ? lineRepository.findByFactoryId(factoryId)
                : lineRepository.findAll();
        return lines.stream().map(this::toResponse).toList();
    }

    private LineResponse toResponse(Line line) {
        String id = line.getLineId();

        // 설비 상태별 카운트 (equipment_status.equip_id LIKE 'LINE-XX_%')
        List<Object[]> statusRows = em.createNativeQuery("""
                SELECT es.status_code, COUNT(*) AS cnt
                FROM equipment_status es
                WHERE es.equip_id LIKE :prefix
                GROUP BY es.status_code
                """)
                .setParameter("prefix", id + "\\_%")
                .getResultList();

        long running = 0, alarm = 0, standby = 0, total = 0;
        for (Object[] row : statusRows) {
            String code = (String) row[0];
            long cnt = ((Number) row[1]).longValue();
            total += cnt;
            switch (code) {
                case "RUNNING" -> running += cnt;
                case "ALARM" -> alarm += cnt;
                case "STANDBY" -> standby += cnt;
            }
        }

        // 오픈 알람 카운트
        Number openCount = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM alarm
                WHERE equipment_code LIKE :prefix AND alarm_status = 'OPEN'
                """)
                .setParameter("prefix", id + "\\_%")
                .getSingleResult();

        // 최신 라인 OEE
        @SuppressWarnings("unchecked")
        List<Object> oeeRows = em.createNativeQuery("""
                SELECT line_oee FROM line_kpi_log
                WHERE line_id = :lineId
                ORDER BY recorded_at DESC LIMIT 1
                """)
                .setParameter("lineId", id)
                .getResultList();
        BigDecimal latestOee = oeeRows.isEmpty() ? null : (BigDecimal) oeeRows.get(0);

        return new LineResponse(
                id,
                line.getLineName(),
                line.getLineStatus(),
                line.getFactoryId(),
                total,
                running,
                alarm,
                standby,
                openCount.longValue(),
                latestOee
        );
    }
}
