package com.example.datapermission.controller;

import com.example.datapermission.dto.LeaveRequest;
import com.example.datapermission.dto.TransferRequest;
import com.example.datapermission.service.PermissionWorkflowService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserWorkflowController {

    private final PermissionWorkflowService workflowService;

    @PostMapping("/{userId}/leave")
    public Result<LeaveRequest.LeaveProgress> initiateLeave(
            @PathVariable Long userId,
            @RequestBody LeaveRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        LeaveRequest.LeaveProgress progress = workflowService.initiateLeaveProcess(userId, request);
        return Result.success(progress);
    }

    @GetMapping("/{userId}/leave/progress")
    public Result<LeaveRequest.LeaveProgress> getLeaveProgress(@PathVariable Long userId) {
        return Result.success(null);
    }

    @PostMapping("/{userId}/leave/cancel")
    public Result<Void> cancelLeave(@PathVariable Long userId) {
        return Result.success();
    }

    @PostMapping("/{userId}/transfer")
    public Result<TransferRequest.TransferResult> initiateTransfer(
            @PathVariable Long userId,
            @RequestBody TransferRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        TransferRequest.TransferResult result = workflowService.initiateTransferProcess(userId, request);
        return Result.success(result);
    }

    @GetMapping("/{userId}/transfer/progress")
    public Result<TransferRequest.TransferResult> getTransferProgress(@PathVariable Long userId) {
        return Result.success(null);
    }
}
