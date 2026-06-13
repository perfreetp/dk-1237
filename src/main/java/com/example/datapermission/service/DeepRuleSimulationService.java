package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.RuleSimulationRequest;
import com.example.datapermission.dto.RuleSimulationRequest.*;
import com.example.datapermission.dto.RuleSimulationResponse;
import com.example.datapermission.dto.RuleSimulationResponse.*;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepRuleSimulationService {

    private final SysUserMapper userMapper;
    private final SysUserPermissionMapper permissionMapper;
    private final SysOrgScopeMapper orgScopeMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysResourceMapper resourceMapper;
    private final SysSensitiveFieldMapper sensitiveFieldMapper;
    private final SysPermissionTemplateMapper permissionTemplateMapper;

    public RuleSimulationResponse simulateRules(RuleSimulationRequest request) {
        try {
            SysUser user = userMapper.selectById(request.getUserId());
            if (user == null) {
                return buildErrorResponse("用户不存在");
            }

            SysResource resource = resourceMapper.selectOne(
                    new LambdaQueryWrapper<SysResource>()
                            .eq(SysResource::getResourceCode, request.getResourceCode())
            );
            if (resource == null) {
                return buildErrorResponse("资源不存在");
            }

            List<SysUserPermission> existingPermissions = getUserPermissions(request.getUserId(), request.getResourceCode());
            PermissionPreview preview = buildPermissionPreview(user, resource, existingPermissions, request);

            List<String> warnings = checkRuleConflicts(existingPermissions, request);
            List<String> denialReasons = buildDenialReasons(existingPermissions, request);

            List<AppliedRule> appliedRules = buildAppliedRules(existingPermissions, request);
            List<ConflictingRule> conflicts = detectConflicts(existingPermissions, request);

            Map<String, Object> evaluationDetails = new HashMap<>();
            evaluationDetails.put("appliedRules", appliedRules);
            evaluationDetails.put("conflicts", conflicts);
            evaluationDetails.put("finalDecision", determineFinalDecision(denialReasons, appliedRules));

            RuleSimulationResponse.SimulationStatus status = determineSimulationStatus(denialReasons, appliedRules);

            return RuleSimulationResponse.builder()
                    .status(status)
                    .userId(request.getUserId())
                    .businessScenario(request.getBusinessScenario())
                    .resourceCode(request.getResourceCode())
                    .preview(preview)
                    .warnings(warnings)
                    .denialReasons(denialReasons)
                    .ruleEvaluationDetails(evaluationDetails)
                    .build();

        } catch (Exception e) {
            log.error("规则模拟失败", e);
            return buildErrorResponse("规则模拟失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> saveSimulatedRules(RuleSimulationRequest request) {
        RuleSimulationResponse simulation = simulateRules(request);

        if (simulation.getStatus() == RuleSimulationResponse.SimulationStatus.FULL_DENIED) {
            return Map.of("success", false, "message", "模拟结果显示权限被完全拒绝，无法保存");
        }

        if (simulation.getStatus() == RuleSimulationResponse.SimulationStatus.ERROR) {
            return Map.of("success", false, "message", "模拟过程出错，请检查输入参数");
        }

        List<SysUserPermission> updatedPermissions = applySimulationToPermissions(
                request.getUserId(),
                request.getResourceCode(),
                simulation.getPreview(),
                request.getTempAdjustments()
        );

        return Map.of(
                "success", true,
                "message", "规则模拟已保存",
                "updatedPermissions", updatedPermissions.size(),
                "simulationId", UUID.randomUUID().toString()
        );
    }

    private PermissionPreview buildPermissionPreview(SysUser user, SysResource resource,
                                                     List<SysUserPermission> existingPermissions,
                                                     RuleSimulationRequest request) {
        List<OrgPreview> visibleOrgs = buildOrgPreview(user, request);
        List<ProjectPreview> visibleProjects = buildProjectPreview(existingPermissions, request);
        List<String> visibleFields = buildVisibleFields(user, resource, existingPermissions, request);
        List<String> maskedFields = buildMaskedFields(resource, existingPermissions, request);
        List<String> deniedFields = buildDeniedFields(resource, existingPermissions, request);
        SqlScopePreview sqlScope = buildSqlScopePreview(existingPermissions, request);
        FieldAccessLevelSummary fieldAccessLevel = buildFieldAccessLevelSummary(existingPermissions, request);

        return PermissionPreview.builder()
                .visibleOrgs(visibleOrgs)
                .visibleProjects(visibleProjects)
                .visibleFields(visibleFields)
                .maskedFields(maskedFields)
                .deniedFields(deniedFields)
                .sqlScope(sqlScope)
                .fieldAccessLevel(fieldAccessLevel)
                .build();
    }

    private List<OrgPreview> buildOrgPreview(SysUser user, RuleSimulationRequest request) {
        List<OrgPreview> previews = new ArrayList<>();

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getOrgScope() != null) {
            OrgScopeAdjustment adjustment = request.getTempAdjustments().getOrgScope();

            if (adjustment.getIncludeOrgIds() != null) {
                for (Long orgId : adjustment.getIncludeOrgIds()) {
                    SysOrganization org = organizationMapper.selectById(orgId);
                    if (org != null) {
                        previews.add(OrgPreview.builder()
                                .orgId(orgId)
                                .orgName(org.getOrgName())
                                .orgType(org.getOrgType())
                                .accessLevel("INCLUDE")
                                .inclusionReason("临时调整-包含")
                                .build());
                    }
                }
            }

            if (adjustment.getExcludeOrgIds() != null) {
                for (Long orgId : adjustment.getExcludeOrgIds()) {
                    SysOrganization org = organizationMapper.selectById(orgId);
                    if (org != null) {
                        previews.add(OrgPreview.builder()
                                .orgId(orgId)
                                .orgName(org.getOrgName())
                                .orgType(org.getOrgType())
                                .accessLevel("EXCLUDE")
                                .inclusionReason("临时调整-排除")
                                .build());
                    }
                }
            }

            if (adjustment.getHierarchyDepth() != null) {
                previews.addAll(buildHierarchyOrgs(user.getOrgId(), adjustment.getHierarchyDepth()));
            }
        } else {
            previews.addAll(buildDefaultOrgScopes(user, request));
        }

        return previews;
    }

    private List<OrgPreview> buildHierarchyOrgs(Long rootOrgId, int depth) {
        List<OrgPreview> previews = new ArrayList<>();

        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootOrgId);
        int currentDepth = 0;

        while (!queue.isEmpty() && currentDepth < depth) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                Long orgId = queue.poll();
                SysOrganization org = organizationMapper.selectById(orgId);
                if (org != null) {
                    previews.add(OrgPreview.builder()
                            .orgId(orgId)
                            .orgName(org.getOrgName())
                            .orgType(org.getOrgType())
                            .accessLevel("HIERARCHY")
                            .inclusionReason("层级深度: " + (currentDepth + 1))
                            .build());

                    List<SysOrganization> children = organizationMapper.selectList(
                            new LambdaQueryWrapper<SysOrganization>()
                                    .eq(SysOrganization::getParentId, orgId)
                    );
                    children.forEach(child -> queue.offer(child.getId()));
                }
            }
            currentDepth++;
        }

        return previews;
    }

    private List<OrgPreview> buildDefaultOrgScopes(SysUser user, RuleSimulationRequest request) {
        List<OrgPreview> previews = new ArrayList<>();

        LambdaQueryWrapper<SysOrgScope> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOrgScope::getSourceOrgId, user.getOrgId());
        wrapper.eq(SysOrgScope::getStatus, 1);
        wrapper.orderByAsc(SysOrgScope::getPriority);

        List<SysOrgScope> scopes = orgScopeMapper.selectList(wrapper);

        for (SysOrgScope scope : scopes) {
            SysOrganization org = organizationMapper.selectById(scope.getTargetOrgId());
            if (org != null) {
                previews.add(OrgPreview.builder()
                        .orgId(scope.getTargetOrgId())
                        .orgName(org.getOrgName())
                        .orgType(scope.getTargetOrgType())
                        .accessLevel(scope.getGrantType())
                        .inclusionReason(scope.getRuleName())
                        .build());
            }
        }

        return previews;
    }

    private List<ProjectPreview> buildProjectPreview(List<SysUserPermission> permissions,
                                                      RuleSimulationRequest request) {
        List<ProjectPreview> previews = new ArrayList<>();

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getProject() != null) {
            ProjectAdjustment adjustment = request.getTempAdjustments().getProject();

            if (adjustment.getIncludeProjectIds() != null) {
                for (Long projectId : adjustment.getIncludeProjectIds()) {
                    previews.add(ProjectPreview.builder()
                            .projectId(projectId)
                            .projectName("项目-" + projectId)
                            .projectType("CUSTOM")
                            .accessLevel("FULL")
                            .isIncluded(true)
                            .build());
                }
            }

            if (adjustment.getExcludeProjectIds() != null) {
                for (Long projectId : adjustment.getExcludeProjectIds()) {
                    previews.add(ProjectPreview.builder()
                            .projectId(projectId)
                            .projectName("项目-" + projectId)
                            .projectType("CUSTOM")
                            .accessLevel("DENIED")
                            .isIncluded(false)
                            .build());
                }
            }
        } else {
            Set<Long> projectIds = permissions.stream()
                    .map(p -> {
                        if (StringUtils.hasText(p.getOrgScopeValue())) {
                            try {
                                JSONArray arr = JSON.parseArray(p.getOrgScopeValue());
                                return arr.stream()
                                        .mapToLong(v -> ((Number) v).longValue())
                                        .boxed()
                                        .collect(Collectors.toSet());
                            } catch (Exception e) {
                                return Collections.<Long>emptySet();
                            }
                        }
                        return Collections.<Long>emptySet();
                    })
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

            for (Long projectId : projectIds) {
                previews.add(ProjectPreview.builder()
                        .projectId(projectId)
                        .projectName("项目-" + projectId)
                        .projectType("PERMISSION")
                        .accessLevel("READ")
                        .isIncluded(true)
                        .build());
            }
        }

        return previews;
    }

    private List<String> buildVisibleFields(SysUser user, SysResource resource,
                                            List<SysUserPermission> permissions,
                                            RuleSimulationRequest request) {
        Set<String> visibleFields = new HashSet<>();

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getField() != null) {
            FieldAdjustment fieldAdj = request.getTempAdjustments().getField();
            if (fieldAdj.getAdditionalVisibleFields() != null) {
                visibleFields.addAll(fieldAdj.getAdditionalVisibleFields());
            }
        }

        for (SysUserPermission perm : permissions) {
            if (perm.getFieldAccessLevel() != null && perm.getFieldAccessLevel() >= 1) {
                visibleFields.add("basic_info");
            }
            if (perm.getFieldAccessLevel() != null && perm.getFieldAccessLevel() >= 3) {
                visibleFields.add("contact_info");
                visibleFields.add("financial_info");
            }
            if (perm.getFieldAccessLevel() != null && perm.getFieldAccessLevel() >= 5) {
                visibleFields.add("sensitive_info");
            }
        }

        return new ArrayList<>(visibleFields);
    }

    private List<String> buildMaskedFields(SysResource resource, List<SysUserPermission> permissions,
                                           RuleSimulationRequest request) {
        Set<String> maskedFields = new HashSet<>();

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getField() != null) {
            FieldAdjustment fieldAdj = request.getTempAdjustments().getField();
            if (fieldAdj.getAdditionalMaskedFields() != null) {
                maskedFields.addAll(fieldAdj.getAdditionalMaskedFields());
            }
        }

        int maxLevel = permissions.stream()
                .mapToInt(p -> p.getFieldAccessLevel() != null ? p.getFieldAccessLevel() : 1)
                .max()
                .orElse(1);

        int targetLevel = request.getTempAdjustments() != null &&
                request.getTempAdjustments().getField() != null &&
                request.getTempAdjustments().getField().getTemporaryDesensitizationLevel() != null ?
                request.getTempAdjustments().getField().getTemporaryDesensitizationLevel() : maxLevel;

        if (targetLevel < 3) {
            maskedFields.add("phone");
            maskedFields.add("email");
        }
        if (targetLevel < 5) {
            maskedFields.add("id_card");
            maskedFields.add("bank_account");
        }

        return new ArrayList<>(maskedFields);
    }

    private List<String> buildDeniedFields(SysResource resource, List<SysUserPermission> permissions,
                                            RuleSimulationRequest request) {
        List<String> deniedFields = new ArrayList<>();

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getField() != null) {
            FieldAdjustment fieldAdj = request.getTempAdjustments().getField();
            if (fieldAdj.getRemovedVisibleFields() != null) {
                deniedFields.addAll(fieldAdj.getRemovedVisibleFields());
            }
        }

        int maxLevel = permissions.stream()
                .mapToInt(p -> p.getFieldAccessLevel() != null ? p.getFieldAccessLevel() : 1)
                .max()
                .orElse(1);

        if (maxLevel < 5) {
            if (!deniedFields.contains("salary")) {
                deniedFields.add("salary");
            }
        }

        return deniedFields;
    }

    private SqlScopePreview buildSqlScopePreview(List<SysUserPermission> permissions,
                                                  RuleSimulationRequest request) {
        List<String> allowedConditions = new ArrayList<>();
        List<String> deniedConditions = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder("1=1");

        for (SysUserPermission perm : permissions) {
            if (StringUtils.hasText(perm.getOrgScopeValue())) {
                try {
                    JSONArray scopeValues = JSON.parseArray(perm.getOrgScopeValue());
                    for (Object val : scopeValues) {
                        allowedConditions.add("org_id = " + val);
                        whereClause.append(" AND org_id IN (").append(val).append(")");
                    }
                } catch (Exception e) {
                    log.warn("解析orgScopeValue失败", e);
                }
            }

            if ("READ".equals(perm.getOperationTypes()) || "READ,WRITE".equals(perm.getOperationTypes())) {
                deniedConditions.add("DELETE operation");
                whereClause.append(" AND operation_type NOT IN ('DELETE')");
            }
        }

        return SqlScopePreview.builder()
                .allowedConditions(allowedConditions)
                .deniedConditions(deniedConditions)
                .optimizedWhereClause(whereClause.toString())
                .parameterValues(Map.of(
                        "userId", request.getUserId(),
                        "resourceCode", request.getResourceCode(),
                        "businessScenario", request.getBusinessScenario()
                ))
                .build();
    }

    private FieldAccessLevelSummary buildFieldAccessLevelSummary(List<SysUserPermission> permissions,
                                                                   RuleSimulationRequest request) {
        int effectiveLevel = 1;

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getField() != null) {
            FieldAdjustment fieldAdj = request.getTempAdjustments().getField();
            if (fieldAdj.getTemporaryDesensitizationLevel() != null) {
                effectiveLevel = fieldAdj.getTemporaryDesensitizationLevel();
            }
        }

        int maxPermLevel = permissions.stream()
                .mapToInt(p -> p.getFieldAccessLevel() != null ? p.getFieldAccessLevel() : 1)
                .max()
                .orElse(1);

        effectiveLevel = Math.max(effectiveLevel, maxPermLevel);

        List<String> fieldGroups = new ArrayList<>();
        if (effectiveLevel >= 1) fieldGroups.add("basic_info");
        if (effectiveLevel >= 2) fieldGroups.add("extended_info");
        if (effectiveLevel >= 3) fieldGroups.add("contact_info");
        if (effectiveLevel >= 4) fieldGroups.add("financial_info");
        if (effectiveLevel >= 5) fieldGroups.add("sensitive_info");

        Map<String, Integer> fieldSpecificLevels = new HashMap<>();
        fieldSpecificLevels.put("basic_info", 1);
        fieldSpecificLevels.put("contact_info", 3);
        fieldSpecificLevels.put("financial_info", 4);
        fieldSpecificLevels.put("sensitive_info", 5);

        return FieldAccessLevelSummary.builder()
                .effectiveLevel(effectiveLevel)
                .fieldGroups(fieldGroups)
                .fieldSpecificLevels(fieldSpecificLevels)
                .build();
    }

    private List<String> checkRuleConflicts(List<SysUserPermission> permissions, RuleSimulationRequest request) {
        List<String> warnings = new ArrayList<>();

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getOrgScope() != null) {
            OrgScopeAdjustment orgAdj = request.getTempAdjustments().getOrgScope();
            if (orgAdj.getIncludeOrgIds() != null && orgAdj.getExcludeOrgIds() != null) {
                Set<Long> intersection = new HashSet<>(orgAdj.getIncludeOrgIds());
                intersection.retainAll(orgAdj.getExcludeOrgIds());
                if (!intersection.isEmpty()) {
                    warnings.add("组织包含和排除列表存在重叠: " + intersection);
                }
            }
        }

        long highLevelCount = permissions.stream()
                .filter(p -> p.getFieldAccessLevel() != null && p.getFieldAccessLevel() >= 5)
                .count();
        if (highLevelCount > 3) {
            warnings.add("该用户拥有" + highLevelCount + "个高级别权限，存在权限扩散风险");
        }

        return warnings;
    }

    private List<String> buildDenialReasons(List<SysUserPermission> permissions, RuleSimulationRequest request) {
        List<String> reasons = new ArrayList<>();

        if (permissions.isEmpty()) {
            reasons.add("用户对该资源没有任何权限");
        }

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getField() != null) {
            FieldAdjustment fieldAdj = request.getTempAdjustments().getField();
            if (fieldAdj.getRemovedVisibleFields() != null && !fieldAdj.getRemovedVisibleFields().isEmpty()) {
                reasons.add("以下字段被明确拒绝访问: " + fieldAdj.getRemovedVisibleFields());
            }
        }

        if (request.getTempAdjustments() != null && request.getTempAdjustments().getOrgScope() != null) {
            OrgScopeAdjustment orgAdj = request.getTempAdjustments().getOrgScope();
            if (orgAdj.getExcludeOrgIds() != null && !orgAdj.getExcludeOrgIds().isEmpty()) {
                reasons.add("以下组织被明确排除: " + orgAdj.getExcludeOrgIds());
            }
        }

        return reasons;
    }

    private List<AppliedRule> buildAppliedRules(List<SysUserPermission> permissions, RuleSimulationRequest request) {
        List<AppliedRule> rules = new ArrayList<>();

        for (SysUserPermission perm : permissions) {
            String ruleType = determineRuleType(perm);
            int priority = determineRulePriority(perm);

            rules.add(AppliedRule.builder()
                    .ruleId("RULE-" + perm.getId())
                    .ruleName(getRuleName(perm))
                    .ruleType(ruleType)
                    .priority(priority)
                    .matchedCondition("user_id = " + perm.getUserId() + " AND resource_id = " + perm.getResourceId())
                    .effect("允许访问" + perm.getOperationTypes() + "操作")
                    .build());
        }

        if (request.getTempAdjustments() != null) {
            rules.add(AppliedRule.builder()
                    .ruleId("RULE-TEMP-ADJUST")
                    .ruleName("临时调整规则")
                    .ruleType("TEMP_ADJUSTMENT")
                    .priority(50)
                    .matchedCondition("手动调整")
                    .effect("临时修改权限范围")
                    .build());
        }

        rules.sort(Comparator.comparingInt(AppliedRule::getPriority));
        return rules;
    }

    private List<ConflictingRule> detectConflicts(List<SysUserPermission> permissions, RuleSimulationRequest request) {
        return new ArrayList<>();
    }

    private String determineFinalDecision(List<String> denialReasons, List<AppliedRule> rules) {
        if (!denialReasons.isEmpty() && rules.isEmpty()) {
            return "DENY";
        }
        if (denialReasons.isEmpty() && !rules.isEmpty()) {
            return "ALLOW";
        }
        if (!denialReasons.isEmpty() && !rules.isEmpty()) {
            return "PARTIAL_ALLOW";
        }
        return "NO_RULES";
    }

    private RuleSimulationResponse.SimulationStatus determineSimulationStatus(List<String> denialReasons,
                                                                                List<AppliedRule> rules) {
        if (denialReasons.isEmpty() && !rules.isEmpty()) {
            return RuleSimulationResponse.SimulationStatus.SUCCESS;
        }
        if (!denialReasons.isEmpty() && !rules.isEmpty()) {
            return RuleSimulationResponse.SimulationStatus.PARTIAL_SUCCESS;
        }
        if (!denialReasons.isEmpty() && rules.isEmpty()) {
            return RuleSimulationResponse.SimulationStatus.FULL_DENIED;
        }
        return RuleSimulationResponse.SimulationStatus.ERROR;
    }

    private List<SysUserPermission> getUserPermissions(Long userId, String resourceCode) {
        List<SysUserPermission> permissions = new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(SysUserPermission::getUserId, userId);
        permWrapper.eq(SysUserPermission::getStatus, 1);

        if (StringUtils.hasText(resourceCode)) {
            SysResource resource = resourceMapper.selectOne(
                    new LambdaQueryWrapper<SysResource>()
                            .eq(SysResource::getResourceCode, resourceCode)
            );
            if (resource != null) {
                permWrapper.eq(SysUserPermission::getResourceId, resource.getId());
            }
        }

        permissions.addAll(permissionMapper.selectList(permWrapper));
        return permissions;
    }

    private List<SysUserPermission> applySimulationToPermissions(Long userId, String resourceCode,
                                                                   PermissionPreview preview,
                                                                   TempRuleAdjustments adjustments) {
        List<SysUserPermission> updatedPermissions = new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getUserId, userId);
        wrapper.eq(SysUserPermission::getStatus, 1);

        if (StringUtils.hasText(resourceCode)) {
            SysResource resource = resourceMapper.selectOne(
                    new LambdaQueryWrapper<SysResource>()
                            .eq(SysResource::getResourceCode, resourceCode)
            );
            if (resource != null) {
                wrapper.eq(SysUserPermission::getResourceId, resource.getId());
            }
        }

        List<SysUserPermission> existingPerms = permissionMapper.selectList(wrapper);

        for (SysUserPermission perm : existingPerms) {
            if (adjustments != null && adjustments.getOrgScope() != null) {
                List<Long> includeOrgIds = adjustments.getOrgScope().getIncludeOrgIds();
                if (includeOrgIds != null && !includeOrgIds.isEmpty()) {
                    perm.setOrgScopeValue(JSON.toJSONString(includeOrgIds));
                    perm.setOrgScopeType("CUSTOM");
                }
            }

            if (adjustments != null && adjustments.getField() != null) {
                if (adjustments.getField().getTemporaryDesensitizationLevel() != null) {
                    perm.setFieldAccessLevel(adjustments.getField().getTemporaryDesensitizationLevel());
                }
                perm.setDesensitizationEnabled(1);
            }

            perm.setUpdatedTime(LocalDateTime.now());
            permissionMapper.updateById(perm);
            updatedPermissions.add(perm);
        }

        return updatedPermissions;
    }

    private String determineRuleType(SysUserPermission perm) {
        if ("AUTO".equals(perm.getGrantType())) return "AUTO_SCOPE";
        if ("MANUAL".equals(perm.getGrantType())) return "MANUAL_GRANT";
        if (perm.getPermissionTemplateId() != null) return "TEMPLATE";
        return "CUSTOM";
    }

    private int determineRulePriority(SysUserPermission perm) {
        switch (perm.getGrantType()) {
            case "PRIVILEGED": return 100;
            case "DENY": return 200;
            case "PROJECT_TEMP": return 300;
            case "MANUAL": return 400;
            case "POST_TEMPLATE": return 500;
            case "ORG_SCOPE": return 600;
            case "AUTO": return 700;
            default: return 500;
        }
    }

    private String getRuleName(SysUserPermission perm) {
        switch (perm.getGrantType()) {
            case "AUTO": return "自动授权";
            case "MANUAL": return "手动授权";
            case "POST_TEMPLATE": return "岗位模板";
            case "ORG_SCOPE": return "组织范围";
            default: return "自定义规则";
        }
    }

    private RuleSimulationResponse buildErrorResponse(String message) {
        return RuleSimulationResponse.builder()
                .status(RuleSimulationResponse.SimulationStatus.ERROR)
                .warnings(List.of(message))
                .build();
    }
}
