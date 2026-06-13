package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RuleSimulationRequest {
    private Long userId;
    private String businessScenario;
    private String resourceCode;
    private TempRuleAdjustments tempAdjustments;
    private String previewMode;

    @Data
    public static class TempRuleAdjustments {
        private OrgScopeAdjustment orgScope;
        private ProjectAdjustment project;
        private FieldAdjustment field;
    }

    @Data
    public static class OrgScopeAdjustment {
        private String scopeType;
        private List<Long> includeOrgIds;
        private List<Long> excludeOrgIds;
        private Integer hierarchyDepth;
    }

    @Data
    public static class ProjectAdjustment {
        private List<Long> includeProjectIds;
        private List<Long> excludeProjectIds;
        private Integer maxProjectCount;
    }

    @Data
    public static class FieldAdjustment {
        private List<String> additionalVisibleFields;
        private List<String> removedVisibleFields;
        private List<String> additionalMaskedFields;
        private Integer temporaryDesensitizationLevel;
    }
}
