package com.example.phm.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisFeatures {

    private Double rms;
    private Double peakFrequency;
    private Double peakToPeak;
    private Double crestFactor;
    private Double kurtosis;

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
}
