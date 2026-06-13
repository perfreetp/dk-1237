package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.EnhancedAccessCheckRequest;
import com.example.datapermission.dto.EnhancedAccessCheckResponse;
import com.example.datapermission.dto.EnhancedAccessCheckResponse.*;
import com.example.datapermission.entity.*;
import com.example.datapermission.enums.AccessDecision;
import com.example.datapermission.enums.RulePriority;
import com.example.datapermission.mapper.*;
import com.example.datapermission.util.DesensitizationUtil;
import com.example.datapermission.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedAccessCheckService {

    private final SysUserService userService;
    private final SysResourceService resourceService;
    private final SysSensitiveFieldService sensitiveFieldService;
    private final SysUserPermissionService userPermissionService;
    private final SysOrgScopeService orgScopeService;
    private final SysPermissionTemplateService templateService;
    private final SysAccessLogMapper accessLogMapper;
    private final SysAnomalyAlertMapper anomalyAlertMapper;
    private final DesensitizationUtil desensitizationUtil;

    public EnhancedAccessCheckResponse checkAccess(EnhancedAccessCheckRequest request) {
        long startTime = System.currentTimeMillis();
        EnhancedAccessCheckResponse response = new EnhancedAccessCheckResponse();

        try {
            SysUser user = userService.getById(request.getUserId());
            SysResource resource = resourceService.getByCode(request.getResourceCode());

            List<SysUserPermission> permissions = getEffectivePermissions(user.getId(), resource.getId());
            List<SysOrgScope> orgScopes = orgScopeService.getActiveBySourceOrgId(user.getOrgId());

            List<AppliedRule> appliedRules = buildAppliedRules(user, resource, permissions, orgScopes);
            response.setAppliedRules(appliedRules);

            AppliedRule effectiveRule = determineEffectiveRule(appliedRules);
            response.setEffectiveRule(effectiveRule);

            boolean hasOperationPermission = checkOperationPermission(permissions, request.getOperationType());
            if (!hasOperationPermission) {
                response.setAccessDecision(AccessDecision.DENY.name());
                response.setAllowed(false);
                response.setDeniedReason("您没有" + request.getOperationType() + "该资源的权限");
                response.setSuggestions(List.of("请联系管理员申请权限", "/permission/apply?resourceCode=" + request.getResourceCode()));
                return response;
            }

            AccessScope accessScope = calculateAccessScope(user, resource, permissions, orgScopes, request);
            response.setAccessibleScope(accessScope);

            List<FieldPermission> fieldPermissions = checkFieldPermissions(request, resource, permissions);
            response.setFieldPermissions(fieldPermissions);

            SqlFilters sqlFilters = buildSqlFilters(request, accessScope);
            response.setSqlFilters(sqlFilters);

            ApplyPermission applyPermission = buildApplyPermission(fieldPermissions);
            response.setApplyPermission(applyPermission);

            List<String> hiddenFields = fieldPermissions.stream()
                    .filter(fp -> !fp.getAllowed() && !fp.getMasked())
                    .map(FieldPermission::getField)
                    .collect(Collectors.toList());

            List<FieldPermission> maskedFields = fieldPermissions.stream()
                    .filter(FieldPermission::getMasked)
                    .collect(Collectors.toList());

            if (!hiddenFields.isEmpty() || !maskedFields.isEmpty()) {
                response.setAccessDecision(AccessDecision.PARTIAL.name());
                response.setAllowed(true);
                response.setDeniedReason("部分字段无访问权限: " + String.join(", ", hiddenFields));
            } else {
                response.setAccessDecision(AccessDecision.ALLOW.name());
                response.setAllowed(true);
            }

            if ("EXPORT".equals(request.getOperationType())) {
                checkExportAlert(user.getId());
            }

            saveAccessLog(request, resource, AccessDecision.valueOf(response.getAccessDecision()),
                    response.getDeniedReason(), System.currentTimeMillis() - startTime);

            response.setExecutionTime(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            log.error("增强版访问校验异常", e);
            response.setAccessDecision(AccessDecision.DENY.name());
            response.setAllowed(false);
            response.setDeniedReason("系统错误: " + e.getMessage());
            response.setExecutionTime(System.currentTimeMillis() - startTime);
            return response;
        }
    }

    private List<SysUserPermission> getEffectivePermissions(Long userId, Long resourceId) {
        List<SysUserPermission> permissions = userPermissionService.getByUserIdAndResourceId(userId, resourceId);
        LocalDateTime now = LocalDateTime.now();
        return permissions.stream()
                .filter(p -> p.getStatus() == 1)
                .filter(p -> p.getStartTime() == null || !p.getStartTime().isAfter(now))
                .filter(p -> p.getEndTime() == null || !p.getEndTime().isBefore(now))
                .toList();
    }

    private List<AppliedRule> buildAppliedRules(SysUser user, SysResource resource,
                                               List<SysUserPermission> permissions, List<SysOrgScope> orgScopes) {
        List<AppliedRule> rules = new ArrayList<>();

        for (SysOrgScope scope : orgScopes) {
            AppliedRule rule = new AppliedRule();
            rule.setRuleId(scope.getId());
            rule.setRuleType(scope.getGrantType());
            rule.setRuleName(scope.getRuleName() != null ? scope.getRuleName() : getRuleName(scope.getGrantType()));
            rule.setPriority(scope.getPriority() != null ? scope.getPriority() : getDefaultPriority(scope.getGrantType()));
            rule.setMatched(isRuleMatched(user, scope));
            rule.setMatchReason(generateMatchReason(user, scope));
            rules.add(rule);
        }

        for (SysUserPermission perm : permissions) {
            AppliedRule rule = new AppliedRule();
            rule.setRuleId(perm.getId());
            rule.setRuleType(perm.getGrantType());
            rule.setRuleName("用户直接授权");
            rule.setPriority(getDefaultPriority(perm.getGrantType()));
            rule.setMatched(true);
            rule.setMatchReason("用户拥有该资源的直接授权");
            rules.add(rule);
        }

        return rules.stream()
                .sorted(Comparator.comparing(AppliedRule::getPriority))
                .collect(Collectors.toList());
    }

    private AppliedRule determineEffectiveRule(List<AppliedRule> appliedRules) {
        if (appliedRules.isEmpty()) {
            return null;
        }

        return appliedRules.stream()
                .filter(AppliedRule::getMatched)
                .findFirst()
                .orElse(null);
    }

    private boolean isRuleMatched(SysUser user, SysOrgScope scope) {
        if ("HEADQUARTER_VIEW_SUB".equals(scope.getGrantType())) {
            return true;
        }
        if ("REGION_ISOLATED".equals(scope.getGrantType())) {
            return user.getOrgId() != null;
        }
        return true;
    }

    private String generateMatchReason(SysUser user, SysOrgScope scope) {
        if ("HEADQUARTER_VIEW_SUB".equals(scope.getGrantType())) {
            Integer depth = scope.getHierarchyDepth();
            return String.format("用户属于集团总部，可查看%s级以内下级组织",
                    depth != null ? depth : "全部");
        }
        if ("REGION_ISOLATED".equals(scope.getGrantType())) {
            return "区域数据隔离规则命中";
        }
        return "规则命中";
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

    private AccessScope calculateAccessScope(SysUser user, SysResource resource,
                                            List<SysUserPermission> permissions,
                                            List<SysOrgScope> orgScopes,
                                            EnhancedAccessCheckRequest request) {
        AccessScope scope = new AccessScope();
        List<Long> orgIds = new ArrayList<>();
        List<Long> projectIds = new ArrayList<>();
        List<Integer> customerLevels = new ArrayList<>();

        for (SysUserPermission permission : permissions) {
            if ("ALL".equals(permission.getOrgScopeType())) {
                for (SysOrgScope orgScope : orgScopes) {
                    if ("HEADQUARTER_VIEW_SUB".equals(orgScope.getGrantType())) {
                        List<Long> visibleIds = orgScopeService.getVisibleOrgIds(
                                user.getOrgId(), orgScope.getGrantType(), orgScope.getHierarchyDepth());
                        orgIds.addAll(visibleIds);
                    } else {
                        orgIds.add(user.getOrgId());
                    }
                }
                if (orgScopes.isEmpty()) {
                    orgIds.add(user.getOrgId());
                }
            } else if ("SPECIFIC".equals(permission.getOrgScopeType())) {
                if (permission.getOrgScopeValue() != null) {
                    JSONObject json = JSON.parseObject(permission.getOrgScopeValue());
                    if (json.containsKey("orgIds")) {
                        orgIds.addAll(json.getJSONArray("orgIds").toList(Long.class));
                    }
                }
            }
        }

        if (orgIds.isEmpty()) {
            orgIds.add(user.getOrgId());
        }
        orgIds = orgIds.stream().distinct().sorted().collect(Collectors.toList());
        scope.setOrgIds(orgIds);

        if (request.getComplexConditions() != null) {
            EnhancedAccessCheckRequest.ComplexConditions conditions = request.getComplexConditions();

            if (conditions.getCustomerLevel() != null) {
                scope.setCustomerLevels(conditions.getCustomerLevel().getLevels());
            }

            if (conditions.getProjectScope() != null) {
                scope.setProjectIds(conditions.getProjectScope().getProjectIds());
            }

            if (conditions.getTimeRange() != null) {
                AccessScope.TimeRangeDetail timeRange = new AccessScope.TimeRangeDetail();
                timeRange.setStartTime(conditions.getTimeRange().getStartTime());
                timeRange.setEndTime(conditions.getTimeRange().getEndTime());
                scope.setTimeRange(timeRange);
            }
        }

        scope.setOrgType("DEPT");
        return scope;
    }

    private List<FieldPermission> checkFieldPermissions(EnhancedAccessCheckRequest request,
                                                        SysResource resource,
                                                        List<SysUserPermission> permissions) {
        List<FieldPermission> result = new ArrayList<>();
        int userFieldLevel = getUserFieldLevel(permissions);
        boolean desensitizationEnabled = isDesensitizationEnabled(permissions);

        List<SysSensitiveField> sensitiveFields = sensitiveFieldService.getByResourceId(resource.getId());
        Map<String, SysSensitiveField> fieldMap = sensitiveFields.stream()
                .collect(Collectors.toMap(SysSensitiveField::getFieldName, f -> f));

        if (request.getRequestedFields() == null || request.getRequestedFields().isEmpty()) {
            return result;
        }

        for (String field : request.getRequestedFields()) {
            FieldPermission fp = new FieldPermission();
            fp.setField(field);
            fp.setAllowed(true);
            fp.setMasked(false);

            SysSensitiveField sensitiveField = fieldMap.get(field);
            if (sensitiveField != null) {
                if (sensitiveField.getSensitivityLevel() > userFieldLevel) {
                    fp.setAllowed(false);
                    fp.setRequiredLevel(sensitiveField.getSensitivityLevel());

                    if ("HIDE".equals(sensitiveField.getDesensitizationType())) {
                        fp.setMasked(false);
                        fp.setReason(String.format("字段等级(%d)超过用户权限(%d)，需要隐藏",
                                sensitiveField.getSensitivityLevel(), userFieldLevel));
                    } else if (desensitizationEnabled) {
                        fp.setMasked(true);
                        fp.setMaskedValue("***");
                        fp.setReason(String.format("字段等级(%d)超过用户权限(%d)，已脱敏",
                                sensitiveField.getSensitivityLevel(), userFieldLevel));
                    } else {
                        fp.setMasked(false);
                        fp.setReason(String.format("字段等级(%d)超过用户权限(%d)，需要隐藏",
                                sensitiveField.getSensitivityLevel(), userFieldLevel));
                    }
                }
            }

            result.add(fp);
        }

        return result;
    }

    private int getUserFieldLevel(List<SysUserPermission> permissions) {
        if (permissions.isEmpty()) {
            return 1;
        }
        return permissions.stream()
                .mapToInt(p -> p.getFieldAccessLevel() != null ? p.getFieldAccessLevel() : 1)
                .max()
                .orElse(1);
    }

    private boolean isDesensitizationEnabled(List<SysUserPermission> permissions) {
        if (permissions.isEmpty()) {
            return true;
        }
        for (SysUserPermission permission : permissions) {
            if (permission.getDesensitizationEnabled() != null && permission.getDesensitizationEnabled() == 1) {
                return true;
            }
        }
        return false;
    }

    private SqlFilters buildSqlFilters(EnhancedAccessCheckRequest request, AccessScope scope) {
        SqlFilters filters = new SqlFilters();
        List<String> conditions = new ArrayList<>();

        if (scope.getOrgIds() != null && !scope.getOrgIds().isEmpty()) {
            if (scope.getOrgIds().size() == 1) {
                conditions.add("org_id = " + scope.getOrgIds().get(0));
            } else {
                conditions.add("org_id IN (" + scope.getOrgIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")) + ")");
            }
        }

        if (scope.getCustomerLevels() != null && !scope.getCustomerLevels().isEmpty()) {
            conditions.add("customer_level IN (" + scope.getCustomerLevels().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")) + ")");
        }

        if (scope.getProjectIds() != null && !scope.getProjectIds().isEmpty()) {
            conditions.add("project_id IN (" + scope.getProjectIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")) + ")");
        }

        if (scope.getTimeRange() != null) {
            if (scope.getTimeRange().getStartTime() != null) {
                conditions.add("order_time >= '" + scope.getTimeRange().getStartTime() + "'");
            }
            if (scope.getTimeRange().getEndTime() != null) {
                conditions.add("order_time <= '" + scope.getTimeRange().getEndTime() + "'");
            }
        }

        if (!conditions.isEmpty()) {
            filters.setWhereClause(String.join(" AND ", conditions));
        }

        filters.setOrderByClause("order_time DESC");
        filters.setLimitClause("LIMIT 1000");

        return filters;
    }

    private ApplyPermission buildApplyPermission(List<FieldPermission> fieldPermissions) {
        ApplyPermission apply = new ApplyPermission();

        List<FieldPermission> deniedFields = fieldPermissions.stream()
                .filter(fp -> !fp.getAllowed())
                .collect(Collectors.toList());

        if (deniedFields.isEmpty()) {
            apply.setCanApply(false);
            return apply;
        }

        apply.setCanApply(true);
        apply.setApplyUrl("/permission/apply");

        List<PermissionOption> options = new ArrayList<>();
        Map<Integer, List<String>> levelFieldsMap = new HashMap<>();

        for (FieldPermission fp : deniedFields) {
            if (fp.getRequiredLevel() != null) {
                levelFieldsMap.computeIfAbsent(fp.getRequiredLevel(), k -> new ArrayList<>())
                        .add(fp.getField());
            }
        }

        for (Map.Entry<Integer, List<String>> entry : levelFieldsMap.entrySet()) {
            PermissionOption option = new PermissionOption();
            option.setPermissionType("FIELD_LEVEL");
            option.setTargetFields(entry.getValue());
            option.setRequiredLevel(entry.getKey());
            option.setApprovalRequired(entry.getKey() >= 4);
            option.setValidityPeriod(entry.getKey() >= 5 ? "需特批" : "30天");
            options.add(option);
        }

        apply.setPermissionOptions(options);
        return apply;
    }

    private boolean checkOperationPermission(List<SysUserPermission> permissions, String operationType) {
        if (permissions.isEmpty()) {
            return false;
        }
        for (SysUserPermission permission : permissions) {
            String operations = permission.getOperationTypes();
            if (operations != null && operations.contains(operationType)) {
                return true;
            }
        }
        return false;
    }

    private void checkExportAlert(Long userId) {
        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAccessLog::getUserId, userId)
                .eq(SysAccessLog::getOperationType, "EXPORT")
                .ge(SysAccessLog::getCreatedTime, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
        Long count = accessLogMapper.selectCount(wrapper);

        if (count > 100) {
            SysAnomalyAlert alert = new SysAnomalyAlert();
            alert.setUserId(userId);
            alert.setAlertType("ABNORMAL_DOWNLOAD");
            alert.setAlertContent(String.format("用户今日导出次数超过100次，当前次数: %d", count));
            alert.setAlertLevel(2);
            alert.setRiskScore(50 + (int) ((count - 100) / 10));
            anomalyAlertMapper.insert(alert);
        }
    }

    private void saveAccessLog(EnhancedAccessCheckRequest request, SysResource resource,
                               AccessDecision decision, String reason, long executionTime) {
        try {
            SysAccessLog log = new SysAccessLog();
            log.setUserId(request.getUserId());
            log.setResourceId(resource.getId());
            log.setOperationType(request.getOperationType());
            log.setAccessDecision(decision.name());
            log.setDeniedReason(reason);
            log.setQueryConditions(request.getQueryConditions() != null ?
                    JsonUtil.toJsonString(request.getQueryConditions()) : null);
            log.setRequestParams(request.getRequestedFields() != null ?
                    JsonUtil.toJsonString(request.getRequestedFields()) : null);
            log.setExecutionTimeMs(executionTime);
            accessLogMapper.insert(log);
        } catch (Exception e) {
            log.error("保存访问日志失败", e);
        }
    }
}
