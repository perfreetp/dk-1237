package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class EnhancedAccessCheckRequest {

    private Long userId;

    private String resourceCode;

    private String operationType;

    private String version = "v2";

    private Map<String, Object> queryConditions;

    private ComplexConditions complexConditions;

    private List<String> requestedFields;

    private Boolean returnSqlFilter = true;

    private Boolean returnAppliedRules = true;

    @Data
    public static class ComplexConditions {
        private TimeRange timeRange;
        private CustomerLevel customerLevel;
        private ProjectScope projectScope;
        private List<ConditionRule> rules;
        private String operator = "AND";
    }

    @Data
    public static class TimeRange {
        private String field;
        private String startTime;
        private String endTime;
        private String timeZone;

        public boolean isValid() {
            return (startTime != null && !startTime.isEmpty()) || (endTime != null && !endTime.isEmpty());
        }

        public boolean hasBoth() {
            return (startTime != null && !startTime.isEmpty()) && (endTime != null && !endTime.isEmpty());
        }
    }

    @Data
    public static class CustomerLevel {
        private String field;
        private List<Integer> levels;
        private String operator = "IN";

        public boolean isValid() {
            return levels != null && !levels.isEmpty();
        }
    }

    @Data
    public static class ProjectScope {
        private String field;
        private List<Long> projectIds;
        private Boolean includeSubProject = false;

        public boolean isValid() {
            return projectIds != null && !projectIds.isEmpty();
        }
    }

    @Data
    public static class ConditionRule {
        private String field;
        private String operator;
        private Object value;
        private String operatorType;
        private List<ConditionRule> rules;
    }
}
