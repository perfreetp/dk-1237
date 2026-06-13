package com.example.datapermission.enums;

public enum ChangeType {
    GRANT("授予"),
    REVOKE("撤销"),
    MODIFY("修改"),
    EXPIRE("过期"),
    TRANSFER("转让");

    private final String description;

    ChangeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
