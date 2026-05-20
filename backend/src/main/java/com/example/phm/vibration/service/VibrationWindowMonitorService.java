package com.example.phm.vibration.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
import com.example.phm.vibration.dto.VibrationWindowSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class VibrationWindowMonitorService {

    private static final Duration RECENT_ABNORMAL_HOLD = Duration.ofSeconds(90);

    private final AtomicReference<VibrationWindowMessage> latestMessage = new AtomicReference<>();
    private final AtomicReference<Instant> lastReceivedAt = new AtomicReference<>();
    private final AtomicLong receivedCount = new AtomicLong();
    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Snapshot> recentAbnormalSnapshots = new ConcurrentHashMap<>();

    public void record(VibrationWindowMessage message) {
        Instant now = Instant.now();
        latestMessage.set(message);
        lastReceivedAt.set(now);
        receivedCount.incrementAndGet();
        snapshots.compute(message.getEquipmentId(), (equipmentId, existing) ->
                new Snapshot(message, existing == null ? null : existing.analysis(), now)
        );
    }

    public void recordAnalysis(VibrationWindowMessage message, AnalyzeResponse analysis) {
        Instant now = Instant.now();
        snapshots.compute(message.getEquipmentId(), (equipmentId, existing) ->
                new Snapshot(existing == null ? message : existing.message(), analysis, now)
        );
        if (isAbnormal(analysis)) {
            recentAbnormalSnapshots.put(message.getEquipmentId(), new Snapshot(message, analysis, now));
        }
    }

    public VibrationWindowLatestResponse latest() {
        VibrationWindowMessage message = latestMessage.get();
        if (message == null) {
            return VibrationWindowLatestResponse.empty();
        }

        return new VibrationWindowLatestResponse(
                true,
                receivedCount.get(),
                lastReceivedAt.get(),
                VibrationWindowSummaryResponse.from(message)
        );
    }

    public VibrationRealtimeResponse latestRealtime(String equipmentId) {
        Snapshot snapshot = snapshots.get(equipmentId);
        if (snapshot == null) {
            return VibrationRealtimeResponse.empty(equipmentId);
        }
        return toRealtimeResponse(equipmentId, snapshot, true);
    }

    public List<VibrationRealtimeResponse> latestRealtimeAll() {
        return snapshots.entrySet().stream()
                .map(entry -> toRealtimeResponse(entry.getKey(), entry.getValue(), false))
                .sorted(Comparator.comparing(VibrationRealtimeResponse::equipmentId))
                .toList();
    }

    private VibrationRealtimeResponse toRealtimeResponse(String equipmentId, Snapshot snapshot, boolean includeValues) {
        Snapshot responseSnapshot = heldAbnormalSnapshot(equipmentId, snapshot);
        return new VibrationRealtimeResponse(
                true,
                equipmentId,
                responseSnapshot.receivedAt(),
                VibrationWindowSummaryResponse.from(responseSnapshot.message()),
                includeValues ? responseSnapshot.message().getValues() : List.of(),
                responseSnapshot.analysis()
        );
    }

    private Snapshot heldAbnormalSnapshot(String equipmentId, Snapshot current) {
        if (isAbnormal(current.analysis())) {
            return current;
        }

        Snapshot abnormal = recentAbnormalSnapshots.get(equipmentId);
        if (abnormal == null) {
            return current;
        }

        if (Duration.between(abnormal.receivedAt(), Instant.now()).compareTo(RECENT_ABNORMAL_HOLD) > 0) {
            recentAbnormalSnapshots.remove(equipmentId, abnormal);
            return current;
        }

        return abnormal;
    }

    private boolean isAbnormal(AnalyzeResponse analysis) {
        if (analysis == null) {
            return false;
        }

        String alarmLevel = normalize(analysis.getAlarmLevel());
        if ("warning".equals(alarmLevel) || "danger".equals(alarmLevel)) {
            return true;
        }

        String prediction = normalize(analysis.getPrediction());
        return !prediction.isBlank()
                && !"normal".equals(prediction)
                && !"not_trained".equals(prediction)
                && !"prediction_error".equals(prediction);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Snapshot(
            VibrationWindowMessage message,
            AnalyzeResponse analysis,
            Instant receivedAt
    ) {
    }
}
