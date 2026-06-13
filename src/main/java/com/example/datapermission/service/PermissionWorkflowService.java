package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.LeaveRequest;
import com.example.datapermission.dto.LeaveRequest.*;
import com.example.datapermission.dto.TransferRequest;
import com.example.datapermission.dto.TransferRequest.*;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
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
    private final SysUserMapper userMapper;
    private final SysUserPermissionService permissionService;
    private final SysUserPermissionMapper permissionMapper;
    private final SysPermissionTemplateService templateService;
    private final SysOrgScopeService orgScopeService;
    private final SysPermissionTaskMapper taskMapper;
    private final SysPermissionChangeLogMapper changeLogMapper;
    private final SysResourceMapper resourceMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysPostMapper postMapper;
    private final AuditService auditService;

    private static final DateTimeFormatter TASK_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional
    public LeaveProgress initiateLeaveProcess(Long userId, LeaveRequest request, Long operatorId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String taskId = generateTaskId("LT");
        List<SysUserPermission> activePermissions = permissionService.getActiveByUserId(userId);

        List<PermissionChange> affectedPermissions = new ArrayList<>();
        for (SysUserPermission permission : activePermissions) {
            SysResource resource = resourceMapper.selectById(permission.getResourceId());
            PermissionChange change = new PermissionChange();
            change.setPermissionId(permission.getId());
            change.setResourceCode(resource != null ? resource.getResourceCode() : "unknown");
            change.setResourceName(resource != null ? resource.getResourceName() : "未知资源");
            change.setStatus("PENDING");
            change.setAction("REVOKE");
            change.setReason(request.getReason());
            affectedPermissions.add(change);
        }

        List<StepProgress> steps = createLeaveSteps();

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
        task.setChangeBy(operatorId);
        task.setDueDate(request.getLeaveDate());
        task.setCreatedTime(LocalDateTime.now());
        taskMapper.insert(task);

        executeLeaveStep(taskId, "ACCOUNT_FREEZE", userId, operatorId);

        LeaveProgress progress = new LeaveProgress();
        progress.setTaskId(taskId);
        progress.setStatus("IN_PROGRESS");
        progress.setUserId(userId);
        progress.setUserName(user.getUsername());
        progress.setLeaveDate(request.getLeaveDate());
        progress.setSteps(queryStepProgress(taskId));
        progress.setAffectedPermissions(queryAffectedPermissions(taskId));

        if (request.getTransferToUserId() != null) {
            SysUser targetUser = userMapper.selectById(request.getTransferToUserId());
            TransferInfo transferInfo = new TransferInfo();
            transferInfo.setTargetUserId(request.getTransferToUserId());
            transferInfo.setTargetUserName(targetUser != null ? targetUser.getUsername() : "未知");
            progress.setTransferInfo(transferInfo);
        }

        return progress;
    }

    public LeaveProgress getLeaveProgress(String taskId) {
        SysPermissionTask task = getTaskById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        SysUser user = userMapper.selectById(task.getUserId());

        LeaveProgress progress = new LeaveProgress();
        progress.setTaskId(taskId);
        progress.setStatus(task.getStatus());
        progress.setUserId(task.getUserId());
        progress.setUserName(user != null ? user.getUsername() : "未知");
        progress.setLeaveDate(task.getDueDate());
        progress.setSteps(queryStepProgress(taskId));
        progress.setAffectedPermissions(queryAffectedPermissions(taskId));

        if (task.getTargetUserId() != null) {
            SysUser targetUser = userMapper.selectById(task.getTargetUserId());
            TransferInfo transferInfo = new TransferInfo();
            transferInfo.setTargetUserId(task.getTargetUserId());
            transferInfo.setTargetUserName(targetUser != null ? targetUser.getUsername() : "未知");
            progress.setTransferInfo(transferInfo);
        }

        return progress;
    }

    public LeaveCompletionReport getLeaveCompletionReport(String taskId) {
        SysPermissionTask task = getTaskById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        SysUser user = userMapper.selectById(task.getUserId());

        List<LeaveRequest.PermissionChange> changeLog = JSON.parseArray(task.getAffectedPermissions(), LeaveRequest.PermissionChange.class);

        LeaveCompletionReport report = new LeaveCompletionReport();
        report.setTaskId(taskId);
        report.setUserId(task.getUserId());
        report.setUserName(user != null ? user.getUsername() : "未知");
        report.setCompletedTime(task.getCompletedTime());

        int total = changeLog != null ? changeLog.size() : 0;
        int transferred = (int) changeLog.stream().filter(c -> "TRANSFER".equals(c.getAction())).count();
        int revoked = (int) changeLog.stream().filter(c -> "REVOKE".equals(c.getAction())).count();

        report.setTotalPermissions(total);
        report.setTransferredCount(transferred);
        report.setRevokedCount(revoked);
        report.setChangeLog(changeLog);

        return report;
    }

    public List<PermissionChange> getUserRemainingPermissions(Long userId) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getUserId, userId)
                .eq(SysUserPermission::getStatus, 1);
        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);

        return permissions.stream().map(p -> {
            SysResource resource = resourceMapper.selectById(p.getResourceId());
            PermissionChange change = new PermissionChange();
            change.setPermissionId(p.getId());
            change.setResourceCode(resource != null ? resource.getResourceCode() : "unknown");
            change.setResourceName(resource != null ? resource.getResourceName() : "未知资源");
            change.setStatus(p.getStatus() == 1 ? "ACTIVE" : "INACTIVE");
            return change;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void executeLeaveStep(String taskId, String step, Long userId, Long operatorId) {
        SysPermissionTask task = getTaskById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        switch (step) {
            case "ACCOUNT_FREEZE":
                userService.leave(userId);
                updateStepStatus(task, "ACCOUNT_FREEZE", "COMPLETED", operatorId, "账户已冻结");
                task.setCurrentStep("PERMISSION_ASSESS");
                break;
            case "PERMISSION_ASSESS":
                updateStepStatus(task, "PERMISSION_ASSESS", "COMPLETED", operatorId, "权限评估完成");
                task.setCurrentStep("PERMISSION_TRANSFER");
                break;
            case "PERMISSION_TRANSFER":
                if (task.getTargetUserId() != null) {
                    transferPermissions(task, task.getUserId(), task.getTargetUserId());
                    updateStepStatus(task, "PERMISSION_TRANSFER", "COMPLETED", operatorId, "权限已交接");
                } else {
                    updateStepStatus(task, "PERMISSION_TRANSFER", "SKIPPED", operatorId, "无需交接");
                }
                task.setCurrentStep("PERMISSION_REVOKE");
                break;
            case "PERMISSION_REVOKE":
                revokeAllPermissions(task, task.getUserId(), operatorId);
                updateStepStatus(task, "PERMISSION_REVOKE", "COMPLETED", operatorId, "权限已回收");
                task.setCurrentStep("LEAVE_COMPLETE");
                break;
            case "LEAVE_COMPLETE":
                task.setStatus("COMPLETED");
                task.setCompletedTime(LocalDateTime.now());
                updateStepStatus(task, "LEAVE_COMPLETE", "COMPLETED", operatorId, "离职流程完成");
                break;
        }

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
            permissionMapper.insert(newPermission);

            permissionService.revoke(permission.getId());

            logPermissionChange(permission.getId(), "TRANSFER", permission, newPermission,
                    "离职权限交接", task.getChangeBy());
        }
    }

    private void revokeAllPermissions(SysPermissionTask task, Long userId, Long operatorId) {
        List<SysUserPermission> permissions = permissionService.getActiveByUserId(userId);

        for (SysUserPermission permission : permissions) {
            permissionService.revoke(permission.getId());

            logPermissionChange(permission.getId(), "REVOKE", permission, null,
                    "离职自动回收", operatorId);
        }
    }

    @Transactional
    public TransferResult initiateTransferProcess(Long userId, TransferRequest request, Long operatorId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String taskId = generateTaskId("TR");

        SysOrganization fromOrg = organizationMapper.selectById(user.getOrgId());
        SysOrganization toOrg = organizationMapper.selectById(request.getTargetOrgId());
        SysPost fromPost = user.getPostId() != null ? postMapper.selectById(user.getPostId()) : null;
        SysPost toPost = postMapper.selectById(request.getTargetPostId());

        List<SysUserPermission> currentPermissions = permissionService.getActiveByUserId(userId);

        TransferSummary summary = new TransferSummary();
        summary.setUserId(userId);
        summary.setUserName(user.getUsername());
        summary.setFromOrgName(fromOrg != null ? fromOrg.getOrgName() : "未知");
        summary.setToOrgName(toOrg != null ? toOrg.getOrgName() : "未知");
        summary.setFromPostName(fromPost != null ? fromPost.getPostName() : "无");
        summary.setToPostName(toPost != null ? toPost.getPostName() : "未知");
        summary.setTransferDate(request.getTransferDate());

        PermissionChanges changes = calculatePermissionChanges(currentPermissions, request);

        Comparison comparison = buildComparison(currentPermissions, changes);

        List<ChangeDetail> changeDetails = buildChangeDetails(changes, userId, operatorId);

        Map<String, Object> changeDetailsMap = new HashMap<>();
        changeDetailsMap.put("userId", userId);
        changeDetailsMap.put("fromOrgId", user.getOrgId());
        changeDetailsMap.put("toOrgId", request.getTargetOrgId());
        changeDetailsMap.put("toPostId", request.getTargetPostId());

        SysPermissionTask task = new SysPermissionTask();
        task.setTaskId(taskId);
        task.setTaskType("TRANSFER");
        task.setUserId(userId);
        task.setTargetUserId(userId);
        task.setStatus("COMPLETED");
        task.setCurrentStep("COMPLETED");
        task.setChangeDetails(JSON.toJSONString(changeDetailsMap));
        task.setChangeReason(request.getReason());
        task.setChangeBy(operatorId);
        task.setCompletedTime(LocalDateTime.now());
        task.setCreatedTime(LocalDateTime.now());
        taskMapper.insert(task);

        executeTransferChanges(changes, userId, operatorId);

        user.setOrgId(request.getTargetOrgId());
        user.setPostId(request.getTargetPostId());
        userService.update(userId, user);

        TransferResult result = new TransferResult();
        result.setTaskId(taskId);
        result.setStatus("COMPLETED");
        result.setSummary(summary);
        result.setPermissionChanges(changes);
        result.setComparison(comparison);
        result.setChangeDetails(changeDetails);

        return result;
    }

    public TransferProgress getTransferProgress(String taskId) {
        SysPermissionTask task = getTaskById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        SysUser user = userMapper.selectById(task.getUserId());
        Map<String, Object> details = JSON.parseObject(task.getChangeDetails());

        TransferSummary summary = new TransferSummary();
        summary.setUserId(task.getUserId());
        summary.setUserName(user != null ? user.getUsername() : "未知");
        summary.setFromOrgId((Long) details.get("fromOrgId"));
        summary.setToOrgId((Long) details.get("toOrgId"));

        List<ChangeDetail> changeLog = queryChangeLog(task.getUserId());

        TransferProgress progress = new TransferProgress();
        progress.setTaskId(taskId);
        progress.setStatus(task.getStatus());
        progress.setSummary(summary);
        progress.setChangeLog(changeLog);

        return progress;
    }

    public Comparison previewTransferPermissions(Long userId, TransferRequest request) {
        List<SysUserPermission> currentPermissions = permissionService.getActiveByUserId(userId);
        PermissionChanges changes = calculatePermissionChanges(currentPermissions, request);
        return buildComparison(currentPermissions, changes);
    }

    private PermissionChanges calculatePermissionChanges(List<SysUserPermission> currentPermissions, TransferRequest request) {
        PermissionChanges changes = new PermissionChanges();
        List<ChangeItem> toKeep = new ArrayList<>();
        List<ChangeItem> toRevoke = new ArrayList<>();
        List<ChangeItem> toGrant = new ArrayList<>();

        Set<Long> keepResourceIds = request.getKeepPermissions() != null ?
                request.getKeepPermissions().stream()
                        .map(TransferRequest.KeepPermission::getResourceId)
                        .collect(Collectors.toSet()) : Collections.emptySet();

        Set<Long> revokeResourceIds = request.getRevokePermissions() != null ?
                request.getRevokePermissions().stream()
                        .map(TransferRequest.RevokePermission::getResourceId)
                        .collect(Collectors.toSet()) : Collections.emptySet();

        List<SysUserPermission> newTemplatePermissions = calculateNewTemplatePermissions(
                request.getTargetOrgId(), request.getTargetPostId());

        for (SysUserPermission permission : currentPermissions) {
            SysResource resource = resourceMapper.selectById(permission.getResourceId());
            String resourceName = resource != null ? resource.getResourceName() : "未知资源";

            if (revokeResourceIds.contains(permission.getResourceId())) {
                ChangeItem item = new ChangeItem();
                item.setPermissionId(permission.getId());
                item.setResourceId(permission.getResourceId());
                item.setResourceName(resourceName);
                item.setRevoked(true);
                item.setReason(getRevokeReason(request, permission.getResourceId()));
                toRevoke.add(item);
            } else if (keepResourceIds.contains(permission.getResourceId())) {
                ChangeItem item = new ChangeItem();
                item.setPermissionId(permission.getId());
                item.setResourceId(permission.getResourceId());
                item.setResourceName(resourceName);
                item.setKept(true);
                item.setReason(getKeepReason(request, permission.getResourceId()));
                toKeep.add(item);
            } else {
                ChangeItem item = new ChangeItem();
                item.setPermissionId(permission.getId());
                item.setResourceId(permission.getResourceId());
                item.setResourceName(resourceName);
                item.setRevoked(true);
                item.setReason("原岗位权限，超出新岗位范围");
                toRevoke.add(item);
            }
        }

        for (SysUserPermission newPerm : newTemplatePermissions) {
            boolean exists = currentPermissions.stream()
                    .anyMatch(p -> p.getResourceId().equals(newPerm.getResourceId()));
            if (!exists) {
                SysResource resource = resourceMapper.selectById(newPerm.getResourceId());
                ChangeItem item = new ChangeItem();
                item.setResourceId(newPerm.getResourceId());
                item.setResourceName(resource != null ? resource.getResourceName() : "未知资源");
                item.setGranted(true);
                item.setFromTemplate("新岗位模板");
                toGrant.add(item);
            }
        }

        changes.setToKeep(toKeep);
        changes.setToRevoke(toRevoke);
        changes.setToGrant(toGrant);

        return changes;
    }

    private Comparison buildComparison(List<SysUserPermission> before, PermissionChanges changes) {
        Comparison comparison = new Comparison();

        BeforeAfterCount beforeCount = new BeforeAfterCount();
        beforeCount.setPermissionCount(before.size());
        beforeCount.setMaxLevel(before.stream()
                .mapToInt(p -> p.getFieldAccessLevel() != null ? p.getFieldAccessLevel() : 1)
                .max().orElse(1));
        comparison.setBefore(beforeCount);

        int afterCount = changes.getToKeep().size() + changes.getToGrant().size();
        BeforeAfterCount afterCountObj = new BeforeAfterCount();
        afterCountObj.setPermissionCount(afterCount);
        afterCountObj.setMaxLevel(changes.getToGrant().stream()
                .mapToInt(g -> 2)
                .max().orElse(1));
        comparison.setAfter(afterCountObj);

        comparison.setNewFields(changes.getToGrant().stream()
                .map(ChangeItem::getResourceName)
                .collect(Collectors.toList()));

        comparison.setRemovedFields(changes.getToRevoke().stream()
                .map(ChangeItem::getResourceName)
                .collect(Collectors.toList()));

        return comparison;
    }

    private List<ChangeDetail> buildChangeDetails(PermissionChanges changes, Long userId, Long operatorId) {
        List<ChangeDetail> details = new ArrayList<>();
        int index = 0;

        for (ChangeItem item : changes.getToKeep()) {
            ChangeDetail detail = new ChangeDetail();
            detail.setChangeId("CH" + System.currentTimeMillis() + index++);
            detail.setPermissionId(item.getPermissionId());
            detail.setResourceName(item.getResourceName());
            detail.setAction("KEEP");
            detail.setReason(item.getReason());
            detail.setChangeTime(LocalDateTime.now());
            details.add(detail);
        }

        for (ChangeItem item : changes.getToRevoke()) {
            ChangeDetail detail = new ChangeDetail();
            detail.setChangeId("CH" + System.currentTimeMillis() + index++);
            detail.setPermissionId(item.getPermissionId());
            detail.setResourceName(item.getResourceName());
            detail.setAction("REVOKE");
            detail.setBeforeValue("status=1, level=" + 3);
            detail.setAfterValue("status=0");
            detail.setReason(item.getReason());
            detail.setChangeTime(LocalDateTime.now());
            details.add(detail);
        }

        for (ChangeItem item : changes.getToGrant()) {
            ChangeDetail detail = new ChangeDetail();
            detail.setChangeId("CH" + System.currentTimeMillis() + index++);
            detail.setResourceName(item.getResourceName());
            detail.setAction("GRANT");
            detail.setAfterValue("status=1, level=2");
            detail.setReason("转岗自动授予（来自" + item.getFromTemplate() + "）");
            detail.setChangeTime(LocalDateTime.now());
            details.add(detail);
        }

        return details;
    }

    private void executeTransferChanges(PermissionChanges changes, Long userId, Long operatorId) {
        for (ChangeItem item : changes.getToRevoke()) {
            if (item.getPermissionId() != null) {
                List<SysUserPermission> permissions = permissionMapper.selectList(
                        new LambdaQueryWrapper<SysUserPermission>()
                                .eq(SysUserPermission::getUserId, userId)
                                .eq(SysUserPermission::getResourceId, item.getResourceId())
                );
                for (SysUserPermission permission : permissions) {
                    permissionService.revoke(permission.getId());
                }
            }
        }

        for (ChangeItem item : changes.getToGrant()) {
            SysUserPermission newPermission = new SysUserPermission();
            newPermission.setUserId(userId);
            newPermission.setResourceId(item.getResourceId());
            newPermission.setOrgScopeType("ALL");
            newPermission.setOperationTypes("READ");
            newPermission.setFieldAccessLevel(2);
            newPermission.setGrantType("TRANSFER");
            newPermission.setGrantReason("转岗自动授予");
            newPermission.setStatus(1);
            newPermission.setCreatedBy(operatorId);
            permissionMapper.insert(newPermission);
        }
    }

    private List<SysUserPermission> calculateNewTemplatePermissions(Long orgId, Long postId) {
        return new ArrayList<>();
    }

    private List<LeaveRequest.StepProgress> createLeaveSteps() {
        List<StepProgress> steps = new ArrayList<>();
        steps.add(createStep("ACCOUNT_FREEZE", "账户冻结", "PENDING"));
        steps.add(createStep("PERMISSION_ASSESS", "权限评估", "PENDING"));
        steps.add(createStep("PERMISSION_TRANSFER", "权限交接", "PENDING"));
        steps.add(createStep("PERMISSION_REVOKE", "权限回收", "PENDING"));
        steps.add(createStep("LEAVE_COMPLETE", "离职完成", "PENDING"));
        return steps;
    }

    private StepProgress createStep(String step, String stepName, String status) {
        StepProgress progress = new StepProgress();
        progress.setStep(step);
        progress.setStepName(stepName);
        progress.setStatus(status);
        progress.setStartTime(LocalDateTime.now());
        return progress;
    }

    private void updateStepStatus(SysPermissionTask task, String step, String status, Long operatorId, String notes) {
        List<StepProgress> steps = JSON.parseArray(task.getSteps(), StepProgress.class);
        for (StepProgress stepProgress : steps) {
            if (stepProgress.getStep().equals(step)) {
                stepProgress.setStatus(status);
                stepProgress.setCompletedTime(LocalDateTime.now());
                stepProgress.setOperator(operatorId != null ? "用户" + operatorId : "系统");
                stepProgress.setNotes(notes);
            }
        }
        task.setSteps(JSON.toJSONString(steps));
    }

    private List<StepProgress> queryStepProgress(String taskId) {
        SysPermissionTask task = getTaskById(taskId);
        if (task == null) return new ArrayList<>();
        return JSON.parseArray(task.getSteps(), StepProgress.class);
    }

    private List<PermissionChange> queryAffectedPermissions(String taskId) {
        SysPermissionTask task = getTaskById(taskId);
        if (task == null) return new ArrayList<>();
        return JSON.parseArray(task.getAffectedPermissions(), PermissionChange.class);
    }

    private List<ChangeDetail> queryChangeLog(Long userId) {
        LambdaQueryWrapper<SysPermissionChangeLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermissionChangeLog::getChangeBy, userId)
                .orderByDesc(SysPermissionChangeLog::getChangeTime)
                .last("LIMIT 50");

        return changeLogMapper.selectList(wrapper).stream().map(log -> {
            ChangeDetail detail = new ChangeDetail();
            detail.setChangeId("CH" + log.getId());
            detail.setPermissionId(log.getPermissionId());
            detail.setAction(log.getChangeType());
            detail.setBeforeValue(log.getBeforeValue());
            detail.setAfterValue(log.getAfterValue());
            detail.setReason(log.getChangeReason());
            detail.setChangeTime(log.getChangeTime());
            detail.setChangeBy(log.getChangeBy() != null ? "用户" + log.getChangeBy() : "系统");
            return detail;
        }).collect(Collectors.toList());
    }

    private SysPermissionTask getTaskById(String taskId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<SysPermissionTask>()
                        .eq(SysPermissionTask::getTaskId, taskId)
        ).stream().findFirst().orElse(null);
    }

    private void logPermissionChange(Long permissionId, String changeType, Object before, Object after, String reason, Long changeBy) {
        SysPermissionChangeLog log = new SysPermissionChangeLog();
        log.setPermissionId(permissionId);
        log.setChangeType(changeType);
        log.setBeforeValue(before != null ? JSON.toJSONString(before) : null);
        log.setAfterValue(after != null ? JSON.toJSONString(after) : null);
        log.setChangeReason(reason);
        log.setChangeBy(changeBy);
        log.setChangeTime(LocalDateTime.now());
        changeLogMapper.insert(log);
    }

    private String generateTaskId(String prefix) {
        return prefix + LocalDateTime.now().format(TASK_ID_FORMAT) +
                String.format("%04d", new Random().nextInt(10000));
    }

    private String getResourceCode(Long resourceId) {
        SysResource resource = resourceMapper.selectById(resourceId);
        return resource != null ? resource.getResourceCode() : "unknown";
    }

    private String getRevokeReason(TransferRequest request, Long resourceId) {
        if (request.getRevokePermissions() != null) {
            return request.getRevokePermissions().stream()
                    .filter(r -> r.getResourceId().equals(resourceId))
                    .map(TransferRequest.RevokePermission::getReason)
                    .findFirst()
                    .orElse("超出新岗位权限范围");
        }
        return "超出新岗位权限范围";
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
}
