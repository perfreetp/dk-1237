package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class RuleChangeApprovalRequest {
    private Long userId;
    private String resourceCode;
    private String businessScenario;
    private String tempAdjustmentsJson;
    private String impactEstimateJson;
    private List<Long> approverIds;
    private String changeReason;
}
