package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_recovery_work_order")
public class SysRecoveryWorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String triggerType;

    private Long triggerUserId;

    private Long targetUserId;

    private String targetUsername;

    private Long responsibleId;

    private String status;

    private Integer totalPermissions;

    private Integer pendingCount;

    private Integer successCount;

    private Integer failedCount;

    private String transferToUserId;

    private String transferToUsername;

    private String remark;

    private LocalDateTime dueTime;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
