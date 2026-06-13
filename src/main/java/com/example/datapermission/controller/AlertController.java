package com.example.datapermission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.AlertHandleRequest;
import com.example.datapermission.entity.SysAnomalyAlert;
import com.example.datapermission.enums.AlertAction;
import com.example.datapermission.mapper.SysAnomalyAlertMapper;
import com.example.datapermission.service.AlertService;
import com.example.datapermission.vo.PageResult;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final SysAnomalyAlertMapper alertMapper;

    @GetMapping("/page")
    public Result<PageResult<SysAnomalyAlert>> pageQuery(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(required = false) Integer handleStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        Page<SysAnomalyAlert> page = new Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAnomalyAlert> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(SysAnomalyAlert::getUserId, userId);
        }
        if (alertLevel != null) {
            wrapper.eq(SysAnomalyAlert::getAlertLevel, alertLevel);
        }
        if (handleStatus != null) {
            wrapper.eq(SysAnomalyAlert::getHandleStatus, handleStatus);
        }
        if (startDate != null) {
            wrapper.ge(SysAnomalyAlert::getCreatedTime, startDate);
        }
        if (endDate != null) {
            wrapper.le(SysAnomalyAlert::getCreatedTime, endDate);
        }

        wrapper.orderByDesc(SysAnomalyAlert::getCreatedTime);
        Page<SysAnomalyAlert> result = alertMapper.selectPage(page, wrapper);

        PageResult<SysAnomalyAlert> pageResult = new PageResult<>(
                result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
        return Result.success(pageResult);
    }

    @GetMapping("/{alertId}")
    public Result<SysAnomalyAlert> getById(@PathVariable Long alertId) {
        return Result.success(alertMapper.selectById(alertId));
    }

    @PutMapping("/{alertId}/handle")
    public Result<Void> handleAlert(@PathVariable Long alertId,
                                   @RequestAttribute(value = "userId", required = false) Long handleBy,
                                   @RequestBody AlertHandleRequest request) {
        alertService.handleAlert(alertId, handleBy, request);
        return Result.success();
    }

    @PostMapping("/{alertId}/block-user")
    public Result<Void> blockUser(@PathVariable Long alertId,
                                  @RequestAttribute(value = "userId", required = false) Long operatorId,
                                  @RequestParam String reason) {
        alertService.blockUser(alertId, operatorId, reason);
        return Result.success();
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        return Result.success(alertService.getStatistics(startDate, endDate));
    }
}
