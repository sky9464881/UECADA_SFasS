package com.example.phm.analysis.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FftSummary {

    private Double frequencyResolution;
    private Integer binCount;
    private List<Double> frequencies;
    private List<Double> magnitudes;

    public Double getFrequencyResolution() {
        return frequencyResolution;
    }

    public void setFrequencyResolution(Double frequencyResolution) {
        this.frequencyResolution = frequencyResolution;
    }

    public Integer getBinCount() {
        return binCount;
    }

    public void setBinCount(Integer binCount) {
        this.binCount = binCount;
    }

    public List<Double> getFrequencies() {
        return frequencies;
    }

    public void setFrequencies(List<Double> frequencies) {
        this.frequencies = frequencies;
    }

    public List<Double> getMagnitudes() {
        return magnitudes;
    }

    public void setMagnitudes(List<Double> magnitudes) {
        this.magnitudes = magnitudes;
    }
}
