package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_access_log")
public class SysAccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    private String operationType;

    private String accessDecision;

    private String deniedReason;

    private String queryConditions;

    private String resultScope;

    private String hiddenFields;

    private String maskedFields;

    private String sensitiveFieldsAccessed;

    private Long recordCount;

    private Long dataVolume;

    private String requestParams;

    private String clientIp;

    private String userAgent;

    private Long executionTimeMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
