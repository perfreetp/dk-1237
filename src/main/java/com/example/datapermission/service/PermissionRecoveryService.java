package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.datapermission.dto.RecoveryTaskDTO;
import com.example.datapermission.dto.RecoveryTaskDTO.*;
import com.example.datapermission.entity.SysPermissionChangeLog;
import com.example.datapermission.entity.SysPermissionRecovery;
import com.example.datapermission.entity.SysPermissionTask;
import com.example.datapermission.entity.SysResource;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionRecoveryService {

    private final SysPermissionRecoveryMapper recoveryMapper;
    private final SysUserPermissionMapper permissionMapper;
    private final SysPermissionTaskMapper taskMapper;
    private final SysPermissionChangeLogMapper changeLogMapper;
    private final SysResourceMapper resourceMapper;
    private final SysUserMapper userMapper;

    private static final int DEFAULT_MAX_RETRY = 3;
    private static final int RETRY_INTERVAL_MINUTES = 5;

    @Transactional
    public void createRecoveryRecords(String taskId, List<PermissionRecoveryInfo> permissions) {
        for (PermissionRecoveryInfo info : permissions) {
            SysPermissionRecovery recovery = new SysPermissionRecovery();
            recovery.setTaskId(taskId);
            recovery.setPermissionId(info.getPermissionId());
            recovery.setUserId(info.getUserId());
            recovery.setResourceId(info.getResourceId());
            recovery.setResourceCode(info.getResourceCode());
            recovery.setAction(info.getAction());
            recovery.setStatus(0);
            recovery.setRetryCount(0);
            recovery.setMaxRetryCount(DEFAULT_MAX_RETRY);
            recovery.setNextRetryTime(LocalDateTime.now());
            recovery.setCreatedTime(LocalDateTime.now());
            recoveryMapper.insert(recovery);
        }
    }

    @Transactional
    public RecoveryTaskDTO getRecoveryTask(String taskId) {
        List<SysPermissionRecovery> records = recoveryMapper.selectByTaskId(taskId);
        if (records == null || records.isEmpty()) {
            return null;
        }

        RecoveryTaskDTO dto = new RecoveryTaskDTO();
        dto.setTaskId(taskId);

        if (!records.isEmpty()) {
            SysPermissionRecovery first = records.get(0);
            dto.setUserId(first.getUserId());

            com.example.datapermission.entity.SysUser user = userMapper.selectById(first.getUserId());
            if (user != null) {
                dto.setUserName(user.getUsername());
            }
        }

        dto.setItems(records.stream()
                .map(this::convertToItemDTO)
                .collect(Collectors.toList()));

        dto.setSummary(buildSummary(records));

        if (records.stream().allMatch(r -> r.getStatus() == 1)) {
            dto.setStatus("COMPLETED");
        } else if (records.stream().anyMatch(r -> r.getStatus() == 2)) {
            dto.setStatus("PARTIAL_FAILED");
        } else {
            dto.setStatus("IN_PROGRESS");
        }

        return dto;
    }

    @Transactional
    public RecoveryResult executeRecovery(Long recoveryId) {
        SysPermissionRecovery recovery = recoveryMapper.selectById(recoveryId);
        if (recovery == null) {
            return RecoveryResult.fail("回收记录不存在");
        }

        if (recovery.getStatus() == 1) {
            return RecoveryResult.fail("该权限已回收成功，无需重复操作");
        }

        try {
            if ("REVOKE".equals(recovery.getAction())) {
                boolean success = revokePermission(recovery.getPermissionId());
                if (success) {
                    updateRecoveryStatus(recovery, 1, null);
                    return RecoveryResult.success();
                } else {
                    return handleRecoveryFailure(recovery, "权限撤销失败");
                }
            } else if ("TRANSFER".equals(recovery.getAction())) {
                boolean success = transferPermission(recovery);
                if (success) {
                    updateRecoveryStatus(recovery, 1, null);
                    return RecoveryResult.success();
                } else {
                    return handleRecoveryFailure(recovery, "权限转移失败");
                }
            }

            return RecoveryResult.fail("未知操作类型: " + recovery.getAction());

        } catch (Exception e) {
            log.error("执行回收异常: recoveryId={}", recoveryId, e);
            return handleRecoveryFailure(recovery, "执行异常: " + e.getMessage());
        }
    }

    @Transactional
    public BatchRetryResult retryFailed(String taskId) {
        List<SysPermissionRecovery> failedRecords = recoveryMapper.selectByStatusAndTaskId(taskId, 2);

        BatchRetryResult result = new BatchRetryResult();
        result.setTotalCount(failedRecords.size());
        result.setSuccessCount(0);
        result.setFailedCount(0);

        for (SysPermissionRecovery record : failedRecords) {
            RecoveryResult retryResult = executeRecovery(record.getId());
            if (retryResult.isSuccess()) {
                result.setSuccessCount(result.getSuccessCount() + 1);
            } else {
                result.setFailedCount(result.getFailedCount() + 1);
            }
        }

        return result;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processPendingRecoveries() {
        List<SysPermissionRecovery> pendingRecoveries = recoveryMapper.selectPendingRecoveries();

        for (SysPermissionRecovery recovery : pendingRecoveries) {
            if (recovery.getNextRetryTime() != null &&
                    recovery.getNextRetryTime().isAfter(LocalDateTime.now())) {
                continue;
            }

            log.info("自动重试回收: recoveryId={}, retryCount={}",
                    recovery.getId(), recovery.getRetryCount());

            executeRecovery(recovery.getId());
        }
    }

    private RecoveryResult handleRecoveryFailure(SysPermissionRecovery recovery, String errorMessage) {
        int newRetryCount = recovery.getRetryCount() + 1;

        if (newRetryCount >= recovery.getMaxRetryCount()) {
            updateRecoveryStatus(recovery, 2, errorMessage);
            return RecoveryResult.fail(errorMessage + "，已达最大重试次数");
        }

        updateRecoveryForRetry(recovery, errorMessage, newRetryCount);
        return RecoveryResult.fail(errorMessage + "，将在" + RETRY_INTERVAL_MINUTES + "分钟后自动重试");
    }

    private void updateRecoveryStatus(SysPermissionRecovery recovery, int status, String errorMessage) {
        LambdaUpdateWrapper<SysPermissionRecovery> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysPermissionRecovery::getId, recovery.getId())
                .set(SysPermissionRecovery::getStatus, status)
                .set(SysPermissionRecovery::getUpdatedTime, LocalDateTime.now());

        if (status == 1) {
            wrapper.set(SysPermissionRecovery::getCompletedTime, LocalDateTime.now());
        }

        if (errorMessage != null) {
            wrapper.set(SysPermissionRecovery::getErrorMessage, errorMessage);
        }

        recoveryMapper.update(null, wrapper);
    }

    private void updateRecoveryForRetry(SysPermissionRecovery recovery, String errorMessage, int newRetryCount) {
        LambdaUpdateWrapper<SysPermissionRecovery> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysPermissionRecovery::getId, recovery.getId())
                .set(SysPermissionRecovery::getRetryCount, newRetryCount)
                .set(SysPermissionRecovery::getErrorMessage, errorMessage)
                .set(SysPermissionRecovery::getLastRetryTime, LocalDateTime.now())
                .set(SysPermissionRecovery::getNextRetryTime,
                        LocalDateTime.now().plusMinutes(RETRY_INTERVAL_MINUTES))
                .set(SysPermissionRecovery::getUpdatedTime, LocalDateTime.now());

        recoveryMapper.update(null, wrapper);
    }

    private boolean revokePermission(Long permissionId) {
        if (permissionId == null) return false;

        com.example.datapermission.entity.SysUserPermission permission = permissionMapper.selectById(permissionId);
        if (permission == null) return false;

        permission.setStatus(0);
        permission.setEndTime(LocalDateTime.now());
        return permissionMapper.updateById(permission) > 0;
    }

    private boolean transferPermission(SysPermissionRecovery recovery) {
        return true;
    }

    private RecoveryItemDTO convertToItemDTO(SysPermissionRecovery recovery) {
        RecoveryItemDTO item = new RecoveryItemDTO();
        item.setId(recovery.getId());
        item.setPermissionId(recovery.getPermissionId());
        item.setUserId(recovery.getUserId());
        item.setResourceId(recovery.getResourceId());
        item.setResourceCode(recovery.getResourceCode());
        item.setAction(recovery.getAction());
        item.setStatus(recovery.getStatus());
        item.setErrorMessage(recovery.getErrorMessage());
        item.setRetryCount(recovery.getRetryCount());
        item.setMaxRetryCount(recovery.getMaxRetryCount());
        item.setLastRetryTime(recovery.getLastRetryTime());
        item.setNextRetryTime(recovery.getNextRetryTime());
        item.setCompletedTime(recovery.getCompletedTime());

        switch (recovery.getStatus()) {
            case 0 -> item.setStatusName("待执行");
            case 1 -> item.setStatusName("成功");
            case 2 -> item.setStatusName("失败");
            default -> item.setStatusName("未知");
        }

        item.setCanRetry(recovery.getStatus() == 2 &&
                recovery.getRetryCount() < recovery.getMaxRetryCount() ? "是" : "否");

        if (recovery.getResourceId() != null) {
            SysResource resource = resourceMapper.selectById(recovery.getResourceId());
            if (resource != null) {
                item.setResourceName(resource.getResourceName());
            }
        }

        return item;
    }

    private RecoverySummary buildSummary(List<SysPermissionRecovery> records) {
        RecoverySummary summary = new RecoverySummary();
        summary.setTotalCount(records.size());
        summary.setSuccessCount((int) records.stream().filter(r -> r.getStatus() == 1).count());
        summary.setFailedCount((int) records.stream().filter(r -> r.getStatus() == 2).count());
        summary.setPendingCount((int) records.stream().filter(r -> r.getStatus() == 0).count());
        summary.setRetryCount((int) records.stream()
                .filter(r -> r.getStatus() == 2 && r.getRetryCount() < r.getMaxRetryCount())
                .count());

        if (summary.getTotalCount() > 0) {
            summary.setSuccessRate((double) summary.getSuccessCount() / summary.getTotalCount() * 100);
        } else {
            summary.setSuccessRate(0.0);
        }

        return summary;
    }

    @Data
    public static class PermissionRecoveryInfo {
        private Long permissionId;
        private Long userId;
        private Long resourceId;
        private String resourceCode;
        private String action;
    }

    @Data
    public static class RecoveryResult {
        private boolean success;
        private String message;

        public static RecoveryResult success() {
            RecoveryResult result = new RecoveryResult();
            result.setSuccess(true);
            result.setMessage("操作成功");
            return result;
        }

        public static RecoveryResult fail(String message) {
            RecoveryResult result = new RecoveryResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }
    }

    @Data
    public static class BatchRetryResult {
        private int totalCount;
        private int successCount;
        private int failedCount;
    }
}
