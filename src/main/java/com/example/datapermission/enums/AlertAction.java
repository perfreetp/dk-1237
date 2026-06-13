package com.example.datapermission.enums;

public enum AlertAction {
    CONFIRMED("已确认"),
    FALSE_ALARM("误报"),
    RESTRICTED("已限制"),
    BLOCKED("已封禁");

    private final String description;

    AlertAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
