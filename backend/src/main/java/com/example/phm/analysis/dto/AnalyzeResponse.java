package com.example.phm.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyzeResponse {

    private Long analysisResultId;
    private Long vibrationWindowId;
    private Boolean rawWindowSaved;
    private Boolean alarmCreated;
    private String equipmentId;
    private String timestamp;
    private Integer samplingRate;
    private Integer rpm;
    private Integer windowSize;
    private Integer windowIndex;
    private AnalysisFeatures features;
    private FftSummary fft;
    private Double anomalyScore;
    private String alarmLevel;
    private String prediction;
    private Double confidence;
    private String modelVersion;
    private String modelInputType;
    private Integer modelInputSize;
    private Integer modelExpectedInputSize;
    private String modelInputStrategy;
    private String modelStatus;

    public Long getAnalysisResultId() {
        return analysisResultId;
    }

    public void setAnalysisResultId(Long analysisResultId) {
        this.analysisResultId = analysisResultId;
    }

    public Long getVibrationWindowId() {
        return vibrationWindowId;
    }

    public void setVibrationWindowId(Long vibrationWindowId) {
        this.vibrationWindowId = vibrationWindowId;
    }

    public Boolean getRawWindowSaved() {
        return rawWindowSaved;
    }

    public void setRawWindowSaved(Boolean rawWindowSaved) {
        this.rawWindowSaved = rawWindowSaved;
    }

    public Boolean getAlarmCreated() {
        return alarmCreated;
    }

    public void setAlarmCreated(Boolean alarmCreated) {
        this.alarmCreated = alarmCreated;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(String equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(Integer samplingRate) {
        this.samplingRate = samplingRate;
    }

    public Integer getRpm() {
        return rpm;
    }

    public void setRpm(Integer rpm) {
        this.rpm = rpm;
    }

    public Integer getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(Integer windowSize) {
        this.windowSize = windowSize;
    }

    public Integer getWindowIndex() {
        return windowIndex;
    }

    public void setWindowIndex(Integer windowIndex) {
        this.windowIndex = windowIndex;
    }

    public AnalysisFeatures getFeatures() {
        return features;
    }

    public void setFeatures(AnalysisFeatures features) {
        this.features = features;
    }

    public FftSummary getFft() {
        return fft;
    }

    public void setFft(FftSummary fft) {
        this.fft = fft;
    }

    public Double getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(Double anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public String getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(String alarmLevel) {
        this.alarmLevel = alarmLevel;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getModelInputType() {
        return modelInputType;
    }

    public void setModelInputType(String modelInputType) {
        this.modelInputType = modelInputType;
    }

    public Integer getModelInputSize() {
        return modelInputSize;
    }

    public void setModelInputSize(Integer modelInputSize) {
        this.modelInputSize = modelInputSize;
    }

    public Integer getModelExpectedInputSize() {
        return modelExpectedInputSize;
    }

    public void setModelExpectedInputSize(Integer modelExpectedInputSize) {
        this.modelExpectedInputSize = modelExpectedInputSize;
    }

    public String getModelInputStrategy() {
        return modelInputStrategy;
    }

    public void setModelInputStrategy(String modelInputStrategy) {
        this.modelInputStrategy = modelInputStrategy;
    }

    public String getModelStatus() {
        return modelStatus;
    }

    public void setModelStatus(String modelStatus) {
        this.modelStatus = modelStatus;
    }
}
