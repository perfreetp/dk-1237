package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class EnhancedAccessCheckResponse {

    private String accessDecision;

    private Boolean allowed;

    private List<AppliedRule> appliedRules;

    private AppliedRule effectiveRule;

    private AccessScope accessibleScope;

    private SqlFilters sqlFilters;

    private List<FieldPermission> fieldPermissions;

    private String deniedReason;

    private ApplyPermission applyPermission;

    private List<String> suggestions;

    private Long executionTime;

    @Data
    public static class AppliedRule {
        private Long ruleId;
        private String ruleType;
        private String ruleName;
        private Integer priority;
        private Boolean matched;
        private String matchReason;
        private String effectiveReason;
    }

    @Data
    public static class AccessScope {
        private List<Long> orgIds;
        private String orgType;
        private List<Long> projectIds;
        private TimeRangeDetail timeRange;
        private List<Integer> customerLevels;

        @Data
        public static class TimeRangeDetail {
            private String startTime;
            private String endTime;
        }
    }

    @Data
    public static class SqlFilters {
        private String whereClause;
        private String orderByClause;
        private String limitClause;
    }

    @Data
    public static class FieldPermission {
        private String field;
        private Boolean allowed;
        private Boolean masked;
        private String maskedValue;
        private String reason;
        private Integer requiredLevel;
    }

    @Data
    public static class ApplyPermission {
        private Boolean canApply;
        private String applyUrl;
        private List<PermissionOption> permissionOptions;
    }

    @Data
    public static class PermissionOption {
        private String permissionType;
        private List<String> targetFields;
        private Integer requiredLevel;
        private Boolean approvalRequired;
        private String validityPeriod;
    }
}
