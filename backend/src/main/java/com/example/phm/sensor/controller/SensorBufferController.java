package com.example.phm.sensor.controller;

<<<<<<< HEAD
import java.util.List;
import java.util.Set;

import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.dto.SensorBufferResponse;
import com.example.phm.sensor.dto.SensorDataRequest;
=======
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.example.phm.sensor.SensorBuffer;
import com.example.phm.sensor.SensorBufferKeys;
import com.example.phm.sensor.SensorBufferRegistry;
import com.example.phm.sensor.dto.SensorBufferLatestResponse;
import com.example.phm.sensor.dto.SensorBufferResponse;
import com.example.phm.sensor.dto.SensorDataRequest;
import com.example.phm.sensor.dto.SensorLatestValuesRequest;
>>>>>>> feature/develop_before
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

<<<<<<< HEAD
    /** 센서 데이터 버퍼에 적재 (배치 push 지원) */
    @PostMapping("/{bufferKey}")
=======
    @GetMapping("/latest-values")
    public List<SensorBufferLatestResponse> latestValues(@RequestParam List<String> bufferKeys) {
        return latestValuesFor(bufferKeys);
    }

    @PostMapping("/latest-values")
    public List<SensorBufferLatestResponse> latestValues(@RequestBody SensorLatestValuesRequest request) {
        return latestValuesFor(request == null ? List.of() : request.bufferKeys());
    }

    @PostMapping("/{bufferKey:.+}")
>>>>>>> feature/develop_before
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void push(
            @PathVariable String bufferKey,
            @Valid @RequestBody SensorDataRequest request
    ) {
        request.frames().forEach(f -> registry.push(bufferKey, f));
    }

<<<<<<< HEAD
    /** 버퍼 전체 스냅샷 조회 */
    @GetMapping("/{bufferKey}")
=======
    @GetMapping("/{bufferKey:.+}")
>>>>>>> feature/develop_before
    public SensorBufferResponse get(
            @PathVariable String bufferKey,
            @RequestParam(defaultValue = "0") int last
    ) {
<<<<<<< HEAD
        SensorBuffer buf = registry.get(bufferKey);
=======
        SensorBuffer buf = resolveBuffer(bufferKey);
>>>>>>> feature/develop_before
        if (buf == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Buffer not found: " + bufferKey);
        }
        var frames = buf.snapshot();
        var result = last > 0 && last < frames.size()
                ? frames.subList(frames.size() - last, frames.size())
                : frames;
        return new SensorBufferResponse(bufferKey, buf.size(), buf.capacity(), buf.latest(), result);
    }

<<<<<<< HEAD
    /** 등록된 버퍼 키 목록 */
=======
>>>>>>> feature/develop_before
    @GetMapping
    public Set<String> listKeys() {
        return registry.registeredKeys();
    }
<<<<<<< HEAD
=======

    private List<SensorBufferLatestResponse> latestValuesFor(List<String> bufferKeys) {
        return normalizeBufferKeys(bufferKeys)
                .map(this::latestValue)
                .toList();
    }

    private SensorBufferLatestResponse latestValue(String bufferKey) {
        SensorBuffer buf = resolveBuffer(bufferKey);
        if (buf == null) {
            return new SensorBufferLatestResponse(bufferKey, 0, 0, null);
        }
        return new SensorBufferLatestResponse(bufferKey, buf.size(), buf.capacity(), buf.latest());
    }

    private Stream<String> normalizeBufferKeys(List<String> bufferKeys) {
        if (bufferKeys == null) {
            return Stream.empty();
        }
        return bufferKeys.stream()
                .flatMap(key -> Arrays.stream(key.split(",")))
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .distinct();
    }

    private SensorBuffer resolveBuffer(String bufferKey) {
        SensorBuffer buffer = registry.get(bufferKey);
        if (buffer != null) {
            return buffer;
        }
        for (String alternateKey : SensorBufferKeys.alternateKeys(bufferKey)) {
            SensorBuffer alternate = registry.get(alternateKey);
            if (alternate != null) {
                return alternate;
            }
        }
        return null;
    }
>>>>>>> feature/develop_before
}
