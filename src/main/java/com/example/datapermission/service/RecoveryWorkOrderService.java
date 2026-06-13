package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.RecoveryWorkOrderRequest;
import com.example.datapermission.dto.RecoveryWorkOrderResponse;
import com.example.datapermission.dto.RecoveryWorkOrderResponse.*;
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
public class RecoveryWorkOrderService {

    private final SysRecoveryWorkOrderMapper workOrderMapper;
    private final SysRecoveryRetryLogMapper retryLogMapper;
    private final SysUserMapper userMapper;
    private final SysUserPermissionMapper permissionMapper;
    private final SysResourceMapper resourceMapper;
    private final SysPermissionRecoveryMapper recoveryMapper;
    private final PermissionRecoveryService permissionRecoveryService;

    @Transactional
    public RecoveryWorkOrderResponse createWorkOrder(RecoveryWorkOrderRequest request, Long createdBy) {
        SysUser targetUser = userMapper.selectById(request.getTargetUserId());
        if (targetUser == null) {
            throw new RuntimeException("目标用户不存在");
        }

        SysUser responsible = request.getResponsibleId() != null ?
                userMapper.selectById(request.getResponsibleId()) : null;

        SysUser transferTo = null;
        if (StringUtils.hasText(request.getTransferToUserId())) {
            try {
                transferTo = userMapper.selectById(Long.parseLong(request.getTransferToUserId()));
            } catch (Exception e) {
                log.warn("转换目标用户解析失败", e);
            }
        }

        String orderNo = generateOrderNo(request.getTriggerType());

        SysRecoveryWorkOrder workOrder = new SysRecoveryWorkOrder();
        workOrder.setOrderNo(orderNo);
        workOrder.setTriggerType(request.getTriggerType());
        workOrder.setTriggerUserId(createdBy);
        workOrder.setTargetUserId(request.getTargetUserId());
        workOrder.setTargetUsername(targetUser.getUsername());
        workOrder.setResponsibleId(request.getResponsibleId());
        workOrder.setStatus("PENDING");
        workOrder.setTransferToUserId(request.getTransferToUserId());
        workOrder.setTransferToUsername(transferTo != null ? transferTo.getUsername() : null);
        workOrder.setRemark(request.getRemark());
        workOrder.setDueTime(request.getDueTime());
        workOrder.setCreatedBy(createdBy);
        workOrder.setCreatedTime(LocalDateTime.now());

        List<SysUserPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<SysUserPermission>()
                        .eq(SysUserPermission::getUserId, request.getTargetUserId())
                        .eq(SysUserPermission::getStatus, 1)
        );

        workOrder.setTotalPermissions(permissions.size());
        workOrder.setPendingCount(permissions.size());
        workOrder.setSuccessCount(0);
        workOrder.setFailedCount(0);

        workOrderMapper.insert(workOrder);

        List<WorkOrderItem> items = new ArrayList<>();
        for (SysUserPermission permission : permissions) {
            SysResource resource = resourceMapper.selectById(permission.getResourceId());

            SysPermissionRecovery recovery = new SysPermissionRecovery();
            recovery.setTaskId(orderNo);
            recovery.setPermissionId(permission.getId());
            recovery.setUserId(permission.getUserId());
            recovery.setResourceId(permission.getResourceId());
            recovery.setStatus(0);
            recovery.setRetryCount(0);
            recovery.setMaxRetryCount(3);
            recovery.setCreatedTime(LocalDateTime.now());
            recoveryMapper.insert(recovery);

            WorkOrderItem item = WorkOrderItem.builder()
                    .id(recovery.getId())
                    .permissionId(permission.getId())
                    .resourceCode(resource != null ? resource.getResourceCode() : null)
                    .resourceName(resource != null ? resource.getResourceName() : null)
                    .status(0)
                    .statusName("待回收")
                    .retryCount(0)
                    .maxRetryCount(3)
                    .attempts(new ArrayList<>())
                    .build();
            items.add(item);
        }

        executeRecoveryForWorkOrder(orderNo);

