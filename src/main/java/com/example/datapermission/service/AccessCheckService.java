package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.AccessCheckRequest;
import com.example.datapermission.dto.AccessCheckResponse;
import com.example.datapermission.entity.*;
import com.example.datapermission.enums.AccessDecision;
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
public class AccessCheckService {

    private final SysUserService userService;
    private final SysResourceService resourceService;
    private final SysSensitiveFieldService sensitiveFieldService;
    private final SysUserPermissionService userPermissionService;
    private final SysOrgScopeService orgScopeService;
    private final SysPermissionTemplateService templateService;
    private final SysAccessLogMapper accessLogMapper;
    private final SysAnomalyAlertMapper anomalyAlertMapper;
    private final DesensitizationUtil desensitizationUtil;

    public AccessCheckResponse checkAccess(AccessCheckRequest request) {
        long startTime = System.currentTimeMillis();
        AccessCheckResponse response = new AccessCheckResponse();

        try {
            SysUser user = userService.getById(request.getUserId());
            SysResource resource = resourceService.getByCode(request.getResourceCode());

            List<SysUserPermission> permissions = getEffectivePermissions(user.getId(), resource.getId());

            if (permissions.isEmpty()) {
                permissions = getPermissionsFromPost(user.getPostId(), resource.getId());
            }

            AccessCheckResponse.AccessScope accessScope = calculateAccessScope(user, resource, permissions);
            response.setAccessibleScope(accessScope);

            boolean hasOperationPermission = checkOperationPermission(permissions, request.getOperationType());
            if (!hasOperationPermission) {
                response.setAccessDecision(AccessDecision.DENY.name());
                response.setAllowed(false);
                response.setDeniedReason("您没有" + request.getOperationType() + "该资源的权限");
                response.setSuggestions(List.of("请联系管理员申请权限", "/permission/apply?resourceCode=" + request.getResourceCode()));
                saveAccessLog(request, resource, AccessDecision.DENY, response.getDeniedReason(), System.currentTimeMillis() - startTime);
                return response;
            }

            List<String> hiddenFields = new ArrayList<>();
            List<AccessCheckResponse.MaskedField> maskedFields = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();

            int userFieldLevel = getUserFieldLevel(permissions);

            List<SysSensitiveField> sensitiveFields = sensitiveFieldService.getByResourceId(resource.getId());
            Map<String, SysSensitiveField> fieldMap = sensitiveFields.stream()
                    .collect(Collectors.toMap(SysSensitiveField::getFieldName, f -> f));

            boolean desensitizationEnabled = isDesensitizationEnabled(permissions);

            if (request.getRequestedFields() != null && !request.getRequestedFields().isEmpty()) {
                for (String field : request.getRequestedFields()) {
                    SysSensitiveField sensitiveField = fieldMap.get(field);
                    if (sensitiveField != null) {
                        if (sensitiveField.getSensitivityLevel() > userFieldLevel) {
                            if ("HIDE".equals(sensitiveField.getDesensitizationType())) {
                                hiddenFields.add(field);
                            } else if (desensitizationEnabled) {
                                AccessCheckResponse.MaskedField masked = new AccessCheckResponse.MaskedField();
                                masked.setField(field);
                                masked.setMaskedValue("***");
                                masked.setReason("字段等级(" + sensitiveField.getSensitivityLevel() + ")超过用户权限(" + userFieldLevel + ")");
                                maskedFields.add(masked);
                            } else {
                                hiddenFields.add(field);
                            }
                        }
                    }
                }
            }

            List<AccessCheckResponse.QueryFilter> queryFilters = buildQueryFilters(request, accessScope);

            if (!hiddenFields.isEmpty() || !maskedFields.isEmpty()) {
                response.setAccessDecision(AccessDecision.PARTIAL.name());
                response.setAllowed(true);
            } else {
                response.setAccessDecision(AccessDecision.ALLOW.name());
                response.setAllowed(true);
            }

            response.setHiddenFields(hiddenFields);
            response.setMaskedFields(maskedFields);
            response.setQueryFilters(queryFilters);

            if (!hiddenFields.isEmpty()) {
                response.setDeniedReason("部分字段无访问权限: " + String.join(", ", hiddenFields));
                response.setApplyUrl("/permission/apply?resourceCode=" + request.getResourceCode() + "&fields=" + String.join(",", hiddenFields));
                suggestions.add("您的岗位权限模板中不包含以上字段的访问权限");
                suggestions.add("可申请临时授权访问这些字段");
            }

            response.setSuggestions(suggestions);

            if ("EXPORT".equals(request.getOperationType())) {
                checkExportAlert(user.getId());
            }

            saveAccessLog(request, resource, AccessDecision.valueOf(response.getAccessDecision()),
                    response.getDeniedReason(), System.currentTimeMillis() - startTime);

            response.setExecutionTime(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            log.error("访问校验异常", e);
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
                .filter(p -> (p.getStartTime() == null || !p.getStartTime().isAfter(now)))
                .filter(p -> (p.getEndTime() == null || !p.getEndTime().isBefore(now)))
                .toList();
    }

    private List<SysUserPermission> getPermissionsFromPost(Long postId, Long resourceId) {
        if (postId == null) {
            return List.of();
        }
        return List.of();
    }

    private AccessCheckResponse.AccessScope calculateAccessScope(SysUser user, SysResource resource, List<SysUserPermission> permissions) {
        AccessCheckResponse.AccessScope scope = new AccessCheckResponse.AccessScope();
        List<Long> orgIds = new ArrayList<>();
        String orgType = null;

        for (SysUserPermission permission : permissions) {
            if ("ALL".equals(permission.getOrgScopeType())) {
                List<SysOrgScope> orgScopes = orgScopeService.getActiveBySourceOrgId(user.getOrgId());
                for (SysOrgScope orgScope : orgScopes) {
                    if ("HEADQUARTER_VIEW_SUB".equals(orgScope.getGrantType())) {
                        List<Long> visibleIds = orgScopeService.getVisibleOrgIds(user.getOrgId(), orgScope.getGrantType(), orgScope.getHierarchyDepth());
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
            } else if ("HIERARCHY".equals(permission.getOrgScopeType())) {
                List<Long> visibleIds = orgScopeService.getVisibleOrgIds(user.getOrgId(), "HEADQUARTER_VIEW_SUB", null);
                orgIds.addAll(visibleIds);
            }
        }

        if (orgIds.isEmpty()) {
            orgIds.add(user.getOrgId());
        }

        orgIds = orgIds.stream().distinct().toList();
        scope.setOrgIds(orgIds);

        try {
            SysOrganization org = user.getOrgId() != null ?
                    (user.getOrgId() > 0 ? new SysOrganization() : null) : null;
            if (org != null) {
                orgType = org.getOrgType();
            }
        } catch (Exception e) {
            orgType = "DEPT";
        }

        scope.setOrgType(orgType != null ? orgType : "DEPT");
        return scope;
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

    private List<AccessCheckResponse.QueryFilter> buildQueryFilters(AccessCheckRequest request, AccessCheckResponse.AccessScope scope) {
        List<AccessCheckResponse.QueryFilter> filters = new ArrayList<>();

        if (scope != null && scope.getOrgIds() != null && !scope.getOrgIds().isEmpty()) {
            AccessCheckResponse.QueryFilter filter = new AccessCheckResponse.QueryFilter();
            filter.setField("org_id");
            filter.setOperator("IN");
            filter.setValue(scope.getOrgIds());
            filters.add(filter);
        }

        if (request.getQueryConditions() != null) {
            JSONObject conditions = null;
            if (request.getQueryConditions() instanceof String) {
                conditions = JSON.parseObject((String) request.getQueryConditions());
            } else if (request.getQueryConditions() instanceof JSONObject) {
                conditions = (JSONObject) request.getQueryConditions();
            }

            if (conditions != null && scope != null && scope.getOrgIds() != null && scope.getOrgIds().size() == 1) {
                if (conditions.containsKey("orgId")) {
                    Object requestedOrgId = conditions.get("orgId");
                    if (requestedOrgId instanceof Integer) {
                        requestedOrgId = ((Integer) requestedOrgId).longValue();
                    }
                    if (!scope.getOrgIds().contains(requestedOrgId)) {
                        AccessCheckResponse.QueryFilter filter = new AccessCheckResponse.QueryFilter();
                        filter.setField("org_id");
                        filter.setOperator("=");
                        filter.setValue(scope.getOrgIds().get(0));
                        filters.clear();
                        filters.add(filter);
                    }
                }
            }
        }

        return filters;
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
            alert.setAlertContent("用户今日导出次数超过100次，当前次数: " + count);
            alert.setAlertLevel(2);
            anomalyAlertMapper.insert(alert);
        }
    }

    private void saveAccessLog(AccessCheckRequest request, SysResource resource, AccessDecision decision, String reason, long executionTime) {
        try {
            SysAccessLog log = new SysAccessLog();
            log.setUserId(request.getUserId());
            log.setResourceId(resource.getId());
            log.setOperationType(request.getOperationType());
            log.setAccessDecision(decision.name());
            log.setDeniedReason(reason);
            log.setQueryConditions(request.getQueryConditions() != null ? JsonUtil.toJsonString(request.getQueryConditions()) : null);
            log.setRequestParams(request.getRequestedFields() != null ? JsonUtil.toJsonString(request.getRequestedFields()) : null);
            log.setExecutionTimeMs(executionTime);
            accessLogMapper.insert(log);
        } catch (Exception e) {
            AccessCheckService.log.error("保存访问日志失败", e);
        }
    }
}
