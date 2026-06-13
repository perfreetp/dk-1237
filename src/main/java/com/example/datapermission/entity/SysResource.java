package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_resource")
public class SysResource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String resourceCode;

    private String resourceName;

    private String resourceType;

    private String description;

    private Integer sensitivityLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
