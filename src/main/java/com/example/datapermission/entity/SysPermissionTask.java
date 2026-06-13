package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission_task")
public class SysPermissionTask {

    @TableId(type = IdType.AUTO)
    private String taskId;

    private String taskType;

    private Long userId;

    private Long targetUserId;

    private String status;

    private String currentStep;

    private String steps;

    private String affectedPermissions;

    private String changeDetails;

    private String changeReason;

    private Long changeBy;

    private LocalDateTime dueDate;

    private LocalDateTime completedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
