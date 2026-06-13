package com.example.datapermission.enums;

public enum OrgType {
    HEADQUARTER("集团总部"),
    COMPANY("公司"),
    REGION("区域"),
    DEPT("部门"),
    PROJECT("项目");

    private final String description;

    OrgType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
