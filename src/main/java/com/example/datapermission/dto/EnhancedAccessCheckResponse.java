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
        private Boolean effective;
        private String status;
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
        private Map<String, Object> parameters;
        private List<String> appliedConditions;
    }

    @Data
    public static class FieldPermission {
        private String field;
        private String fieldLabel;
        private Boolean allowed;
        private Boolean masked;
        private String maskedValue;
        private String reason;
        private Integer requiredLevel;
        private Integer resourceSensitivityLevel;
    }

    @Data
    public static class ApplyPermission {
        private Boolean canApply;
        private String applyUrl;
        private List<PermissionGroup> permissionGroups;
        private List<PermissionOption> directOptions;
    }

    @Data
    public static class PermissionGroup {
        private Integer sensitivityLevel;
        private String levelName;
        private String levelDescription;
        private List<PermissionOption> options;
    }

    @Data
    public static class PermissionOption {
        private String permissionType;
        private String permissionTypeName;
        private List<String> targetFields;
        private List<String> targetFieldLabels;
        private Integer requiredLevel;
        private Boolean approvalRequired;
        private String approvalLevel;
        private String validityPeriod;
        private String applyTemplate;
    }
}
