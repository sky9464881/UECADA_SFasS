package com.example.phm.analysis.service;

import com.example.phm.analysis.dto.AnalyzeRequest;
import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
<<<<<<< HEAD
=======
import java.util.Map;
>>>>>>> feature/develop_before
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiAnalysisClient {

    private final RestClient aiApiRestClient;

    public AiAnalysisClient(RestClient aiApiRestClient) {
        this.aiApiRestClient = aiApiRestClient;
    }

    public AnalyzeResponse analyze(VibrationWindowMessage message) {
<<<<<<< HEAD
=======
        return analyze(message, true, Map.of());
    }

    public AnalyzeResponse analyze(VibrationWindowMessage message, boolean persist) {
        return analyze(message, persist, Map.of());
    }

    public AnalyzeResponse analyze(VibrationWindowMessage message, boolean persist, Map<String, Object> sensorSnapshot) {
        AnalyzeRequest request = AnalyzeRequest.from(message);
        request.setPersist(persist);
        request.setSensorSnapshot(sensorSnapshot);
>>>>>>> feature/develop_before
        return aiApiRestClient.post()
                .uri("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
<<<<<<< HEAD
                .body(AnalyzeRequest.from(message))
=======
                .body(request)
>>>>>>> feature/develop_before
                .retrieve()
                .body(AnalyzeResponse.class);
    }
}