        return buildWorkOrderResponse(workOrder, items);
    }

    public RecoveryWorkOrderResponse getWorkOrder(String orderNo) {
        SysRecoveryWorkOrder workOrder = workOrderMapper.selectOne(
                new LambdaQueryWrapper<SysRecoveryWorkOrder>()
                        .eq(SysRecoveryWorkOrder::getOrderNo, orderNo)
        );

        if (workOrder == null) {
            throw new RuntimeException("工单不存在");
        }

        List<SysPermissionRecovery> recoveries = recoveryMapper.selectList(
                new LambdaQueryWrapper<SysPermissionRecovery>()
                        .eq(SysPermissionRecovery::getTaskId, orderNo)
        );

        List<WorkOrderItem> items = new ArrayList<>();
        for (SysPermissionRecovery recovery : recoveries) {
            SysResource resource = resourceMapper.selectById(recovery.getResourceId());
            List<SysRecoveryRetryLog> logs = getRetryLogs(recovery.getId());

            WorkOrderItem item = WorkOrderItem.builder()
                    .id(recovery.getId())
                    .permissionId(recovery.getPermissionId())
                    .resourceCode(resource != null ? resource.getResourceCode() : null)
                    .resourceName(resource != null ? resource.getResourceName() : null)
                    .status(recovery.getStatus())
                    .statusName(getStatusName(recovery.getStatus()))
                    .errorMessage(recovery.getErrorMessage())
                    .retryCount(recovery.getRetryCount())
                    .maxRetryCount(recovery.getMaxRetryCount())
                    .nextRetryTime(recovery.getNextRetryTime())
                    .lastRetryTime(recovery.getUpdatedTime())
                    .attempts(logs.stream().map(log -> RetryAttemptRecord.builder()
                            .id(log.getId())
                            .attemptNumber(log.getAttemptNumber())
                            .status(log.getStatus())
                            .errorMessage(log.getErrorMessage())
                            .executedTime(log.getExecutedTime())
                            .responseResult(log.getResponseResult())
                            .build())
                            .collect(Collectors.toList()))
                    .build();
            items.add(item);
        }

        return buildWorkOrderResponse(workOrder, items);
    }

    public Page<RecoveryWorkOrderResponse> listWorkOrders(String status, Long responsibleId, int pageNum, int pageSize) {
        Page<SysRecoveryWorkOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysRecoveryWorkOrder> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(status)) {
            wrapper.eq(SysRecoveryWorkOrder::getStatus, status);
        }
        if (responsibleId != null) {
            wrapper.eq(SysRecoveryWorkOrder::getResponsibleId, responsibleId);
        }

        wrapper.orderByDesc(SysRecoveryWorkOrder::getCreatedTime);
        Page<SysRecoveryWorkOrder> resultPage = workOrderMapper.selectPage(page, wrapper);

        Page<RecoveryWorkOrderResponse> responsePage = new Page<>(pageNum, pageSize);
        responsePage.setTotal(resultPage.getTotal());

        List<RecoveryWorkOrderResponse> records = resultPage.getRecords().stream()
                .map(wo -> RecoveryWorkOrderResponse.builder()
                        .orderNo(wo.getOrderNo())
                        .triggerType(wo.getTriggerType())
                        .status(wo.getStatus())
                        .totalPermissions(wo.getTotalPermissions())
                        .pendingCount(wo.getPendingCount())
                        .successCount(wo.getSuccessCount())
                        .failedCount(wo.getFailedCount())
                        .createdTime(wo.getCreatedTime())
                        .updatedTime(wo.getUpdatedTime())
                        .build())
                .collect(Collectors.toList());

        responsePage.setRecords(records);
        return responsePage;
    }

    @Transactional
    public RecoveryWorkOrderResponse retryWorkOrder(String orderNo, Long executedBy) {
        SysRecoveryWorkOrder workOrder = workOrderMapper.selectOne(
                new LambdaQueryWrapper<SysRecoveryWorkOrder>()
                        .eq(SysRecoveryWorkOrder::getOrderNo, orderNo)
        );

        if (workOrder == null) {
            throw new RuntimeException("工单不存在");
        }

        List<SysPermissionRecovery> failedRecoveries = recoveryMapper.selectList(
                new LambdaQueryWrapper<SysPermissionRecovery>()
                        .eq(SysPermissionRecovery::getTaskId, orderNo)
                        .eq(SysPermissionRecovery::getStatus, 2)
        );

        for (SysPermissionRecovery recovery : failedRecoveries) {
            permissionRecoveryService.executeRecovery(recovery.getId());
        }

        return getWorkOrder(orderNo);
    }

    @Transactional
    public RecoveryWorkOrderResponse retrySingleItem(Long recoveryId, Long executedBy) {
        SysPermissionRecovery recovery = recoveryMapper.selectById(recoveryId);
        if (recovery == null) {
            throw new RuntimeException("回收记录不存在");
        }

        permissionRecoveryService.executeRecovery(recoveryId);

        return getWorkOrder(recovery.getTaskId());
    }

    @Transactional
    public void updateWorkOrderStatus(String orderNo) {
        List<SysPermissionRecovery> recoveries = recoveryMapper.selectList(
                new LambdaQueryWrapper<SysPermissionRecovery>()
                        .eq(SysPermissionRecovery::getTaskId, orderNo)
        );

        long successCount = recoveries.stream().filter(r -> r.getStatus() == 1).count();
        long failedCount = recoveries.stream().filter(r -> r.getStatus() == 2).count();
        long pendingCount = recoveries.stream().filter(r -> r.getStatus() == 0).count();

        String status = "PROCESSING";
        if (pendingCount == 0 && failedCount == 0) {
            status = "COMPLETED";
        } else if (pendingCount == 0 && failedCount > 0) {
            status = "PARTIAL_FAILED";
        } else if (successCount > 0 || failedCount > 0) {
            status = "PROCESSING";
        }

        SysRecoveryWorkOrder workOrder = workOrderMapper.selectOne(
                new LambdaQueryWrapper<SysRecoveryWorkOrder>()
                        .eq(SysRecoveryWorkOrder::getOrderNo, orderNo)
        );

        if (workOrder != null) {
            workOrder.setStatus(status);
            workOrder.setPendingCount((int) pendingCount);
            workOrder.setSuccessCount((int) successCount);
            workOrder.setFailedCount((int) failedCount);
            workOrder.setUpdatedTime(LocalDateTime.now());
            workOrderMapper.updateById(workOrder);
        }
    }

    private void executeRecoveryForWorkOrder(String orderNo) {
        List<SysPermissionRecovery> recoveries = recoveryMapper.selectList(
                new LambdaQueryWrapper<SysPermissionRecovery>()
                        .eq(SysPermissionRecovery::getTaskId, orderNo)
                        .eq(SysPermissionRecovery::getStatus, 0)
        );

        for (SysPermissionRecovery recovery : recoveries) {
            try {
                permissionRecoveryService.executeRecovery(recovery.getId());
            } catch (Exception e) {
                log.error("执行回收失败: recoveryId={}", recovery.getId(), e);
            }
        }

        updateWorkOrderStatus(orderNo);
    }

    private List<SysRecoveryRetryLog> getRetryLogs(Long recoveryId) {
        return retryLogMapper.selectList(
                new LambdaQueryWrapper<SysRecoveryRetryLog>()
                        .eq(SysRecoveryRetryLog::getRecoveryId, recoveryId)
                        .orderByAsc(SysRecoveryRetryLog::getAttemptNumber)
        );
    }

    private String generateOrderNo(String triggerType) {
        String prefix = "RCV";
        if ("LEAVE".equals(triggerType)) {
            prefix = "RCV-LEAVE";
        } else if ("TRANSFER".equals(triggerType)) {
            prefix = "RCV-TRANSFER";
        } else if ("TEMP_EXPIRE".equals(triggerType)) {
            prefix = "RCV-TEMP";
        }

        return prefix + "-" + LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                String.format("%04d", new Random().nextInt(10000));
    }

    private RecoveryWorkOrderResponse buildWorkOrderResponse(SysRecoveryWorkOrder workOrder, List<WorkOrderItem> items) {
        List<SysRecoveryRetryLog> allLogs = new ArrayList<>();
        if (items != null) {
            for (WorkOrderItem item : items) {
                allLogs.addAll(retryLogMapper.selectList(
                        new LambdaQueryWrapper<SysRecoveryRetryLog>()
                                .eq(SysRecoveryRetryLog::getRecoveryId, item.getId())
                                .orderByAsc(SysRecoveryRetryLog::getAttemptNumber)
                ));
            }
        }

        List<RetryAttemptRecord> retryHistory = allLogs.stream()
                .map(log -> RetryAttemptRecord.builder()
                        .id(log.getId())
                        .attemptNumber(log.getAttemptNumber())
                        .status(log.getStatus())
                        .errorMessage(log.getErrorMessage())
                        .executedTime(log.getExecutedTime())
                        .responseResult(log.getResponseResult())
                        .build())
                .collect(Collectors.toList());

        return RecoveryWorkOrderResponse.builder()
                .orderNo(workOrder.getOrderNo())
                .triggerType(workOrder.getTriggerType())
                .status(workOrder.getStatus())
                .totalPermissions(workOrder.getTotalPermissions())
                .pendingCount(workOrder.getPendingCount())
                .successCount(workOrder.getSuccessCount())
                .failedCount(workOrder.getFailedCount())
                .items(items)
                .retryHistory(retryHistory)
                .createdTime(workOrder.getCreatedTime())
                .updatedTime(workOrder.getUpdatedTime())
                .build();
    }

    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待回收";
            case 1: return "已成功";
            case 2: return "已失败";
            default: return "未知";
        }
    }
}
