package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.PermissionExportRequest;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditExportService {

    private final SysUserPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;
    private final SysResourceMapper resourceMapper;
    private final SysPermissionChangeLogMapper changeLogMapper;
    private final SysOrganizationMapper organizationMapper;

    public Map<String, Object> exportPermissions(PermissionExportRequest request) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionQueryWrapper(request);
        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);

        List<Map<String, Object>> permissionList = buildPermissionExportData(permissions, request);

        List<Map<String, Object>> filteredList = applyRiskFilters(permissionList, request.getRiskFilters());

        result.put("permissions", filteredList);
        result.put("totalCount", filteredList.size());
        result.put("unfilteredCount", permissionList.size());

        if (Boolean.TRUE.equals(request.getIncludeStatistics())) {
            result.put("statistics", calculateStatistics(filteredList));
        }

        if (Boolean.TRUE.equals(request.getIncludeChangeHistory())) {
            result.put("changeHistory", getRecentChanges(request.getStartDate(), request.getEndDate()));
        }

        if (request.getRiskFilters() != null && !request.getRiskFilters().isEmpty()) {
            result.put("appliedFilters", request.getRiskFilters());
            result.put("filterSummary", buildFilterSummary(filteredList, request.getRiskFilters()));
        }

        return result;
    }

    public Map<String, Object> exportExpiringPermissions(int daysRemaining, PermissionExportRequest request) {
        request.setRiskFilters(List.of("EXPIRING"));
        return exportByRiskType("EXPIRING", daysRemaining, request);
    }

    public Map<String, Object> exportUnusedPermissions(int unusedDays, PermissionExportRequest request) {
        request.setRiskFilters(List.of("UNUSED"));
        return exportByRiskType("UNUSED", unusedDays, request);
    }

    private Map<String, Object> exportByRiskType(String riskType, int threshold, PermissionExportRequest request) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> filteredList = new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getStatus, 1);

        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);

        for (SysUserPermission permission : permissions) {
            Map<String, Object> item = buildSinglePermissionData(permission);

            boolean matches = false;
            if ("EXPIRING".equals(riskType) && permission.getEndTime() != null) {
                long days = ChronoUnit.DAYS.between(LocalDateTime.now(), permission.getEndTime());
                if (days >= 0 && days <= threshold) {
                    item.put("daysRemaining", days);
                    item.put("riskType", "EXPIRING");
                    matches = true;
                }
            } else if ("UNUSED".equals(riskType) && permission.getLastUsedTime() != null) {
                long days = ChronoUnit.DAYS.between(permission.getLastUsedTime(), LocalDateTime.now());
                if (days >= threshold) {
                    item.put("unusedDays", days);
                    item.put("riskType", "UNUSED");
                    matches = true;
                }
            } else if ("UNUSED".equals(riskType) && permission.getLastUsedTime() == null) {
                long days = ChronoUnit.DAYS.between(permission.getCreatedTime(), LocalDateTime.now());
                if (days >= threshold) {
                    item.put("unusedDays", days);
                    item.put("riskType", "UNUSED");
                    matches = true;
                }
            }

            if (matches) {
                addRiskTags(item, permission);
                filteredList.add(item);
            }
        }

        result.put("permissions", filteredList);
        result.put("totalCount", filteredList.size());
        result.put("riskType", riskType);
        result.put("threshold", threshold);
        result.put("statistics", calculateStatistics(filteredList));

        return result;
    }

    public Map<String, Object> exportOverGrantedPermissions(PermissionExportRequest request) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> filteredList = new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getStatus, 1)
                .ge(SysUserPermission::getFieldAccessLevel, 5);

        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);

        for (SysUserPermission permission : permissions) {
            Map<String, Object> item = buildSinglePermissionData(permission);
            item.put("riskType", "OVER_GRANTED");
            item.put("overGrantedReason", "权限等级过高(5级)");
            item.put("suggestion", "建议降低权限等级至3级以下");
            addRiskTags(item, permission);
            filteredList.add(item);
        }

        result.put("permissions", filteredList);
        result.put("totalCount", filteredList.size());
        result.put("riskType", "OVER_GRANTED");
        result.put("statistics", calculateStatistics(filteredList));

        return result;
    }

    private LambdaQueryWrapper<SysUserPermission> buildPermissionQueryWrapper(PermissionExportRequest request) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();

        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            wrapper.in(SysUserPermission::getUserId, request.getUserIds());
        }

        if (request.getOrgIds() != null && !request.getOrgIds().isEmpty()) {
            List<Long> userIds = getUserIdsByOrgIds(request.getOrgIds());
            wrapper.in(SysUserPermission::getUserId, userIds);
        }

        if (request.getResourceTypes() != null && !request.getResourceTypes().isEmpty()) {
            List<Long> resourceIds = getResourceIdsByTypes(request.getResourceTypes());
            wrapper.in(SysUserPermission::getResourceId, resourceIds);
        }

        if (request.getStartDate() != null) {
            wrapper.ge(SysUserPermission::getCreatedTime, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(SysUserPermission::getCreatedTime, request.getEndDate());
        }

        wrapper.eq(SysUserPermission::getStatus, 1);
        wrapper.orderByDesc(SysUserPermission::getCreatedTime);

        return wrapper;
    }

    private List<Map<String, Object>> buildPermissionExportData(List<SysUserPermission> permissions,
                                                              PermissionExportRequest request) {
        return permissions.stream()
                .map(p -> buildSinglePermissionData(p))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildSinglePermissionData(SysUserPermission permission) {
        Map<String, Object> item = new HashMap<>();

        item.put("permissionId", permission.getId());
        item.put("userId", permission.getUserId());
        item.put("userName", getUserName(permission.getUserId()));
        item.put("userOrg", getUserOrg(permission.getUserId()));
        item.put("resourceId", permission.getResourceId());
        item.put("resourceName", getResourceName(permission.getResourceId()));
        item.put("resourceCode", getResourceCode(permission.getResourceId()));
        item.put("orgScopeType", permission.getOrgScopeType());
        item.put("fieldAccessLevel", permission.getFieldAccessLevel());
        item.put("operationTypes", permission.getOperationTypes());
        item.put("grantType", permission.getGrantType());
        item.put("startTime", formatDateTime(permission.getStartTime()));
        item.put("endTime", formatDateTime(permission.getEndTime()));
        item.put("status", permission.getStatus() == 1 ? "生效中" : "已撤销");
        item.put("lastUsedTime", formatDateTime(permission.getLastUsedTime()));
        item.put("usedCount", permission.getUsedCount());
        item.put("createdTime", formatDateTime(permission.getCreatedTime()));
        item.put("grantReason", permission.getGrantReason());

        return item;
    }

    private List<Map<String, Object>> applyRiskFilters(List<Map<String, Object>> permissions,
                                                     List<String> riskFilters) {
        if (riskFilters == null || riskFilters.isEmpty()) {
            return permissions;
        }

        return permissions.stream()
                .filter(p -> {
                    List<String> tags = (List<String>) p.get("riskTags");
                    if (tags == null || tags.isEmpty()) {
                        return false;
                    }
                    return tags.stream().anyMatch(t -> riskFilters.contains(t));
                })
                .collect(Collectors.toList());
    }

    private void addRiskTags(Map<String, Object> item, SysUserPermission permission) {
        List<String> riskTags = new ArrayList<>();

        if (permission.getEndTime() != null) {
            long daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), permission.getEndTime());
            if (daysRemaining <= 7 && daysRemaining >= 0) {
                riskTags.add("EXPIRING");
                item.put("daysRemaining", daysRemaining);
            }
        }

        if (permission.getLastUsedTime() != null) {
            long unusedDays = ChronoUnit.DAYS.between(permission.getLastUsedTime(), LocalDateTime.now());
            if (unusedDays >= 90) {
                riskTags.add("UNUSED");
                item.put("unusedDays", unusedDays);
            }
        } else if (permission.getCreatedTime() != null) {
            long unusedDays = ChronoUnit.DAYS.between(permission.getCreatedTime(), LocalDateTime.now());
            if (unusedDays >= 90) {
                riskTags.add("UNUSED");
                item.put("unusedDays", unusedDays);
            }
        }

        if (permission.getFieldAccessLevel() != null && permission.getFieldAccessLevel() >= 5) {
            riskTags.add("OVER_GRANTED");
            item.put("overGrantedReason", "权限等级过高(5级)");
        }

        item.put("riskTags", riskTags);
        item.put("riskLevel", determineRiskLevel(riskTags));
    }

    private String determineRiskLevel(List<String> riskTags) {
        if (riskTags.contains("OVER_GRANTED")) {
            return "CRITICAL";
        }
        if (riskTags.contains("UNUSED")) {
            return "HIGH";
        }
        if (riskTags.contains("EXPIRING")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Map<String, Object> calculateStatistics(List<Map<String, Object>> permissions) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalCount", permissions.size());

        Map<String, Long> byRiskType = new HashMap<>();
        int expiring = 0, unused = 0, overGranted = 0;

        for (Map<String, Object> perm : permissions) {
            List<String> tags = (List<String>) perm.get("riskTags");
            if (tags != null) {
                for (String tag : tags) {
                    byRiskType.merge(tag, 1L, Long::sum);
                    switch (tag) {
                        case "EXPIRING": expiring++; break;
                        case "UNUSED": unused++; break;
                        case "OVER_GRANTED": overGranted++; break;
                    }
                }
            }
        }

        stats.put("byRiskType", byRiskType);
        stats.put("expiringCount", expiring);
        stats.put("unusedCount", unused);
        stats.put("overGrantedCount", overGranted);

        Map<String, Long> byLevel = permissions.stream()
                .collect(Collectors.groupingBy(
                        p -> "等级" + p.getOrDefault("fieldAccessLevel", 1),
                        Collectors.counting()));
        stats.put("byLevel", byLevel);

        Map<String, Long> byGrantType = permissions.stream()
                .collect(Collectors.groupingBy(
                        p -> String.valueOf(p.getOrDefault("grantType", "UNKNOWN")),
                        Collectors.counting()));
        stats.put("byGrantType", byGrantType);

        return stats;
    }

    private Map<String, Object> buildFilterSummary(List<Map<String, Object>> permissions, List<String> filters) {
        Map<String, Object> summary = new HashMap<>();

        int expiring = 0, unused = 0, overGranted = 0;
        for (Map<String, Object> perm : permissions) {
            List<String> tags = (List<String>) perm.get("riskTags");
            if (tags != null) {
                if (tags.contains("EXPIRING")) expiring++;
                if (tags.contains("UNUSED")) unused++;
                if (tags.contains("OVER_GRANTED")) overGranted++;
            }
        }

        summary.put("totalFiltered", permissions.size());
        summary.put("expiringPermissions", expiring);
        summary.put("unusedPermissions", unused);
        summary.put("overGrantedPermissions", overGranted);
        summary.put("appliedFilters", filters);
        summary.put("exportTime", LocalDateTime.now());

        return summary;
    }

    private List<Map<String, Object>> getRecentChanges(LocalDateTime startDate, LocalDateTime endDate) {
        LambdaQueryWrapper<SysPermissionChangeLog> wrapper = new LambdaQueryWrapper<>();

        if (startDate != null) {
            wrapper.ge(SysPermissionChangeLog::getChangeTime, startDate);
        }
        if (endDate != null) {
            wrapper.le(SysPermissionChangeLog::getChangeTime, endDate);
        }

        wrapper.orderByDesc(SysPermissionChangeLog::getChangeTime);
        wrapper.last("LIMIT 100");

        return changeLogMapper.selectList(wrapper).stream().map(change -> {
            Map<String, Object> item = new HashMap<>();
            item.put("changeId", change.getId());
            item.put("permissionId", change.getPermissionId());
            item.put("changeType", change.getChangeType());
            item.put("beforeValue", change.getBeforeValue());
            item.put("afterValue", change.getAfterValue());
            item.put("changeReason", change.getChangeReason());
            item.put("changeBy", change.getChangeBy());
            item.put("changeTime", formatDateTime(change.getChangeTime()));
            item.put("clientIp", change.getClientIp());
            return item;
        }).collect(Collectors.toList());
    }

    private String getUserName(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getUsername() : "未知";
    }

    private String getUserOrg(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user != null && user.getOrgId() != null) {
            SysOrganization org = organizationMapper.selectById(user.getOrgId());
            return org != null ? org.getOrgName() : "未知";
        }
        return "未知";
    }

    private String getResourceName(Long resourceId) {
        SysResource resource = resourceMapper.selectById(resourceId);
        return resource != null ? resource.getResourceName() : "未知";
    }

    private String getResourceCode(Long resourceId) {
        SysResource resource = resourceMapper.selectById(resourceId);
        return resource != null ? resource.getResourceCode() : "unknown";
    }

    private List<Long> getUserIdsByOrgIds(List<Long> orgIds) {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getOrgId, orgIds)).stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());
    }

    private List<Long> getResourceIdsByTypes(List<String> types) {
        return resourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                .in(SysResource::getResourceType, types)).stream()
                .map(SysResource::getId)
                .collect(Collectors.toList());
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }
}
