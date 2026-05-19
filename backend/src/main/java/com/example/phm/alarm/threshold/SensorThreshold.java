package com.example.phm.alarm.threshold;

public record SensorThreshold(
        double warnLow,
        double warnHigh,
        double dangerLow,
        double dangerHigh
) {
    /** 단방향 상한 임계값 (값이 너무 높을 때) */
    public static SensorThreshold highOnly(double warnHigh, double dangerHigh) {
        return new SensorThreshold(Double.NaN, warnHigh, Double.NaN, dangerHigh);
    }

    /** 단방향 하한 임계값 (값이 너무 낮을 때) */
    public static SensorThreshold lowOnly(double warnLow, double dangerLow) {
        return new SensorThreshold(warnLow, Double.NaN, dangerLow, Double.NaN);
    }

    /** 양방향 밴드 임계값 (정상 범위 이탈 시) */
    public static SensorThreshold band(double dangerLow, double warnLow, double warnHigh, double dangerHigh) {
        return new SensorThreshold(warnLow, warnHigh, dangerLow, dangerHigh);
    }

    /** "DANGER", "WARNING", "NORMAL" 중 하나를 반환 */
    public String evaluate(double value) {
        if (!Double.isNaN(dangerLow) && value < dangerLow) return "DANGER";
        if (!Double.isNaN(dangerHigh) && value > dangerHigh) return "DANGER";
        if (!Double.isNaN(warnLow) && value < warnLow) return "WARNING";
        if (!Double.isNaN(warnHigh) && value > warnHigh) return "WARNING";
        return "NORMAL";
    }
}
