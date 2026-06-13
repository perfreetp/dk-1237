package com.example.datapermission.enums;

public enum AlertType {
    ABNORMAL_DOWNLOAD("异常下载"),
    MULTIPLE_ACCESS("频繁访问"),
    OFF_HOURS_ACCESS("非工作时间访问"),
    SENSITIVE_DATA_ACCESS("敏感数据访问");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
