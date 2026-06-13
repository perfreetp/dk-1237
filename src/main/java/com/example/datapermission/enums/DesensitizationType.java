package com.example.datapermission.enums;

public enum DesensitizationType {
    NONE("不脱敏"),
    MASK("掩码"),
    HASH("哈希"),
    ENCRYPT("加密"),
    HIDE("隐藏");

    private final String description;

    DesensitizationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
