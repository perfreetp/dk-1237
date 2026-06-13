package com.example.datapermission.enums;

public enum GrantType {
    HEADQUARTER_VIEW_SUB("总部查看下级"),
    REGION_ISOLATED("区域互不可见"),
    PROJECT_TEMP("项目成员临时可见"),
    AUTO("自动授权"),
    MANUAL("手动授权"),
    TEMP("临时授权");

    private final String description;

    GrantType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
