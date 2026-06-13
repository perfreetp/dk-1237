package com.example.datapermission.enums;

public enum AlertDimension {
    DOWNLOAD_COUNT("下载次数", 100, 200, 0.30),
    DATA_VOLUME("数据量", 10000, 50000, 0.25),
    SENSITIVE_FIELD_COUNT("敏感字段数量", 10, 30, 0.25),
    ACCESS_FREQUENCY("访问频率", 500, 1000, 0.10),
    OFF_HOURS_ACCESS("非工作时间访问", 0, 0, 0.10);

    private final String description;
    private final Integer warningThreshold;
    private final Integer dangerThreshold;
    private final Double weight;

    AlertDimension(String description, Integer warningThreshold, Integer dangerThreshold, Double weight) {
        this.description = description;
        this.warningThreshold = warningThreshold;
        this.dangerThreshold = dangerThreshold;
        this.weight = weight;
    }

    public String getDescription() {
        return description;
    }

    public Integer getWarningThreshold() {
        return warningThreshold;
    }

    public Integer getDangerThreshold() {
        return dangerThreshold;
    }

    public Double getWeight() {
        return weight;
    }

    public Integer calculateScore(Integer actual) {
        if (actual == null) return 0;

        if (this == OFF_HOURS_ACCESS) {
            return actual > 0 ? 50 : 0;
        }

        if (actual >= dangerThreshold) {
            return 100;
        } else if (actual >= warningThreshold) {
            return 50 + (actual - warningThreshold) * 50 / (dangerThreshold - warningThreshold);
        }
        return 0;
    }
}
