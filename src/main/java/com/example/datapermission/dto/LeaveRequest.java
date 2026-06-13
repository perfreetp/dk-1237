package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

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
        private String status;
        private String action;
    }

    @Data
    public static class LeaveProgress {
        private String taskId;
        private String status;
        private List<StepProgress> steps;
        private List<PermissionChange> affectedPermissions;
    }

    @Data
    public static class StepProgress {
        private String step;
        private String status;
        private LocalDateTime completedTime;
    }
}
