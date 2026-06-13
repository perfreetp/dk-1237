package com.example.datapermission.enums;

public enum OperationType {
    READ("读取"),
    WRITE("写入"),
    DELETE("删除"),
    EXPORT("导出");

    private final String description;

    OperationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
