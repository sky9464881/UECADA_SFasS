package com.example.phm.dashboard.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.phm.dashboard.dto.FrontendDashboardResponse;
import com.example.phm.dashboard.dto.FrontendDashboardResponse.AlarmSummary;
import com.example.phm.dashboard.dto.FrontendDashboardResponse.LineOeeSeries;
import com.example.phm.dashboard.dto.FrontendDashboardResponse.LineStat;
import com.example.phm.dashboard.dto.FrontendDashboardResponse.OeePoint;
import com.example.phm.dashboard.dto.FrontendDashboardResponse.StatusDonut;
import com.example.phm.dashboard.repository.DashboardNativeRepository;
import com.example.phm.dashboard.repository.DashboardNativeRepository.StatusCount;
import com.example.phm.factory.entity.Line;
import com.example.phm.factory.repository.LineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FrontendDashboardService {

    private static final String FACTORY_ID = "FACTORY-01";

    private final DashboardNativeRepository nativeRepo;
    private final LineRepository lineRepository;

    public FrontendDashboardService(
            DashboardNativeRepository nativeRepo,
            LineRepository lineRepository
    ) {
        this.nativeRepo = nativeRepo;
        this.lineRepository = lineRepository;
    }

    public FrontendDashboardResponse build() {
        // 1. 공장 OEE
        var factoryOee = nativeRepo.getLatestFactoryOee(FACTORY_ID);

        // 2. 설비 상태 도넛
        var statusDonut = buildStatusDonut();

        // 3. 알람 요약
        var alarmSummary = buildAlarmSummary();

        // 4. 라인별 최신 OEE + 시간별 시계열
        List<Line> lines = lineRepository.findByFactoryId(FACTORY_ID);
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        List<LineStat> lineStats = lines.stream()
                .map(l -> new LineStat(
                        l.getLineId(),
                        l.getLineName(),
                        nativeRepo.getLatestLineOee(l.getLineId())
                ))
                .toList();

        List<LineOeeSeries> oeeHourlySeries = lines.stream()
                .map(l -> {
                    List<OeePoint> points = nativeRepo.getHourlyLineOee(l.getLineId(), since)
                            .stream()
                            .map(p -> new OeePoint(p.recordedAt(), p.oee()))
                            .toList();
                    return new LineOeeSeries(l.getLineId(), l.getLineName(), points);
                })
                .toList();

        return new FrontendDashboardResponse(factoryOee, statusDonut, alarmSummary, lineStats, oeeHourlySeries);
    }

    private StatusDonut buildStatusDonut() {
        List<StatusCount> counts = nativeRepo.getEquipmentStatusCounts();
        Map<String, Long> map = counts.stream()
                .collect(Collectors.toMap(StatusCount::statusCode, StatusCount::count));

        long running     = map.getOrDefault("RUNNING",     0L);
        long standby     = map.getOrDefault("STANDBY",     0L);
        long alarm       = map.getOrDefault("ALARM",       0L);
        long maintenance = map.getOrDefault("MAINTENANCE", 0L);
        long total       = running + standby + alarm + maintenance;

        return new StatusDonut(running, standby, alarm, maintenance, total);
    }

    private AlarmSummary buildAlarmSummary() {
        var data = nativeRepo.getAlarmSummary();
        return new AlarmSummary(
                data.total(), data.critical(), data.warning(), data.resolved(), data.open()
        );
    }
}
