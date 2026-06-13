package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_recovery_retry_log")
public class SysRecoveryRetryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recoveryId;

    private String orderNo;

    private Long permissionId;

    private Integer attemptNumber;

    private String status;

    private String errorMessage;

    private String stackTrace;

    private String requestParams;

    private String responseResult;

    private Long executedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime executedTime;
}
