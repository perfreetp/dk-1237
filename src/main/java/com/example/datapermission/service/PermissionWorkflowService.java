package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.datapermission.dto.LeaveRequest;
import com.example.datapermission.dto.TransferRequest;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
import com.example.datapermission.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionWorkflowService {

    private final SysUserService userService;
    private final SysUserPermissionService permissionService;
    private final SysPermissionTemplateService templateService;
    private final SysOrgScopeService orgScopeService;
    private final SysPermissionTaskMapper taskMapper;
    private final AuditService auditService;

    private static final DateTimeFormatter TASK_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional
    public LeaveRequest.LeaveProgress initiateLeaveProcess(Long userId, LeaveRequest request) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String taskId = generateTaskId("LT");
        List<SysUserPermission> activePermissions = permissionService.getActiveByUserId(userId);

        List<LeaveRequest.PermissionChange> affectedPermissions = new ArrayList<>();
        for (SysUserPermission permission : activePermissions) {
            LeaveRequest.PermissionChange change = new LeaveRequest.PermissionChange();
            change.setPermissionId(permission.getId());
            change.setResourceCode(getResourceCode(permission.getResourceId()));
            change.setStatus("PENDING");
            change.setAction("REVOKE");
            affectedPermissions.add(change);
        }

        List<LeaveRequest.StepProgress> steps = new ArrayList<>();
        steps.add(createStep("ACCOUNT_FREEZE", "PENDING"));
        steps.add(createStep("PERMISSION_ASSESS", "PENDING"));
        steps.add(createStep("PERMISSION_TRANSFER", request.getTransferPermissions() ? "PENDING" : "SKIP"));
        steps.add(createStep("PERMISSION_REVOKE", "PENDING"));
        steps.add(createStep("LEAVE_COMPLETE", "PENDING"));

        SysPermissionTask task = new SysPermissionTask();
        task.setTaskId(taskId);
        task.setTaskType("LEAVE");
        task.setUserId(userId);
        task.setTargetUserId(request.getTransferToUserId());
        task.setStatus("INITIATED");
        task.setCurrentStep("ACCOUNT_FREEZE");
        task.setSteps(JSON.toJSONString(steps));
        task.setAffectedPermissions(JSON.toJSONString(affectedPermissions));
        task.setChangeReason(request.getReason());
        task.setCreatedTime(LocalDateTime.now());
        taskMapper.insert(task);

        executeLeaveStep(taskId, "ACCOUNT_FREEZE", userId);

        LeaveRequest.LeaveProgress progress = new LeaveRequest.LeaveProgress();
        progress.setTaskId(taskId);
        progress.setStatus("INITIATED");
        progress.setSteps(steps);
        progress.setAffectedPermissions(affectedPermissions);
        return progress;
    }

    @Transactional
    public void executeLeaveStep(String taskId, String step, Long userId) {
        SysPermissionTask task = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermissionTask>()
                        .eq(SysPermissionTask::getTaskId, taskId)).stream().findFirst().orElse(null);

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        switch (step) {
            case "ACCOUNT_FREEZE":
                userService.leave(userId);
                task.setCurrentStep("PERMISSION_ASSESS");
                break;
            case "PERMISSION_ASSESS":
                task.setCurrentStep("PERMISSION_TRANSFER");
                break;
            case "PERMISSION_TRANSFER":
                Long targetUserId = task.getTargetUserId();
                if (targetUserId != null) {
                    transferPermissions(task, userId, targetUserId);
                }
                task.setCurrentStep("PERMISSION_REVOKE");
                break;
            case "PERMISSION_REVOKE":
                revokeAllPermissions(task, userId);
                task.setCurrentStep("LEAVE_COMPLETE");
                break;
            case "LEAVE_COMPLETE":
                task.setStatus("COMPLETED");
                task.setCompletedTime(LocalDateTime.now());
                break;
        }

        updateStepStatus(task, step, "COMPLETED");
        taskMapper.updateById(task);

        log.info("离职流程步骤执行完成: taskId={}, step={}", taskId, step);
    }

    private void transferPermissions(SysPermissionTask task, Long fromUserId, Long toUserId) {
        List<SysUserPermission> permissions = permissionService.getActiveByUserId(fromUserId);

        for (SysUserPermission permission : permissions) {
            SysUserPermission newPermission = new SysUserPermission();
            newPermission.setUserId(toUserId);
            newPermission.setResourceId(permission.getResourceId());
            newPermission.setOrgScopeType(permission.getOrgScopeType());
            newPermission.setOrgScopeValue(permission.getOrgScopeValue());
            newPermission.setPermissionTemplateId(permission.getPermissionTemplateId());
            newPermission.setOperationTypes(permission.getOperationTypes());
            newPermission.setFieldAccessLevel(permission.getFieldAccessLevel());
            newPermission.setDesensitizationEnabled(permission.getDesensitizationEnabled());
            newPermission.setGrantType("TRANSFER");
            newPermission.setSourceGrantId(permission.getId());
            newPermission.setStatus(1);
            newPermission.setGrantReason("离职权限交接");
            newPermission.setCreatedBy(task.getChangeBy());
            permissionService.create(newPermission);

            permissionService.revoke(permission.getId());

            auditService.logPermissionChange(
                    permission.getId(),
                    "TRANSFER",
                    permission,
                    newPermission,
                    "离职权限交接",
                    task.getChangeBy(),
                    null,
                    null
            );
        }
    }

    private void revokeAllPermissions(SysPermissionTask task, Long userId) {
        List<SysUserPermission> permissions = permissionService.getActiveByUserId(userId);

        for (SysUserPermission permission : permissions) {
            permissionService.revoke(permission.getId());

            auditService.logPermissionChange(
                    permission.getId(),
                    "REVOKE",
                    permission,
                    null,
                    "离职自动回收",
                    task.getChangeBy(),
                    null,
                    null
            );
        }
    }

    private void updateStepStatus(SysPermissionTask task, String step, String status) {
        List<LeaveRequest.StepProgress> steps = JSON.parseArray(task.getSteps(), LeaveRequest.StepProgress.class);
        for (LeaveRequest.StepProgress stepProgress : steps) {
            if (stepProgress.getStep().equals(step)) {
                stepProgress.setStatus(status);
                if ("COMPLETED".equals(status)) {
                    stepProgress.setCompletedTime(LocalDateTime.now());
                }
            }
        }
        task.setSteps(JSON.toJSONString(steps));
    }

    @Transactional
    public TransferRequest.TransferResult initiateTransferProcess(Long userId, TransferRequest request) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String taskId = generateTaskId("TR");

        List<SysUserPermission> currentPermissions = permissionService.getActiveByUserId(userId);

        TransferRequest.PermissionChanges changes = new TransferRequest.PermissionChanges();
        List<TransferRequest.ChangeItem> toKeep = new ArrayList<>();
        List<TransferRequest.ChangeItem> toRevoke = new ArrayList<>();
        List<TransferRequest.ChangeItem> toGrant = new ArrayList<>();

        Set<Long> keepResourceIds = request.getKeepPermissions() != null ?
                request.getKeepPermissions().stream()
                        .map(TransferRequest.KeepPermission::getResourceId)
                        .collect(Collectors.toSet()) : Collections.emptySet();

        Set<Long> revokeResourceIds = request.getRevokePermissions() != null ?
                request.getRevokePermissions().stream()
                        .map(TransferRequest.RevokePermission::getResourceId)
                        .collect(Collectors.toSet()) : Collections.emptySet();

        for (SysUserPermission permission : currentPermissions) {
            String resourceCode = getResourceCode(permission.getResourceId());

            if (revokeResourceIds.contains(permission.getResourceId())) {
                TransferRequest.ChangeItem item = new TransferRequest.ChangeItem();
                item.setResourceId(permission.getResourceId());
                item.setResourceName(resourceCode);
                item.setRevokeReason(getRevokeReason(request, permission.getResourceId()));
                toRevoke.add(item);
            } else if (keepResourceIds.contains(permission.getResourceId())) {
                TransferRequest.ChangeItem item = new TransferRequest.ChangeItem();
                item.setResourceId(permission.getResourceId());
                item.setResourceName(resourceCode);
                item.setKept(true);
                item.setReason(getKeepReason(request, permission.getResourceId()));
                toKeep.add(item);
            } else {
                toRevoke.add(createChangeItem(permission, "原岗位权限"));
            }
        }

        List<SysUserPermission> newTemplatePermissions = calculateNewTemplatePermissions(
                userId, request.getTargetPostId());

        for (SysUserPermission newPerm : newTemplatePermissions) {
            if (!currentPermissions.stream()
                    .anyMatch(p -> p.getResourceId().equals(newPerm.getResourceId()))) {
                TransferRequest.ChangeItem item = new TransferRequest.ChangeItem();
                item.setResourceId(newPerm.getResourceId());
                item.setResourceName(getResourceCode(newPerm.getResourceId()));
                item.setFromTemplate("新岗位模板");
                toGrant.add(item);
            }
        }

        changes.setToKeep(toKeep);
        changes.setToRevoke(toRevoke);
        changes.setToGrant(toGrant);

        Map<String, Object> changeDetails = new HashMap<>();
        changeDetails.put("userId", userId);
        changeDetails.put("fromOrgId", user.getOrgId());
        changeDetails.put("toOrgId", request.getTargetOrgId());
        changeDetails.put("toPostId", request.getTargetPostId());

        SysPermissionTask task = new SysPermissionTask();
        task.setTaskId(taskId);
        task.setTaskType("TRANSFER");
        task.setUserId(userId);
        task.setTargetUserId(userId);
        task.setStatus("INITIATED");
        task.setCurrentStep("PERMISSION_CALCULATE");
        task.setChangeDetails(JSON.toJSONString(changeDetails));
        task.setChangeReason(request.getReason());
        task.setCreatedTime(LocalDateTime.now());
        taskMapper.insert(task);

        executeTransferChanges(taskId, userId, changes);

        TransferRequest.TransferResult result = new TransferRequest.TransferResult();
        result.setTaskId(taskId);
        result.setStatus("COMPLETED");
        result.setPermissionChanges(changes);

        TransferRequest.Comparison comparison = new TransferRequest.Comparison();
        comparison.setBeforeCount(currentPermissions.size());
        comparison.setAfterCount(toKeep.size() + toGrant.size());
        Set<String> allFields = new HashSet<>();
        currentPermissions.forEach(p -> {
            if (p.getOperationTypes() != null) {
                Arrays.stream(p.getOperationTypes().split(","))
                        .forEach(allFields::add);
            }
        });
        Set<String> newFields = new HashSet<>();
        toGrant.forEach(g -> newFields.add(g.getResourceName()));
        comparison.setNewFields(new ArrayList<>(newFields));
        comparison.setRemovedFields(currentPermissions.stream()
                .filter(p -> toRevoke.stream()
                        .anyMatch(r -> r.getResourceId().equals(p.getResourceId())))
                .map(p -> getResourceCode(p.getResourceId()))
                .collect(Collectors.toList()));
        result.setComparison(comparison);

        user.setOrgId(request.getTargetOrgId());
        user.setPostId(request.getTargetPostId());
        userService.update(userId, user);

        auditService.logPermissionChange(
                null,
                "TRANSFER",
                currentPermissions,
                changes,
                "转岗权限变更: " + request.getReason(),
                null,
                null,
                null
        );

        return result;
    }

    private void executeTransferChanges(String taskId, Long userId, TransferRequest.PermissionChanges changes) {
        for (TransferRequest.ChangeItem item : changes.getToRevoke()) {
            List<SysUserPermission> permissions = permissionService.getByUserIdAndResourceId(
                    userId, item.getResourceId());
            for (SysUserPermission permission : permissions) {
                permissionService.revoke(permission.getId());
            }
        }

        for (TransferRequest.ChangeItem item : changes.getToGrant()) {
            SysUserPermission newPermission = new SysUserPermission();
            newPermission.setUserId(userId);
            newPermission.setResourceId(item.getResourceId());
            newPermission.setOrgScopeType("ALL");
            newPermission.setOperationTypes("READ");
            newPermission.setFieldAccessLevel(2);
            newPermission.setGrantType("TRANSFER");
            newPermission.setGrantReason("转岗自动授予");
            newPermission.setStatus(1);
            permissionService.create(newPermission);
        }
    }

    private List<SysUserPermission> calculateNewTemplatePermissions(Long userId, Long postId) {
        return new ArrayList<>();
    }

    private String generateTaskId(String prefix) {
        return prefix + LocalDateTime.now().format(TASK_ID_FORMAT) +
                String.format("%04d", new Random().nextInt(10000));
    }

    private LeaveRequest.StepProgress createStep(String step, String status) {
        LeaveRequest.StepProgress progress = new LeaveRequest.StepProgress();
        progress.setStep(step);
        progress.setStatus(status);
        return progress;
    }

    private String getResourceCode(Long resourceId) {
        return "resource_" + resourceId;
    }

    private String getRevokeReason(TransferRequest request, Long resourceId) {
        if (request.getRevokePermissions() != null) {
            return request.getRevokePermissions().stream()
                    .filter(r -> r.getResourceId().equals(resourceId))
                    .map(TransferRequest.RevokePermission::getReason)
                    .findFirst()
                    .orElse("原部门专属权限");
        }
        return "原岗位权限";
    }

    private String getKeepReason(TransferRequest request, Long resourceId) {
        if (request.getKeepPermissions() != null) {
            return request.getKeepPermissions().stream()
                    .filter(k -> k.getResourceId().equals(resourceId))
                    .map(TransferRequest.KeepPermission::getReason)
                    .findFirst()
                    .orElse("保留");
        }
        return "保留";
    }

    private TransferRequest.ChangeItem createChangeItem(SysUserPermission permission, String reason) {
        TransferRequest.ChangeItem item = new TransferRequest.ChangeItem();
        item.setResourceId(permission.getResourceId());
        item.setResourceName(getResourceCode(permission.getResourceId()));
        item.setRevokeReason(reason);
        return item;
    }
}
