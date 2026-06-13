package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_sensitive_field")
public class SysSensitiveField {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private String fieldName;

    private String fieldLabel;

    private Integer sensitivityLevel;

    private String desensitizationType;

    private String maskPattern;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
