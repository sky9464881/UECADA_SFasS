package com.example.phm.vibration.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.example.phm.vibration.dto.VibrationWindowLatestResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.dto.VibrationWindowSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class VibrationWindowMonitorService {

    private final AtomicReference<VibrationWindowMessage> latestMessage = new AtomicReference<>();
    private final AtomicReference<Instant> lastReceivedAt = new AtomicReference<>();
    private final AtomicLong receivedCount = new AtomicLong();

    public void record(VibrationWindowMessage message) {
        latestMessage.set(message);
        lastReceivedAt.set(Instant.now());
        receivedCount.incrementAndGet();
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
}
