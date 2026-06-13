package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.RuleConflictRequest;
import com.example.datapermission.dto.RuleConflictResponse;
import com.example.datapermission.dto.RuleConflictResponse.*;
import com.example.datapermission.entity.SysOrgScope;
import com.example.datapermission.entity.SysUser;
import com.example.datapermission.enums.RulePriority;
import com.example.datapermission.mapper.SysOrgScopeMapper;
import com.example.datapermission.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleConflictService {

    private final SysOrgScopeMapper orgScopeMapper;
    private final SysOrgScopeService orgScopeService;
    private final SysUserMapper userMapper;

    public RuleConflictResponse analyzeConflicts(Long orgId) {
        RuleConflictResponse response = new RuleConflictResponse();

        List<SysOrgScope> rules = orgScopeMapper.selectList(
                new LambdaQueryWrapper<SysOrgScope>()
                        .eq(SysOrgScope::getSourceOrgId, orgId)
                        .eq(SysOrgScope::getStatus, 1)
        );

        List<RuleEvaluation> allRules = evaluateRules(rules);
        List<ConflictDetail> conflicts = detectConflicts(allRules);

        response.setHasConflict(!conflicts.isEmpty());
        response.setConflicts(conflicts);
        response.setAllRules(allRules);

        List<RuleEvaluation> effectiveRules = allRules.stream()
                .filter(RuleEvaluation::getEffective)
                .collect(Collectors.toList());
        response.setEffectiveRules(effectiveRules);

        RuleSummary summary = new RuleSummary();
        summary.setTotalRules(allRules.size());
        summary.setActiveRules((int) allRules.stream().filter(r -> "ACTIVE".equals(r.getStatus())).count());
        summary.setMatchedRules((int) allRules.stream().filter(RuleEvaluation::getMatched).count());
        summary.setEffectiveRules(effectiveRules.size());
        summary.setConflictRules(conflicts.size());
        summary.setConflictsResolved(conflicts.size());
        response.setSummary(summary);

        return response;
    }

    public RuleConflictResponse analyzeConflictsWithTypes(Long orgId, List<String> grantTypes) {
        RuleConflictResponse response = new RuleConflictResponse();

        LambdaQueryWrapper<SysOrgScope> wrapper = new LambdaQueryWrapper<SysOrgScope>()
                .eq(SysOrgScope::getSourceOrgId, orgId)
                .eq(SysOrgScope::getStatus, 1);

        if (grantTypes != null && !grantTypes.isEmpty()) {
            wrapper.in(SysOrgScope::getGrantType, grantTypes);
        }

        List<SysOrgScope> rules = orgScopeMapper.selectList(wrapper);
        List<RuleEvaluation> allRules = evaluateRules(rules);
        List<ConflictDetail> conflicts = detectConflicts(allRules);

        response.setHasConflict(!conflicts.isEmpty());
        response.setConflicts(conflicts);
        response.setAllRules(allRules);

        List<RuleEvaluation> effectiveRules = allRules.stream()
                .filter(RuleEvaluation::getEffective)
                .collect(Collectors.toList());
        response.setEffectiveRules(effectiveRules);

        return response;
    }

    public RuleConflictResponse simulateRuleChange(Long orgId, RuleConflictRequest request) {
        RuleConflictResponse response = new RuleConflictResponse();

        List<SysOrgScope> originalRules = orgScopeMapper.selectList(
                new LambdaQueryWrapper<SysOrgScope>()
                        .eq(SysOrgScope::getSourceOrgId, orgId)
                        .eq(SysOrgScope::getStatus, 1)
        );

        List<SysOrgScope> simulatedRules = new ArrayList<>(originalRules);

        if (request.getRuleChanges() != null) {
            simulatedRules = applyRuleChanges(simulatedRules, request.getRuleChanges());
        }

        List<RuleEvaluation> originalEvaluations = evaluateRules(originalRules);
        List<RuleEvaluation> simulatedEvaluations = evaluateRules(simulatedRules);

        List<String> affectedUsers = findAffectedUsers(orgId);
        List<String> scopeChanges = calculateScopeChanges(originalEvaluations, simulatedEvaluations);

        RuleSimulationResult simulationResult = new RuleSimulationResult();
        simulationResult.setChanged(!originalEvaluations.equals(simulatedEvaluations));
        simulationResult.setOriginalRules(originalEvaluations);
        simulationResult.setSimulatedRules(simulatedEvaluations);

        List<RuleEvaluation> newEffectiveRules = simulatedEvaluations.stream()
                .filter(RuleEvaluation::getEffective)
                .collect(Collectors.toList());
        if (!newEffectiveRules.isEmpty()) {
            simulationResult.setNewEffectiveRule(newEffectiveRules.get(0));
        }

        simulationResult.setAffectedUsers(affectedUsers);
        simulationResult.setScopeChanges(scopeChanges);

        response.setSimulationResult(simulationResult);
        response.setAllRules(simulatedEvaluations);
        response.setEffectiveRules(newEffectiveRules);

        return response;
    }

    private List<RuleEvaluation> evaluateRules(List<SysOrgScope> rules) {
        List<RuleEvaluation> evaluations = new ArrayList<>();

        for (SysOrgScope rule : rules) {
            RuleEvaluation evaluation = new RuleEvaluation();
            evaluation.setRuleId(rule.getId());
            evaluation.setRuleType(rule.getGrantType());
            evaluation.setRuleName(rule.getRuleName() != null ? rule.getRuleName() : getRuleName(rule.getGrantType()));
            evaluation.setPriority(rule.getPriority() != null ? rule.getPriority() : getDefaultPriority(rule.getGrantType()));
            evaluation.setMatched(true);
            evaluation.setMatchReason(generateMatchReason(rule));
            evaluation.setEffective(false);
            evaluation.setStatus("MATCHED");
            evaluations.add(evaluation);
        }

        evaluations.sort(Comparator.comparing(RuleEvaluation::getPriority));

        if (!evaluations.isEmpty()) {
            evaluations.get(0).setEffective(true);
            evaluations.get(0).setStatus("EFFECTIVE");
            evaluations.get(0).setStatusDescription("优先级最高，生效中");

            for (int i = 1; i < evaluations.size(); i++) {
                RuleEvaluation superseded = evaluations.get(i);
                RuleEvaluation supersededBy = evaluations.get(0);
                superseded.setEffective(false);
                superseded.setStatus("SUPERSEDED");
                superseded.setStatusDescription("被规则【" + supersededBy.getRuleName() + "(ID:" + supersededBy.getRuleId() + ")】覆盖");
                superseded.setSupersededBy(supersededBy.getRuleName() + "(ID:" + supersededBy.getRuleId() + ")");
            }
        }

        return evaluations;
    }

    private List<ConflictDetail> detectConflicts(List<RuleEvaluation> rules) {
        List<ConflictDetail> conflicts = new ArrayList<>();

        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                RuleEvaluation rule1 = rules.get(i);
                RuleEvaluation rule2 = rules.get(j);

                if (hasConflict(rule1.getRuleType(), rule2.getRuleType())) {
                    ConflictDetail conflict = new ConflictDetail();
                    conflict.setRule1Id(rule1.getRuleId());
                    conflict.setRule1Type(rule1.getRuleType());
                    conflict.setRule1Name(rule1.getRuleName());
                    conflict.setRule1Priority(rule1.getPriority());
                    conflict.setRule2Id(rule2.getRuleId());
                    conflict.setRule2Type(rule2.getRuleType());
                    conflict.setRule2Name(rule2.getRuleName());
                    conflict.setRule2Priority(rule2.getPriority());
                    conflict.setConflictType(determineConflictType(rule1.getRuleType(), rule2.getRuleType()));
                    conflict.setConflictDescription(generateConflictDescription(rule1, rule2));
                    conflict.setResolution(determineResolution(rule1, rule2));
                    conflicts.add(conflict);
                }
            }
        }

        return conflicts;
    }

    private boolean hasConflict(String type1, String type2) {
        Set<String> conflictPairs = Set.of(
                "HEADQUARTER_VIEW_SUB-REGION_ISOLATED",
                "REGION_ISOLATED-HEADQUARTER_VIEW_SUB",
                "HEADQUARTER_VIEW_SUB-PROJECT_TEMP",
                "REGION_ISOLATED-PROJECT_TEMP"
        );
        return conflictPairs.contains(type1 + "-" + type2);
    }

    private String determineConflictType(String type1, String type2) {
        if ("HEADQUARTER_VIEW_SUB".equals(type1) && "REGION_ISOLATED".equals(type2)) {
            return "SCOPE_OVERLAP";
        }
        if ("REGION_ISOLATED".equals(type1) && "HEADQUARTER_VIEW_SUB".equals(type2)) {
            return "SCOPE_OVERLAP";
        }
        return "PRIORITY_CONFLICT";
    }

    private String generateConflictDescription(RuleEvaluation rule1, RuleEvaluation rule2) {
        return String.format("规则【%s】与规则【%s】存在冲突，需要按优先级确定生效规则",
                rule1.getRuleName(), rule2.getRuleName());
    }

    private String determineResolution(RuleEvaluation rule1, RuleEvaluation rule2) {
        if (rule1.getPriority() < rule2.getPriority()) {
            return "规则【" + rule1.getRuleName() + "】优先级更高，取其作为最终生效规则";
        } else {
            return "规则【" + rule2.getRuleName() + "】优先级更高，取其作为最终生效规则";
        }
    }

    private List<SysOrgScope> applyRuleChanges(List<SysOrgScope> rules, Map<String, Object> changes) {
        List<SysOrgScope> result = new ArrayList<>(rules);

        for (SysOrgScope rule : result) {
            String key = "rule_" + rule.getId();
            if (changes.containsKey(key)) {
                Map<String, Object> change = (Map<String, Object>) changes.get(key);
                if (change.containsKey("priority")) {
                    rule.setPriority((Integer) change.get("priority"));
                }
                if (change.containsKey("status")) {
                    rule.setStatus((Integer) change.get("status"));
                }
                if (change.containsKey("hierarchyDepth")) {
                    rule.setHierarchyDepth((Integer) change.get("hierarchyDepth"));
                }
            }
        }

        return result;
    }

    private List<String> findAffectedUsers(Long orgId) {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getOrgId, orgId)
                        .eq(SysUser::getStatus, 1)
        );
        return users.stream().map(SysUser::getUsername).collect(Collectors.toList());
    }

    private List<String> calculateScopeChanges(List<RuleEvaluation> original, List<RuleEvaluation> simulated) {
        List<String> changes = new ArrayList<>();

        RuleEvaluation originalEffective = original.stream()
                .filter(RuleEvaluation::getEffective)
                .findFirst().orElse(null);

        RuleEvaluation simulatedEffective = simulated.stream()
                .filter(RuleEvaluation::getEffective)
                .findFirst().orElse(null);

        if (originalEffective != null && simulatedEffective != null) {
            if (!originalEffective.getRuleId().equals(simulatedEffective.getRuleId())) {
                changes.add("生效规则从【" + originalEffective.getRuleName() + "】变更为【" + simulatedEffective.getRuleName() + "】");
            }
            if (!originalEffective.getPriority().equals(simulatedEffective.getPriority())) {
                changes.add("规则优先级从" + originalEffective.getPriority() + "变更为" + simulatedEffective.getPriority());
            }
        } else if (simulatedEffective != null) {
            changes.add("新增生效规则【" + simulatedEffective.getRuleName() + "】");
        } else if (originalEffective != null) {
            changes.add("移除生效规则【" + originalEffective.getRuleName() + "】");
        }

        return changes;
    }

    private String getRuleName(String grantType) {
        switch (grantType) {
            case "HEADQUARTER_VIEW_SUB": return "总部查看下级组织";
            case "REGION_ISOLATED": return "区域数据隔离";
            case "PROJECT_TEMP": return "项目成员临时可见";
            default: return "组织范围规则";
        }
    }

    private Integer getDefaultPriority(String grantType) {
        switch (grantType) {
            case "PRIVILEGED": return RulePriority.PRIVILEGED.getPriority();
            case "DENY": return RulePriority.DENY.getPriority();
            case "PROJECT_TEMP": return RulePriority.PROJECT_TEMP.getPriority();
            case "MANUAL": return RulePriority.MANUAL.getPriority();
            case "AUTO": return RulePriority.AUTO.getPriority();
            default: return RulePriority.ORG_SCOPE.getPriority();
        }
    }

    private String generateMatchReason(SysOrgScope rule) {
        if ("HEADQUARTER_VIEW_SUB".equals(rule.getGrantType())) {
            Integer depth = rule.getHierarchyDepth();
            return String.format("总部可查看%d级以内下级组织", depth != null ? depth : 999);
        }
        if ("REGION_ISOLATED".equals(rule.getGrantType())) {
            return "区域间数据隔离规则生效";
        }
        return "规则匹配";
    }
}
