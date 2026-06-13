package com.example.datapermission.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RuleChangeApprovalResponse {
    private String approvalNo;
    private String status;
    private String changeType;
    private Long userId;
    private String userName;
    private String resourceCode;
    private ImpactEstimateResponse impactEstimate;
    private Long approverId;
    private String approverName;
    private String approvalComment;
    private LocalDateTime approveTime;
    private LocalDateTime createdTime;

    @Data
    @Builder
    public static class ApprovalHistory {
        private Long id;
        private String approvalNo;
        private String action;
        private String actorName;
        private String comment;
        private LocalDateTime actionTime;
    }
}
