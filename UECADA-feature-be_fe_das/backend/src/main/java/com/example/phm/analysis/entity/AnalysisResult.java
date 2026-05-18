package com.example.phm.analysis.entity;

import java.time.LocalDateTime;

import com.example.phm.vibration.entity.VibrationWindow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis_result")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vibration_window_id", nullable = true)
    private VibrationWindow vibrationWindow;

    @Column(name = "equipment_code", nullable = false, length = 50)
    private String equipmentCode;

    @Column(name = "rms")
    private Double rms;

    @Column(name = "peak_frequency")
    private Double peakFrequency;

    @Column(name = "peak_to_peak")
    private Double peakToPeak;

    @Column(name = "crest_factor")
    private Double crestFactor;

    @Column(name = "kurtosis")
    private Double kurtosis;

    @Column(name = "prediction", length = 50)
    private String prediction;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "model_input_type", length = 50)
    private String modelInputType;

    @Column(name = "model_input_size")
    private Integer modelInputSize;

    @Column(name = "model_expected_input_size")
    private Integer modelExpectedInputSize;

    @Column(name = "model_input_strategy", length = 150)
    private String modelInputStrategy;

    @Column(name = "model_status", length = 50)
    private String modelStatus;

    @Column(name = "anomaly_score")
    private Double anomalyScore;

    @Column(name = "alarm_level", length = 20)
    private String alarmLevel;

    @Column(name = "analysis_type", length = 50)
    private String analysisType;

    @Column(name = "result_json", columnDefinition = "json")
    private String resultJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public VibrationWindow getVibrationWindow() {
        return vibrationWindow;
    }

    public void setVibrationWindow(VibrationWindow vibrationWindow) {
        this.vibrationWindow = vibrationWindow;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public Double getRms() {
        return rms;
    }

    public void setRms(Double rms) {
        this.rms = rms;
    }

    public Double getPeakFrequency() {
        return peakFrequency;
    }

    public void setPeakFrequency(Double peakFrequency) {
        this.peakFrequency = peakFrequency;
    }

    public Double getPeakToPeak() {
        return peakToPeak;
    }

    public void setPeakToPeak(Double peakToPeak) {
        this.peakToPeak = peakToPeak;
    }

    public Double getCrestFactor() {
        return crestFactor;
    }

    public void setCrestFactor(Double crestFactor) {
        this.crestFactor = crestFactor;
    }

    public Double getKurtosis() {
        return kurtosis;
    }

    public void setKurtosis(Double kurtosis) {
        this.kurtosis = kurtosis;
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

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
