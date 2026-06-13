package com.example.datapermission.controller;

import com.example.datapermission.dto.PermissionExportRequest;
import com.example.datapermission.dto.ReviewTaskRequest;
import com.example.datapermission.service.AuditExportService;
import com.example.datapermission.service.AuditInventoryService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/audit")
@RequiredArgsConstructor
public class AuditInventoryController {

    private final AuditInventoryService auditInventoryService;
    private final AuditExportService auditExportService;

    @GetMapping("/permission/export")
    public Result<Map<String, Object>> exportPermissions(
            @RequestParam String type,
            @RequestParam(defaultValue = "JSON") String format,
            @RequestParam(required = false) List<String> riskFilters,
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<Long> userIds,
            @RequestParam(required = false) List<String> resourceTypes,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {

        PermissionExportRequest request = new PermissionExportRequest();
        request.setType(type);
        request.setFormat(format);
        request.setRiskFilters(riskFilters);
        request.setOrgIds(orgIds);
        request.setUserIds(userIds);
        request.setResourceTypes(resourceTypes);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setIncludeChangeHistory(true);
        request.setIncludeStatistics(true);

        Map<String, Object> result = auditExportService.exportPermissions(request);
        return Result.success(result);
    }

    @GetMapping("/permission/export/expiring")
    public Result<Map<String, Object>> exportExpiringPermissions(
            @RequestParam(defaultValue = "7") Integer daysRemaining,
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<String> resourceTypes) {

        PermissionExportRequest request = new PermissionExportRequest();
        request.setOrgIds(orgIds);
        request.setResourceTypes(resourceTypes);

        Map<String, Object> result = auditExportService.exportExpiringPermissions(daysRemaining, request);
        return Result.success(result);
    }

    @GetMapping("/permission/export/unused")
    public Result<Map<String, Object>> exportUnusedPermissions(
            @RequestParam(defaultValue = "90") Integer unusedDays,
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<String> resourceTypes) {

        PermissionExportRequest request = new PermissionExportRequest();
        request.setOrgIds(orgIds);
        request.setResourceTypes(resourceTypes);

        Map<String, Object> result = auditExportService.exportUnusedPermissions(unusedDays, request);
        return Result.success(result);
    }

    @GetMapping("/permission/export/over-granted")
    public Result<Map<String, Object>> exportOverGrantedPermissions(
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<String> resourceTypes) {

        PermissionExportRequest request = new PermissionExportRequest();
        request.setOrgIds(orgIds);
        request.setResourceTypes(resourceTypes);

        Map<String, Object> result = auditExportService.exportOverGrantedPermissions(request);
        return Result.success(result);
    }

    @GetMapping("/permission/export/all")
    public Result<Map<String, Object>> exportAllRiskPermissions(
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<String> resourceTypes) {

        PermissionExportRequest request = new PermissionExportRequest();
        request.setOrgIds(orgIds);
        request.setResourceTypes(resourceTypes);
        request.setRiskFilters(List.of("EXPIRING", "UNUSED", "OVER_GRANTED"));

        Map<String, Object> result = auditExportService.exportPermissions(request);
        return Result.success(result);
    }

    @PostMapping("/review-task")
    public Result<Map<String, Object>> createReviewTask(@RequestBody ReviewTaskRequest request) {
        Map<String, Object> result = auditInventoryService.createReviewTask(request);
        return Result.success(result);
    }

    @GetMapping("/review-task/{taskId}/items")
    public Result<Map<String, Object>> getReviewTaskItems(@PathVariable String taskId) {
        return Result.success(Map.of("taskId", taskId, "message", "待实现"));
    }

    @PostMapping("/review-task/{taskId}/items/{itemId}/approve")
    public Result<Void> approveReviewItem(@PathVariable String taskId, @PathVariable Long itemId) {
        return Result.success();
    }

    @PostMapping("/review-task/{taskId}/items/{itemId}/revoke")
    public Result<Void> revokeReviewItem(@PathVariable String taskId, @PathVariable Long itemId) {
        return Result.success();
    }

    @GetMapping("/review-task/{taskId}/report")
    public Result<Map<String, Object>> getReviewTaskReport(@PathVariable String taskId) {
        return Result.success(Map.of("taskId", taskId, "message", "待实现"));
    }
}
