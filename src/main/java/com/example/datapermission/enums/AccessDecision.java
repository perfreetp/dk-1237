package com.example.datapermission.enums;

public enum AccessDecision {
    ALLOW("允许"),
    DENY("拒绝"),
    PARTIAL("部分允许");

    private final String description;

    AccessDecision(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
