package com.example.datapermission.controller;

import com.example.datapermission.dto.RuleSimulationRequest;
import com.example.datapermission.dto.RuleSimulationResponse;
import com.example.datapermission.service.DeepRuleSimulationService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/rule-simulation")
@RequiredArgsConstructor
public class RuleSimulationController {

    private final DeepRuleSimulationService deepRuleSimulationService;

    @PostMapping("/preview")
    public Result<RuleSimulationResponse> simulateRules(@RequestBody RuleSimulationRequest request) {
        RuleSimulationResponse response = deepRuleSimulationService.simulateRules(request);
        return Result.success(response);
    }

    @PostMapping("/preview/user/{userId}")
    public Result<RuleSimulationResponse> simulateUserRules(
            @PathVariable Long userId,
            @RequestBody RuleSimulationRequest request) {
        request.setUserId(userId);
        RuleSimulationResponse response = deepRuleSimulationService.simulateRules(request);
        return Result.success(response);
    }

    @PostMapping("/preview/scenario/{scenario}")
    public Result<RuleSimulationResponse> simulateScenarioRules(
            @PathVariable String scenario,
            @RequestBody RuleSimulationRequest request) {
        request.setBusinessScenario(scenario);
        RuleSimulationResponse response = deepRuleSimulationService.simulateRules(request);
        return Result.success(response);
    }

    @PostMapping("/save")
    public Result<Map<String, Object>> saveSimulatedRules(@RequestBody RuleSimulationRequest request) {
        Map<String, Object> result = deepRuleSimulationService.saveSimulatedRules(request);
        return Result.success(result);
    }

    @PostMapping("/preview/org-adjustment")
    public Result<RuleSimulationResponse> previewOrgAdjustment(
            @RequestBody RuleSimulationRequest request) {
        RuleSimulationResponse response = deepRuleSimulationService.simulateRules(request);
        return Result.success(response);
    }

    @PostMapping("/preview/project-adjustment")
    public Result<RuleSimulationResponse> previewProjectAdjustment(
            @RequestBody RuleSimulationRequest request) {
        RuleSimulationResponse response = deepRuleSimulationService.simulateRules(request);
        return Result.success(response);
    }

    @PostMapping("/preview/field-adjustment")
    public Result<RuleSimulationResponse> previewFieldAdjustment(
            @RequestBody RuleSimulationRequest request) {
        RuleSimulationResponse response = deepRuleSimulationService.simulateRules(request);
        return Result.success(response);
    }

    @PostMapping("/preview/comparison")
    public Result<Map<String, Object>> compareRules(
            @RequestBody Map<String, RuleSimulationRequest> requests) {
        RuleSimulationRequest before = requests.get("before");
        RuleSimulationRequest after = requests.get("after");

        RuleSimulationResponse beforeResult = deepRuleSimulationService.simulateRules(before);
        RuleSimulationResponse afterResult = deepRuleSimulationService.simulateRules(after);

        Map<String, Object> comparison = buildComparisonResult(beforeResult, afterResult);
        return Result.success(comparison);
    }

    private Map<String, Object> buildComparisonResult(RuleSimulationResponse before, RuleSimulationResponse after) {
        Map<String, Object> result = new java.util.HashMap<>();

        result.put("before", before);
        result.put("after", after);

        int beforeOrgCount = before.getPreview() != null && before.getPreview().getVisibleOrgs() != null ?
                before.getPreview().getVisibleOrgs().size() : 0;
        int afterOrgCount = after.getPreview() != null && after.getPreview().getVisibleOrgs() != null ?
                after.getPreview().getVisibleOrgs().size() : 0;

        int beforeFieldCount = before.getPreview() != null && before.getPreview().getVisibleFields() != null ?
                before.getPreview().getVisibleFields().size() : 0;
        int afterFieldCount = after.getPreview() != null && after.getPreview().getVisibleFields() != null ?
                after.getPreview().getVisibleFields().size() : 0;

        result.put("orgChange", afterOrgCount - beforeOrgCount);
        result.put("fieldChange", afterFieldCount - beforeFieldCount);
        result.put("statusChange", !before.getStatus().equals(after.getStatus()));

        return result;
    }

    @GetMapping("/scenarios")
    public Result<List<String>> getAvailableScenarios() {
        List<String> scenarios = List.of(
                "CUSTOMER_QUERY",
                "SALES_REPORT",
                "FINANCIAL_AUDIT",
                "PROJECT_MANAGEMENT",
                "DATA_EXPORT",
                "BATCH_OPERATION",
                "ADMIN_VIEW"
        );
        return Result.success(scenarios);
    }

    @GetMapping("/adjustment-templates")
    public Result<Map<String, Object>> getAdjustmentTemplates() {
        Map<String, Object> templates = Map.of(
                "orgScope", Map.of(
                        "types", List.of("HIERARCHY", "CUSTOM", "BRANCH"),
                        "depthOptions", List.of(1, 2, 3, 4, 5),
                        "includeModes", List.of("INCLUDE_ONLY", "EXCLUDE_SELF", "EXCLUDE_CHILDREN")
                ),
                "project", Map.of(
                        "selectionModes", List.of("SPECIFIC", "TYPE", "ALL"),
                        "maxLimitOptions", List.of(10, 20, 50, 100, -1)
                ),
                "field", Map.of(
                        "desensitizationLevels", List.of(1, 2, 3, 4, 5),
                        "commonFields", List.of("phone", "email", "id_card", "bank_account", "address", "salary")
                )
        );
        return Result.success(templates);
    }
}
