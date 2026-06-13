package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.PermissionExportRequest;
import com.example.datapermission.dto.ReviewTaskRequest;
import com.example.datapermission.entity.*;
import com.example.datapermission.enums.RiskType;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditInventoryService {

    private final SysUserPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;
    private final SysResourceMapper resourceMapper;
    private final SysPermissionReviewTaskMapper reviewTaskMapper;
    private final SysPermissionReviewItemMapper reviewItemMapper;
    private final SysAccessLogMapper accessLogMapper;
    private final SysPermissionChangeLogMapper changeLogMapper;

    public Map<String, Object> exportPermissions(PermissionExportRequest request) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionQueryWrapper(request);

        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);

        List<Map<String, Object>> permissionList = new ArrayList<>();
        for (SysUserPermission permission : permissions) {
            Map<String, Object> item = buildPermissionExportItem(permission, request);
            addRiskTags(item, permission);
            permissionList.add(item);
        }

        result.put("permissions", permissionList);
        result.put("totalCount", permissionList.size());

        if (request.getRiskFilters() != null && !request.getRiskFilters().isEmpty()) {
            List<Map<String, Object>> riskPermissions = permissionList.stream()
                    .filter(p -> {
                        List<String> tags = (List<String>) p.get("riskTags");
                        if (tags == null) return false;
                        return tags.stream().anyMatch(t -> request.getRiskFilters().contains(t));
                    })
                    .collect(Collectors.toList());
            result.put("riskPermissions", riskPermissions);
            result.put("riskCount", riskPermissions.size());
        }

        if (Boolean.TRUE.equals(request.getIncludeStatistics())) {
            result.put("statistics", calculateStatistics(permissions));
        }

        if (Boolean.TRUE.equals(request.getIncludeChangeHistory())) {
            result.put("changeHistory", getRecentChanges(request.getStartDate(), request.getEndDate()));
        }

        return result;
    }

    private Map<String, Object> buildPermissionExportItem(SysUserPermission permission,
                                                         PermissionExportRequest request) {
        Map<String, Object> item = new HashMap<>();

        item.put("permissionId", permission.getId());
        item.put("userId", permission.getUserId());
        item.put("userName", getUserName(permission.getUserId()));
        item.put("resourceId", permission.getResourceId());
        item.put("resourceName", getResourceName(permission.getResourceId()));
        item.put("orgScopeType", permission.getOrgScopeType());
        item.put("fieldAccessLevel", permission.getFieldAccessLevel());
        item.put("operationTypes", permission.getOperationTypes());
        item.put("grantType", permission.getGrantType());
        item.put("startTime", permission.getStartTime());
        item.put("endTime", permission.getEndTime());
        item.put("status", permission.getStatus());
        item.put("lastUsedTime", permission.getLastUsedTime());
        item.put("usedCount", permission.getUsedCount());
        item.put("createdTime", permission.getCreatedTime());

        if ("USER".equals(request.getType())) {
            item.put("department", getUserDepartment(permission.getUserId()));
        }

        return item;
    }

    private void addRiskTags(Map<String, Object> item, SysUserPermission permission) {
        List<String> riskTags = new ArrayList<>();

        if (permission.getEndTime() != null) {
            long daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), permission.getEndTime());
            if (daysRemaining <= 7 && daysRemaining >= 0) {
                riskTags.add("EXPIRING");
                item.put("expiringDays", daysRemaining);
            }
        }

        if (permission.getLastUsedTime() != null) {
            long unusedDays = ChronoUnit.DAYS.between(permission.getLastUsedTime(), LocalDateTime.now());
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

        return wrapper;
    }

    private Map<String, Object> calculateStatistics(List<SysUserPermission> permissions) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalCount", permissions.size());

        Map<String, Long> byGrantType = permissions.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getGrantType() != null ? p.getGrantType() : "UNKNOWN",
                        Collectors.counting()));
        stats.put("byGrantType", byGrantType);

        Map<String, Long> byLevel = permissions.stream()
                .collect(Collectors.groupingBy(
                        p -> "Level " + (p.getFieldAccessLevel() != null ? p.getFieldAccessLevel() : 1),
                        Collectors.counting()));
        stats.put("byLevel", byLevel);

        Map<String, Long> byStatus = permissions.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() == 1 ? "active" : "inactive",
                        Collectors.counting()));
        stats.put("byStatus", byStatus);

        return stats;
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

        List<SysPermissionChangeLog> changes = changeLogMapper.selectList(wrapper);

        return changes.stream().map(change -> {
            Map<String, Object> item = new HashMap<>();
            item.put("changeId", change.getId());
            item.put("permissionId", change.getPermissionId());
            item.put("changeType", change.getChangeType());
            item.put("changeTime", change.getChangeTime());
            item.put("changeBy", change.getChangeBy());
            item.put("reason", change.getChangeReason());
            return item;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createReviewTask(ReviewTaskRequest request) {
        String taskId = "RT" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) +
                String.format("%04d", new Random().nextInt(10000));

        PermissionExportRequest exportRequest = new PermissionExportRequest();
        exportRequest.setOrgIds(request.getScope() != null ? request.getScope().getOrgIds() : null);
        exportRequest.setUserIds(request.getScope() != null ? request.getScope().getUserIds() : null);
        exportRequest.setResourceTypes(request.getScope() != null ? request.getScope().getResourceTypes() : null);
        exportRequest.setRiskFilters(request.getRiskFilters());

        Map<String, Object> exportResult = exportPermissions(exportRequest);
        List<Map<String, Object>> riskPermissions = (List<Map<String, Object>>) exportResult.getOrDefault("riskPermissions", new ArrayList<>());

        SysPermissionReviewTask task = new SysPermissionReviewTask();
        task.setTaskId(taskId);
        task.setTaskName(request.getTaskName());
        task.setScopeOrgIds(request.getScope() != null && request.getScope().getOrgIds() != null ?
                JSON.toJSONString(request.getScope().getOrgIds()) : null);
        task.setScopeUserIds(request.getScope() != null && request.getScope().getUserIds() != null ?
                JSON.toJSONString(request.getScope().getUserIds()) : null);
        task.setRiskFilters(request.getRiskFilters() != null ? JSON.toJSONString(request.getRiskFilters()) : null);
        task.setReviewers(request.getReviewers() != null ? JSON.toJSONString(request.getReviewers()) : null);
        task.setDueDate(request.getDueDate());
        task.setAutoRemind(request.getAutoRemind());
        task.setRemindInterval(request.getRemindInterval());
        task.setStatus("CREATED");
        task.setStatisticsTotal((long) exportResult.get("totalCount"));
        task.setStatisticsExpiring((long) riskPermissions.stream().filter(p ->
                ((List<String>) p.get("riskTags")).contains("EXPIRING")).count());
        task.setStatisticsUnused((long) riskPermissions.stream().filter(p ->
                ((List<String>) p.get("riskTags")).contains("UNUSED")).count());
        task.setStatisticsOverGranted((long) riskPermissions.stream().filter(p ->
                ((List<String>) p.get("riskTags")).contains("OVER_GRANTED")).count());
        task.setCompletedCount(0L);
        task.setCreatedTime(LocalDateTime.now());
        reviewTaskMapper.insert(task);

        List<SysPermissionReviewItem> reviewItems = new ArrayList<>();
        for (Map<String, Object> riskPerm : riskPermissions) {
            SysPermissionReviewItem item = new SysPermissionReviewItem();
            item.setTaskId(taskId);
            item.setPermissionId(((Number) riskPerm.get("permissionId")).longValue());
            item.setUserId(((Number) riskPerm.get("userId")).longValue());
            item.setResourceId(((Number) riskPerm.get("resourceId")).longValue());

            List<String> tags = (List<String>) riskPerm.get("riskTags");
            String riskType = tags.stream().findFirst().orElse("UNKNOWN");
            item.setRiskType(riskType);
            item.setRiskLevel(getRiskLevel(riskType));
            item.setRiskDetails(JSON.toJSONString(riskPerm));
            item.setReviewStatus("PENDING");
            item.setCreatedTime(LocalDateTime.now());
            reviewItems.add(item);
        }

        if (!reviewItems.isEmpty()) {
            reviewItemMapper.batchInsert(reviewItems);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", "CREATED");
        result.put("statistics", Map.of(
                "totalPermissions", exportResult.get("totalCount"),
                "expiringCount", task.getStatisticsExpiring(),
                "unusedCount", task.getStatisticsUnused(),
                "overGrantedCount", task.getStatisticsOverGranted()
        ));

        return result;
    }

    private int getRiskLevel(String riskType) {
        switch (riskType) {
            case "EXPIRING": return 1;
            case "UNUSED": return 2;
            case "OVER_GRANTED": return 3;
            default: return 0;
        }
    }

    private String getUserName(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getUsername() : "未知";
    }

    private String getResourceName(Long resourceId) {
        SysResource resource = resourceMapper.selectById(resourceId);
        return resource != null ? resource.getResourceName() : "未知";
    }

    private String getUserDepartment(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? "部门" + user.getOrgId() : "未知";
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
}
