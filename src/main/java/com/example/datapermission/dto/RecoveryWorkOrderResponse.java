package com.example.datapermission.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RecoveryWorkOrderResponse {
    private String orderNo;
    private String triggerType;
    private String status;
    private Integer totalPermissions;
    private Integer pendingCount;
    private Integer successCount;
    private Integer failedCount;
    private List<WorkOrderItem> items;
    private List<RetryAttemptRecord> retryHistory;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    @Data
    @Builder
    public static class WorkOrderItem {
        private Long id;
        private Long permissionId;
        private String resourceCode;
        private String resourceName;
        private Integer status;
        private String statusName;
        private String errorMessage;
        private Integer retryCount;
        private Integer maxRetryCount;
        private LocalDateTime nextRetryTime;
        private LocalDateTime lastRetryTime;
        private List<RetryAttemptRecord> attempts;
    }

    @Data
    @Builder
    public static class RetryAttemptRecord {
        private Long id;
        private Integer attemptNumber;
        private String status;
        private String errorMessage;
        private LocalDateTime executedTime;
        private String responseResult;
    }
}
