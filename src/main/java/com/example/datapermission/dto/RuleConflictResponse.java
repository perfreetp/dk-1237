package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class RuleConflictResponse {

    private Boolean hasConflict;

    private List<ConflictDetail> conflicts;

    private List<RuleEvaluation> allRules;

    private List<RuleEvaluation> effectiveRules;

    private RuleSimulationResult simulationResult;

    private RuleSummary summary;

    @Data
    public static class ConflictDetail {
        private Long rule1Id;
        private String rule1Type;
        private String rule1Name;
        private Integer rule1Priority;
        private Long rule2Id;
        private String rule2Type;
        private String rule2Name;
        private Integer rule2Priority;
        private String conflictType;
        private String conflictDescription;
        private String resolution;
    }

    @Data
    public static class RuleEvaluation {
        private Long ruleId;
        private String ruleType;
        private String ruleName;
        private Integer priority;
        private Boolean matched;
        private String matchReason;
        private Boolean effective;
        private String status;
        private String statusDescription;
        private String supersededBy;
    }

    @Data
    public static class RuleSimulationResult {
        private Boolean changed;
        private List<RuleEvaluation> originalRules;
        private List<RuleEvaluation> simulatedRules;
        private RuleEvaluation newEffectiveRule;
        private List<String> affectedUsers;
        private List<String> scopeChanges;
    }

    @Data
    public static class RuleSummary {
        private Integer totalRules;
        private Integer activeRules;
        private Integer matchedRules;
        private Integer effectiveRules;
        private Integer conflictRules;
        private Integer conflictsResolved;
    }
}
