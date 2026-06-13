package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_risk_subscription")
public class SysRiskSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String subscriptionCode;

    private String subscriptionName;

    private Long subscriberId;

    private String subscriberName;

    private String subscriptionType;

    private List<Long> targetOrgIds;

    private List<Long> targetPostIds;

    private List<String> riskTypes;

    private String frequency;

    private String deliveryMethod;

    private String deliveryAddress;

    private Integer enabled;

    private LocalDateTime lastSentTime;

    private LocalDateTime nextSendTime;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
