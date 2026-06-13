package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class LeaveRequest {

    private LocalDateTime leaveDate;

    private Long transferToUserId;

    private Boolean transferPermissions = true;

    private Boolean notifyReviewers = true;

    private String reason;

    @Data
    public static class PermissionChange {
        private Long permissionId;
        private String resourceCode;
        private String resourceName;
        private String status;
        private String action;
        private String reason;
    }

    @Data
    public static class LeaveProgress {
        private String taskId;
        private String status;
        private Long userId;
        private String userName;
        private LocalDateTime leaveDate;
        private List<StepProgress> steps;
        private List<PermissionChange> affectedPermissions;
        private TransferInfo transferInfo;
    }

    @Data
    public static class StepProgress {
        private String step;
        private String stepName;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime completedTime;
        private String operator;
        private String notes;
    }

    @Data
    public static class TransferInfo {
        private Long targetUserId;
        private String targetUserName;
        private Integer transferredCount;
        private Integer revokedCount;
    }

    @Data
    public static class LeaveCompletionReport {
        private String taskId;
        private Long userId;
        private String userName;
        private LocalDateTime completedTime;
        private Integer totalPermissions;
        private Integer transferredCount;
        private Integer revokedCount;
        private List<PermissionChange> changeLog;
    }
}
