package com.example.phm.sensor.controller;

import java.util.List;
import java.util.Set;

import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.dto.SensorBufferLatestResponse;
import com.example.phm.sensor.dto.SensorBufferResponse;
import com.example.phm.sensor.dto.SensorDataRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sensors")
public class SensorBufferController {

    private final SensorBufferRegistry registry;

    public SensorBufferController(SensorBufferRegistry registry) {
        this.registry = registry;
    }

    /** 센서 데이터 버퍼에 적재 (배치 push 지원) */
    @PostMapping("/{bufferKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void push(
            @PathVariable String bufferKey,
            @Valid @RequestBody SensorDataRequest request
    ) {
        request.frames().forEach(f -> registry.push(bufferKey, f));
    }

    /** 버퍼 전체 스냅샷 조회 */
    @GetMapping("/{bufferKey}")
    public SensorBufferResponse get(
            @PathVariable String bufferKey,
            @RequestParam(defaultValue = "0") int last
    ) {
        SensorBuffer buf = registry.get(bufferKey);
        if (buf == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Buffer not found: " + bufferKey);
        }
        var frames = buf.snapshot();
        var result = last > 0 && last < frames.size()
                ? frames.subList(frames.size() - last, frames.size())
                : frames;
        return new SensorBufferResponse(bufferKey, buf.size(), buf.capacity(), buf.latest(), result);
    }

    @GetMapping("/latest-values")
    public List<SensorBufferLatestResponse> latestValues(@RequestParam List<String> bufferKeys) {
        return bufferKeys.stream()
                .map(this::latestValue)
                .toList();
    }

    /** 등록된 버퍼 키 목록 */
    @GetMapping
    public Set<String> listKeys() {
        return registry.registeredKeys();
    }

    private SensorBufferLatestResponse latestValue(String bufferKey) {
        SensorBuffer buf = registry.get(bufferKey);
        if (buf == null) {
            return new SensorBufferLatestResponse(bufferKey, 0, 0, null);
        }
        return new SensorBufferLatestResponse(bufferKey, buf.size(), buf.capacity(), buf.latest());
    }
}
