package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_org_scope")
public class SysOrgScope {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String grantType;

    private Long sourceOrgId;

    private Long targetOrgId;

    private String targetOrgType;

    private Integer hierarchyDepth;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
