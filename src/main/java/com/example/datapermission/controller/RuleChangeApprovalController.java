package com.example.datapermission.controller;

import com.example.datapermission.dto.ImpactEstimateResponse;
import com.example.datapermission.dto.RuleChangeApprovalRequest;
import com.example.datapermission.dto.RuleChangeApprovalResponse;
import com.example.datapermission.dto.RuleSimulationRequest;
import com.example.datapermission.service.ImpactEstimateService;
import com.example.datapermission.service.DeepRuleSimulationService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/rule-change")
@RequiredArgsConstructor
public class RuleChangeApprovalController {

    private final ImpactEstimateService impactEstimateService;
    private final DeepRuleSimulationService simulationService;

    @PostMapping("/estimate")
    public Result<ImpactEstimateResponse> estimateImpact(@RequestBody RuleSimulationRequest request) {
        ImpactEstimateResponse response = impactEstimateService.estimateImpact(request);
        return Result.success(response);
    }

    @PostMapping("/approval")
    public Result<RuleChangeApprovalResponse> createApproval(
            @RequestBody RuleChangeApprovalRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        RuleChangeApprovalResponse response = impactEstimateService.createApproval(request, userId);
        return Result.success(response);
    }

    @PostMapping("/approval/{approvalNo}/approve")
    public Result<RuleChangeApprovalResponse> approve(
            @PathVariable String approvalNo,
            @RequestParam Long approverId,
            @RequestParam(required = false) String comment) {
        RuleChangeApprovalResponse response = impactEstimateService.approve(approvalNo, approverId, comment);
        return Result.success(response);
    }

    @PostMapping("/approval/{approvalNo}/reject")
    public Result<RuleChangeApprovalResponse> reject(
            @PathVariable String approvalNo,
            @RequestParam Long approverId,
            @RequestParam(required = false) String comment) {
        RuleChangeApprovalResponse response = impactEstimateService.reject(approvalNo, approverId, comment);
        return Result.success(response);
    }

    @GetMapping("/approval/{approvalNo}")
    public Result<RuleChangeApprovalResponse> getApproval(@PathVariable String approvalNo) {
        RuleChangeApprovalResponse response = impactEstimateService.getApproval(approvalNo);
        return Result.success(response);
    }

    @GetMapping("/approval")
    public Result<List<RuleChangeApprovalResponse>> listApprovals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long approverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<RuleChangeApprovalResponse> approvals = impactEstimateService.listApprovals(status, approverId, page, size);
        return Result.success(approvals);
    }

    @GetMapping("/approval/pending")
    public Result<List<RuleChangeApprovalResponse>> listPendingApprovals(
            @RequestParam(required = false) Long approverId) {
        List<RuleChangeApprovalResponse> approvals = impactEstimateService.listApprovals("PENDING", approverId, 1, 100);
        return Result.success(approvals);
    }
}
