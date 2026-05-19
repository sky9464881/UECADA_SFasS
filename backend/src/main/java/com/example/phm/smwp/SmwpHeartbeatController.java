package com.example.phm.smwp;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SMWP "데이터 연결" 검증용 엔드포인트.
 * 라벨 하나가 1초마다 epochSecond / counter 값을 갱신하면 연결이 살아있다고 즉시 확인 가능.
 */
@RestController
@RequestMapping("/api/smwp")
public class SmwpHeartbeatController {

    private final AtomicLong counter = new AtomicLong(0);

    @GetMapping("/heartbeat")
    public Map<String, Object> heartbeat() {
        long n = counter.incrementAndGet();
        OffsetDateTime now = OffsetDateTime.now();
        double wave = Math.round(Math.sin(n / 5.0) * 1000.0) / 1000.0;
        return Map.of(
                "ok", true,
                "counter", n,
                "epochSeconds", now.toEpochSecond(),
                "iso", now.toString(),
                "wave", wave
        );
    }
}
