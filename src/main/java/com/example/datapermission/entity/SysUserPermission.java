package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_permission")
public class SysUserPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    private String orgScopeType;

    private String orgScopeValue;

    private Long permissionTemplateId;

    private String operationTypes;

    private Integer fieldAccessLevel;

    private Integer desensitizationEnabled;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String grantReason;

    private String grantType;

    private Long sourceGrantId;

    private Integer status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
