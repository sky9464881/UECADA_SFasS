package com.example.phm.analysis.service;

import com.example.phm.analysis.dto.AnalyzeRequest;
import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.vibration.dto.VibrationWindowMessage;
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
        return aiApiRestClient.post()
                .uri("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(AnalyzeRequest.from(message))
                .retrieve()
                .body(AnalyzeResponse.class);
    }
}
