package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrgScopeDTO {
    private Long id;
    private String grantType;
    private Long sourceOrgId;
    private String sourceOrgName;
    private Long targetOrgId;
    private String targetOrgName;
    private String targetOrgType;
    private Integer hierarchyDepth;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private String statusName;
    private LocalDateTime createdTime;
}
