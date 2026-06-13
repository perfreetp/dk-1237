package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_anomaly_alert")
public class SysAnomalyAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String alertType;

    private String alertContent;

    private Integer alertLevel;

    private Integer riskScore;

    private String triggeredDimensions;

    private String relatedAccessLogs;

    private String suggestions;

    private String restrictActions;

    private Integer handleStatus;

    private Long handleBy;

    private LocalDateTime handleTime;

    private String handleResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
