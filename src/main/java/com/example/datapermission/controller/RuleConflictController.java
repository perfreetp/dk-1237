package com.example.datapermission.controller;

import com.example.datapermission.dto.RuleConflictRequest;
import com.example.datapermission.dto.RuleConflictResponse;
import com.example.datapermission.service.RuleConflictService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/org-scope")
@RequiredArgsConstructor
public class RuleConflictController {

    private final RuleConflictService ruleConflictService;

    @GetMapping("/conflict/analyze/{orgId}")
    public Result<RuleConflictResponse> analyzeConflicts(@PathVariable Long orgId) {
        return Result.success(ruleConflictService.analyzeConflicts(orgId));
    }

    @GetMapping("/conflict/analyze")
    public Result<RuleConflictResponse> analyzeConflictsWithTypes(
            @RequestParam Long sourceOrgId,
            @RequestParam(required = false) List<String> grantTypes) {
        return Result.success(ruleConflictService.analyzeConflictsWithTypes(sourceOrgId, grantTypes));
    }

    @PostMapping("/conflict/simulate/{orgId}")
    public Result<RuleConflictResponse> simulateRuleChange(
            @PathVariable Long orgId,
            @RequestBody RuleConflictRequest request) {
        return Result.success(ruleConflictService.simulateRuleChange(orgId, request));
    }

    @GetMapping("/visualize/{orgId}")
    public Result<RuleConflictResponse> visualizeRules(@PathVariable Long orgId) {
        RuleConflictResponse response = ruleConflictService.analyzeConflicts(orgId);
        return Result.success(response);
    }
}
