package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission_review_item")
public class SysPermissionReviewItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private Long permissionId;

    private Long userId;

    private Long resourceId;

    private String riskType;

    private Integer riskLevel;

    private String riskDetails;

    private String suggestions;

    private String reviewStatus;

    private Long reviewerId;

    private LocalDateTime reviewTime;

    private String reviewComment;

    private String recommendedAction;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
