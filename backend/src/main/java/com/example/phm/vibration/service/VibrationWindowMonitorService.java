package com.example.phm.vibration.service;

import java.time.Instant;
<<<<<<< HEAD
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
=======
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.dto.VibrationRealtimeResponse;
>>>>>>> feature/develop_before
import com.example.phm.vibration.dto.VibrationWindowSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class VibrationWindowMonitorService {

    private final AtomicReference<VibrationWindowMessage> latestMessage = new AtomicReference<>();
    private final AtomicReference<Instant> lastReceivedAt = new AtomicReference<>();
    private final AtomicLong receivedCount = new AtomicLong();
<<<<<<< HEAD

    public void record(VibrationWindowMessage message) {
        latestMessage.set(message);
        lastReceivedAt.set(Instant.now());
        receivedCount.incrementAndGet();
=======
    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();

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
>>>>>>> feature/develop_before
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
<<<<<<< HEAD
=======

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
        return new VibrationRealtimeResponse(
                true,
                equipmentId,
                snapshot.receivedAt(),
                VibrationWindowSummaryResponse.from(snapshot.message()),
                includeValues ? snapshot.message().getValues() : List.of(),
                snapshot.analysis()
        );
    }

    private record Snapshot(
            VibrationWindowMessage message,
            AnalyzeResponse analysis,
            Instant receivedAt
    ) {
    }
>>>>>>> feature/develop_before
}
