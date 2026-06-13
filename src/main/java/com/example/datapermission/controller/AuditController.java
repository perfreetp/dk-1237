package com.example.datapermission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.entity.SysAccessLog;
import com.example.datapermission.entity.SysAnomalyAlert;
import com.example.datapermission.entity.SysPermissionChangeLog;
import com.example.datapermission.service.AuditService;
import com.example.datapermission.vo.PageResult;
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
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/permission-change/page")
    public Result<PageResult<SysPermissionChangeLog>> queryPermissionChangeLogs(
            @RequestParam(required = false) Long permissionId,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<SysPermissionChangeLog> page = auditService.queryChangeLogs(permissionId, changeType, startDate, endDate, pageNum, pageSize);
        PageResult<SysPermissionChangeLog> result = new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
        return Result.success(result);
    }

    @GetMapping("/access-log/page")
    public Result<PageResult<SysAccessLog>> queryAccessLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) String accessDecision,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<SysAccessLog> page = auditService.queryAccessLogs(userId, resourceId, accessDecision, startDate, endDate, pageNum, pageSize);
        PageResult<SysAccessLog> result = new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
        return Result.success(result);
    }

    @GetMapping("/alert/unhandled")
    public Result<List<SysAnomalyAlert>> getUnhandledAlerts() {
        return Result.success(auditService.getUnhandledAlerts());
    }

    @PutMapping("/alert/{id}/handle")
    public Result<Void> handleAlert(@PathVariable Long id,
                                    @RequestAttribute(value = "userId", required = false) Long handleBy,
                                    @RequestParam String handleResult) {
        auditService.handleAlert(id, handleBy, handleResult);
        return Result.success();
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        return Result.success(auditService.getStatistics(startDate, endDate));
    }
}
