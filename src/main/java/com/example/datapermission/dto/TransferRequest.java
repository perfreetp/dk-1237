package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class TransferRequest {

    private Long targetOrgId;

    private Long targetPostId;

    private LocalDateTime transferDate;

    private List<KeepPermission> keepPermissions;

    private List<RevokePermission> revokePermissions;

    private String reason;

    @Data
    public static class KeepPermission {
        private Long resourceId;
        private String reason;
    }

    @Data
    public static class RevokePermission {
        private Long resourceId;
        private String reason;
    }

    @Data
    public static class TransferResult {
        private String taskId;
        private String status;
        private TransferSummary summary;
        private PermissionChanges permissionChanges;
        private Comparison comparison;
        private List<ChangeDetail> changeDetails;
    }

    @Data
    public static class TransferSummary {
        private Long userId;
        private String userName;
        private String fromOrgName;
        private String toOrgName;
        private String fromPostName;
        private String toPostName;
        private LocalDateTime transferDate;
    }

    @Data
    public static class PermissionChanges {
        private List<ChangeItem> toKeep;
        private List<ChangeItem> toRevoke;
        private List<ChangeItem> toGrant;
    }

    @Data
    public static class ChangeItem {
        private Long permissionId;
        private Long resourceId;
        private String resourceName;
        private Boolean kept;
        private Boolean revoked;
        private Boolean granted;
        private String reason;
        private String fromTemplate;
    }

    @Data
    public static class Comparison {
        private BeforeAfterCount before;
        private BeforeAfterCount after;
        private List<String> newFields;
        private List<String> removedFields;
        private List<String> changedFields;
    }

    @Data
    public static class BeforeAfterCount {
        private Integer permissionCount;
        private Integer fieldCount;
        private Integer maxLevel;
    }

    @Data
    public static class ChangeDetail {
        private String changeId;
        private Long permissionId;
        private String resourceName;
        private String action;
        private String beforeValue;
        private String afterValue;
        private String reason;
        private LocalDateTime changeTime;
        private String changeBy;
    }

    @Data
    public static class TransferProgress {
        private String taskId;
        private String status;
        private TransferSummary summary;
        private List<ChangeDetail> changeLog;
        private Comparison currentComparison;
    }
}
