package com.example.datapermission.enums;

public enum RulePriority {
    PRIVILEGED(100, "特权规则", "最高优先级，用于紧急授权"),
    DENY(200, "禁止规则", "禁止访问，绝对优先"),
    PROJECT_TEMP(300, "项目临时规则", "项目成员临时可见"),
    MANUAL(400, "手动授权规则", "管理员手动授权"),
    POST_TEMPLATE(500, "岗位模板规则", "根据岗位自动计算"),
    ORG_SCOPE(600, "组织范围规则", "总部查看下级、区域隔离等"),
    AUTO(700, "自动继承规则", "默认权限");

    private final Integer priority;
    private final String name;
    private final String description;

    RulePriority(Integer priority, String name, String description) {
        this.priority = priority;
        this.name = name;
        this.description = description;
    }

    public Integer getPriority() {
        return priority;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
