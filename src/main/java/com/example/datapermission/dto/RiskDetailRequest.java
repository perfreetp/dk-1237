package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RiskDetailRequest {
    private String riskType;
    private List<Long> orgIds;
    private List<Long> postIds;
    private List<String> sensitivityLevels;
    private Long startDate;
    private Long endDate;
    private Integer pageNum;
    private Integer pageSize;
    private String sortBy;
    private String sortOrder;
}
