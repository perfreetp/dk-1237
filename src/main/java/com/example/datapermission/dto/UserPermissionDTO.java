package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserPermissionDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String realName;
    private Long resourceId;
    private String resourceCode;
    private String resourceName;
    private String orgScopeType;
    private Long permissionTemplateId;
    private String permissionTemplateName;
    private String operationTypes;
    private Integer fieldAccessLevel;
    private Integer desensitizationEnabled;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String grantReason;
    private String grantType;
    private Integer status;
    private String statusName;
    private LocalDateTime createdTime;
    private String createdByName;
}
