package com.example.datapermission.enums;

public enum RiskType {
    EXPIRING("快到期", 7, "MEDIUM"),
    UNUSED("长期未使用", 90, "HIGH"),
    OVER_GRANTED("权限过大", 0, "CRITICAL");

    private final String description;
    private final Integer threshold;
    private final String defaultRiskLevel;

    RiskType(String description, Integer threshold, String defaultRiskLevel) {
        this.description = description;
        this.threshold = threshold;
        this.defaultRiskLevel = defaultRiskLevel;
    }

    public String getDescription() {
        return description;
    }

    public Integer getThreshold() {
        return threshold;
    }

    public String getDefaultRiskLevel() {
        return defaultRiskLevel;
    }
}
