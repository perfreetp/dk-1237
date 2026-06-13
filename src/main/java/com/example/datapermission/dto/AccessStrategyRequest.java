package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AccessStrategyRequest {
    private String callerCode;
    private String tenantId;
    private String resourceDomain;
    private String strategyName;
    private Integer defaultDesensitizationLevel;
    private Integer defaultOrgScopeType;
    private String defaultOrgScopeValue;
    private Integer defaultFieldAccessLevel;
    private String fallbackDenyMessage;
    private Integer priority;
    private Map<String, Object> extendsConfig;
}
