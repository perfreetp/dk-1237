package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchAccessCheckResponse {

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private Integer partialCount;

    private Long totalExecutionTime;

    private List<BatchCheckResult> results;

    private BatchSummary summary;

    @Data
    public static class BatchCheckResult {
        private String itemId;
        private Boolean success;
        private String accessDecision;
        private Boolean allowed;
        private AccessScopeResult accessibleScope;
        private List<String> hiddenFields;
        private List<MaskedFieldResult> maskedFields;
        private SqlFilterResult sqlFilters;
        private String deniedReason;
        private ApplyPermissionResult applyPermission;
        private String errorMessage;
        private Long executionTime;
    }

    @Data
    public static class AccessScopeResult {
        private List<Long> orgIds;
        private String orgType;
        private List<Long> projectIds;
        private TimeRangeResult timeRange;
        private List<Integer> customerLevels;
    }

    @Data
    public static class TimeRangeResult {
        private String startTime;
        private String endTime;
    }

    @Data
    public static class MaskedFieldResult {
        private String field;
        private String maskedValue;
        private String reason;
    }

    @Data
    public static class SqlFilterResult {
        private String whereClause;
        private Map<String, Object> parameters;
        private List<String> appliedConditions;
    }

    @Data
    public static class ApplyPermissionResult {
        private Boolean canApply;
        private String applyUrl;
        private List<PermissionGroupResult> permissionGroups;
    }

    @Data
    public static class PermissionGroupResult {
        private Integer sensitivityLevel;
        private String levelName;
        private List<PermissionOptionResult> options;
    }

    @Data
    public static class PermissionOptionResult {
        private List<String> targetFields;
        private Integer requiredLevel;
        private String approvalLevel;
        private String validityPeriod;
    }

    @Data
    public static class BatchSummary {
        private Integer totalUsers;
        private Integer totalResources;
        private Integer allowCount;
        private Integer denyCount;
        private Integer partialCount;
        private Integer errorCount;
    }
}
