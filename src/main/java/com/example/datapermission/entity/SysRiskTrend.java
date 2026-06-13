package com.example.datapermission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_risk_trend")
public class SysRiskTrend {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String trendDate;

    private String riskType;

    private Long orgId;

    private Long postId;

    private Long count;

    private Double changeRate;

    private Long cumulativeCount;

    private LocalDateTime createdTime;
}
