package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission_change_log")
public class SysPermissionChangeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long permissionId;

    private String changeType;

    private String changeContent;

    private String beforeValue;

    private String afterValue;

    private String changeReason;

    private Long changeBy;

    private LocalDateTime changeTime;

    private String clientIp;

    private String userAgent;
}
