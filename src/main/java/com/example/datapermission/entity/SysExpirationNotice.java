package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_expiration_notice")
public class SysExpirationNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long permissionId;

    private String noticeType;

    private LocalDateTime noticeTime;

    private Integer noticeStatus;

    private LocalDateTime sentTime;

    private String noticeResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
