package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission_recovery")
public class SysPermissionRecovery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private Long permissionId;

    private Long userId;

    private Long resourceId;

    private String resourceCode;

    private String action;

    private Integer status;

    private String errorMessage;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime lastRetryTime;

    private LocalDateTime nextRetryTime;

    private LocalDateTime completedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
