package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_access_strategy")
public class SysAccessStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String strategyCode;

    private String strategyName;

    private String callerCode;

    private String callerName;

    private String tenantId;

    private String resourceDomain;

    private Integer defaultDesensitizationLevel;

    private Integer defaultOrgScopeType;

    private String defaultOrgScopeValue;

    private Integer defaultFieldAccessLevel;

    private String fallbackDenyMessage;

    private Integer priority;

    private Integer status;

    private String extendsConfig;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
