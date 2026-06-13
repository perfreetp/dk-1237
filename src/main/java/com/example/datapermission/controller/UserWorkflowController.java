package com.example.datapermission.controller;

import com.example.datapermission.dto.LeaveRequest;
import com.example.datapermission.dto.LeaveRequest.*;
import com.example.datapermission.dto.TransferRequest;
import com.example.datapermission.dto.TransferRequest.*;
import com.example.datapermission.service.PermissionWorkflowService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserWorkflowController {

    private final PermissionWorkflowService workflowService;

    @PostMapping("/{userId}/leave")
    public Result<LeaveProgress> initiateLeave(
            @PathVariable Long userId,
            @RequestBody LeaveRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        LeaveProgress progress = workflowService.initiateLeaveProcess(userId, request, operatorId);
        return Result.success(progress);
    }

    @GetMapping("/leave/progress/{taskId}")
    public Result<LeaveProgress> getLeaveProgress(@PathVariable String taskId) {
        LeaveProgress progress = workflowService.getLeaveProgress(taskId);
        return Result.success(progress);
    }

    @GetMapping("/leave/report/{taskId}")
    public Result<LeaveCompletionReport> getLeaveReport(@PathVariable String taskId) {
        LeaveCompletionReport report = workflowService.getLeaveCompletionReport(taskId);
        return Result.success(report);
    }

    @GetMapping("/{userId}/permissions")
    public Result<List<PermissionChange>> getUserRemainingPermissions(@PathVariable Long userId) {
        List<PermissionChange> permissions = workflowService.getUserRemainingPermissions(userId);
        return Result.success(permissions);
    }

    @PostMapping("/{userId}/leave/step")
    public Result<Void> executeLeaveStep(
            @PathVariable Long userId,
            @RequestParam String taskId,
            @RequestParam String step,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        workflowService.executeLeaveStep(taskId, step, userId, operatorId);
        return Result.success();
    }

    @PostMapping("/{userId}/leave/cancel")
    public Result<Void> cancelLeave(@PathVariable Long userId) {
        return Result.success();
    }

    @PostMapping("/{userId}/transfer")
    public Result<TransferResult> initiateTransfer(
            @PathVariable Long userId,
            @RequestBody TransferRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        TransferResult result = workflowService.initiateTransferProcess(userId, request, operatorId);
        return Result.success(result);
    }

    @GetMapping("/transfer/progress/{taskId}")
    public Result<TransferProgress> getTransferProgress(@PathVariable String taskId) {
        TransferProgress progress = workflowService.getTransferProgress(taskId);
        return Result.success(progress);
    }

    @PostMapping("/{userId}/transfer/preview")
    public Result<Comparison> previewTransfer(
            @PathVariable Long userId,
            @RequestBody TransferRequest request) {
        Comparison comparison = workflowService.previewTransferPermissions(userId, request);
        return Result.success(comparison);
    }
}
