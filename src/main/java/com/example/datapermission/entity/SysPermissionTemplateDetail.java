package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission_template_detail")
public class SysPermissionTemplateDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private Long resourceId;

    private String operationType;

    private String fieldLevelMap;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
