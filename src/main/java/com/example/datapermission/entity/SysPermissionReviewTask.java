package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission_review_task")
public class SysPermissionReviewTask {

    @TableId(type = IdType.AUTO)
    private String taskId;

    private String taskName;

    private String scopeOrgIds;

    private String scopeUserIds;

    private String scopeResourceTypes;

    private String riskFilters;

    private String reviewers;

    private LocalDateTime dueDate;

    private Boolean autoRemind;

    private Integer remindInterval;

    private String status;

    private Long statisticsTotal;

    private Long statisticsExpiring;

    private Long statisticsUnused;

    private Long statisticsOverGranted;

    private Long completedCount;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
