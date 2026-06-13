package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_rule_change_approval")
public class SysRuleChangeApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String approvalNo;

    private String changeType;

    private Long userId;

    private String userName;

    private String resourceCode;

    private String businessScenario;

    private String changeContent;

    private String impactEstimate;

    private String status;

    private Long approverId;

    private String approverName;

    private String approvalComment;

    private LocalDateTime approveTime;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
