package com.example.datapermission.enums;

public enum RiskLevel {
    LOW("低风险", 0, 30),
    MEDIUM("中风险", 30, 60),
    HIGH("高风险", 60, 80),
    CRITICAL("严重风险", 80, 100);

    private final String description;
    private final Integer minScore;
    private final Integer maxScore;

    RiskLevel(String description, Integer minScore, Integer maxScore) {
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDescription() {
        return description;
    }

    public Integer getMinScore() {
        return minScore;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public static RiskLevel fromScore(Integer score) {
        if (score == null) return LOW;
        for (RiskLevel level : values()) {
            if (score >= level.minScore && score < level.maxScore) {
                return level;
            }
        }
        return CRITICAL;
    }
}
