package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RiskDashboardRequest {
    private List<Long> orgIds;
    private List<Long> postIds;
    private List<String> resourceSensitivityLevels;
    private String riskType;
    private Long startDate;
    private Long endDate;
    private String groupBy;
    private Integer pageNum;
    private Integer pageSize;
}
