package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RecoveryTaskDTO {

    private String taskId;

    private String taskType;

    private String status;

    private Long userId;

    private String userName;

    private LocalDateTime createdTime;

    private LocalDateTime completedTime;

    private RecoverySummary summary;

    private List<RecoveryItemDTO> items;

    @Data
    public static class RecoverySummary {
        private Integer totalCount;
        private Integer successCount;
        private Integer failedCount;
        private Integer pendingCount;
        private Integer retryCount;
        private Double successRate;
    }

    @Data
    public static class RecoveryItemDTO {
        private Long id;
        private Long permissionId;
        private Long userId;
        private Long resourceId;
        private String resourceCode;
        private String resourceName;
        private String action;
        private Integer status;
        private String statusName;
        private String errorMessage;
        private Integer retryCount;
        private Integer maxRetryCount;
        private LocalDateTime lastRetryTime;
        private LocalDateTime nextRetryTime;
        private LocalDateTime completedTime;
        private String canRetry;
    }
}
