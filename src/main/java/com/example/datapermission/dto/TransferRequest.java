package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

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
        private PermissionChanges permissionChanges;
        private Comparison comparison;
    }

    @Data
    public static class PermissionChanges {
        private List<ChangeItem> toKeep;
        private List<ChangeItem> toRevoke;
        private List<ChangeItem> toGrant;
    }

    @Data
    public static class ChangeItem {
        private Long resourceId;
        private String resourceName;
        private Boolean kept;
        private String revokeReason;
        private String fromTemplate;
        private String reason;
    }

    @Data
    public static class Comparison {
        private Integer beforeCount;
        private Integer afterCount;
        private List<String> newFields;
        private List<String> removedFields;
    }
}
