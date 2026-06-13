package com.example.datapermission.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class RuleSimulationResponse {
    private SimulationStatus status;
    private Long userId;
    private String businessScenario;
    private String resourceCode;
    private PermissionPreview preview;
    private List<String> warnings;
    private List<String> denialReasons;
    private Map<String, Object> ruleEvaluationDetails;

    @Data
    @Builder
    public static class PermissionPreview {
        private List<OrgPreview> visibleOrgs;
        private List<ProjectPreview> visibleProjects;
        private List<String> visibleFields;
        private List<String> maskedFields;
        private List<String> deniedFields;
        private SqlScopePreview sqlScope;
        private FieldAccessLevelSummary fieldAccessLevel;
    }

    @Data
    @Builder
    public static class OrgPreview {
        private Long orgId;
        private String orgName;
        private String orgType;
        private String accessLevel;
        private String inclusionReason;
    }

    @Data
    @Builder
    public static class ProjectPreview {
        private Long projectId;
        private String projectName;
        private String projectType;
        private String accessLevel;
        private Boolean isIncluded;
    }

    @Data
    @Builder
    public static class SqlScopePreview {
        private List<String> allowedConditions;
        private List<String> deniedConditions;
        private String optimizedWhereClause;
        private Map<String, Object> parameterValues;
    }

    @Data
    @Builder
    public static class FieldAccessLevelSummary {
        private Integer effectiveLevel;
        private List<String> fieldGroups;
        private Map<String, Integer> fieldSpecificLevels;
    }

    @Data
    @Builder
    public static class RuleEvaluationDetails {
        private List<AppliedRule> appliedRules;
        private List<ConflictingRule> conflicts;
        private String finalDecision;
    }

    @Data
    @Builder
    public static class AppliedRule {
        private String ruleId;
        private String ruleName;
        private String ruleType;
        private Integer priority;
        private String matchedCondition;
        private String effect;
    }

    @Data
    @Builder
    public static class ConflictingRule {
        private String rule1Id;
        private String rule2Id;
        private String conflictType;
        private String resolution;
    }

    public enum SimulationStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FULL_DENIED,
        ERROR
    }
}
