package com.example.datapermission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.entity.SysAccessLog;
import com.example.datapermission.entity.SysAnomalyAlert;
import com.example.datapermission.entity.SysPermissionChangeLog;
import com.example.datapermission.mapper.SysAccessLogMapper;
import com.example.datapermission.mapper.SysAnomalyAlertMapper;
import com.example.datapermission.mapper.SysPermissionChangeLogMapper;
import com.example.datapermission.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final SysPermissionChangeLogMapper changeLogMapper;
    private final SysAccessLogMapper accessLogMapper;
    private final SysAnomalyAlertMapper anomalyAlertMapper;
    private final SysUserService userService;
    private final SysResourceService resourceService;

    public void logPermissionChange(Long permissionId, String changeType, Object beforeValue, Object afterValue,
                                    String changeReason, Long changeBy, String clientIp, String userAgent) {
        SysPermissionChangeLog log = new SysPermissionChangeLog();
        log.setPermissionId(permissionId);
        log.setChangeType(changeType);
        log.setBeforeValue(beforeValue != null ? JsonUtil.toJsonString(beforeValue) : null);
        log.setAfterValue(afterValue != null ? JsonUtil.toJsonString(afterValue) : null);
        log.setChangeReason(changeReason);
        log.setChangeBy(changeBy);
        log.setChangeTime(LocalDateTime.now());
        log.setClientIp(clientIp);
        log.setUserAgent(userAgent);
        changeLogMapper.insert(log);
    }

    public Page<SysPermissionChangeLog> queryChangeLogs(Long permissionId, String changeType,
                                                         LocalDateTime startDate, LocalDateTime endDate,
                                                         Integer pageNum, Integer pageSize) {
        Page<SysPermissionChangeLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysPermissionChangeLog> wrapper = new LambdaQueryWrapper<>();

        if (permissionId != null) {
            wrapper.eq(SysPermissionChangeLog::getPermissionId, permissionId);
        }
        if (changeType != null && !changeType.isEmpty()) {
            wrapper.eq(SysPermissionChangeLog::getChangeType, changeType);
        }
        if (startDate != null) {
            wrapper.ge(SysPermissionChangeLog::getChangeTime, startDate);
        }
        if (endDate != null) {
            wrapper.le(SysPermissionChangeLog::getChangeTime, endDate);
        }

        wrapper.orderByDesc(SysPermissionChangeLog::getChangeTime);
        return changeLogMapper.selectPage(page, wrapper);
    }

    public Page<SysAccessLog> queryAccessLogs(Long userId, Long resourceId, String accessDecision,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              Integer pageNum, Integer pageSize) {
        Page<SysAccessLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(SysAccessLog::getUserId, userId);
        }
        if (resourceId != null) {
            wrapper.eq(SysAccessLog::getResourceId, resourceId);
        }
        if (accessDecision != null && !accessDecision.isEmpty()) {
            wrapper.eq(SysAccessLog::getAccessDecision, accessDecision);
        }
        if (startDate != null) {
            wrapper.ge(SysAccessLog::getCreatedTime, startDate);
        }
        if (endDate != null) {
            wrapper.le(SysAccessLog::getCreatedTime, endDate);
        }

        wrapper.orderByDesc(SysAccessLog::getCreatedTime);
        return accessLogMapper.selectPage(page, wrapper);
    }

    public List<SysAnomalyAlert> getUnhandledAlerts() {
        LambdaQueryWrapper<SysAnomalyAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAnomalyAlert::getHandleStatus, 0)
                .orderByDesc(SysAnomalyAlert::getCreatedTime);
        return anomalyAlertMapper.selectList(wrapper);
    }

    public void handleAlert(Long alertId, Long handleBy, String handleResult) {
        SysAnomalyAlert alert = anomalyAlertMapper.selectById(alertId);
        if (alert != null) {
            alert.setHandleStatus(1);
            alert.setHandleBy(handleBy);
            alert.setHandleTime(LocalDateTime.now());
            alert.setHandleResult(handleResult);
            anomalyAlertMapper.updateById(alert);
        }
    }

    public Map<String, Object> getStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new java.util.HashMap<>();

        LambdaQueryWrapper<SysPermissionChangeLog> changeWrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            changeWrapper.ge(SysPermissionChangeLog::getChangeTime, startDate);
        }
        if (endDate != null) {
            changeWrapper.le(SysPermissionChangeLog::getChangeTime, endDate);
        }
        Long changeCount = changeLogMapper.selectCount(changeWrapper);
        stats.put("permissionChangeCount", changeCount);

        LambdaQueryWrapper<SysAccessLog> accessWrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            accessWrapper.ge(SysAccessLog::getCreatedTime, startDate);
        }
        if (endDate != null) {
            accessWrapper.le(SysAccessLog::getCreatedTime, endDate);
        }
        Long accessCount = accessLogMapper.selectCount(accessWrapper);
        stats.put("accessCount", accessCount);

        LambdaQueryWrapper<SysAccessLog> denyWrapper = new LambdaQueryWrapper<>();
        denyWrapper.eq(SysAccessLog::getAccessDecision, "DENY");
        if (startDate != null) {
            denyWrapper.ge(SysAccessLog::getCreatedTime, startDate);
        }
        if (endDate != null) {
            denyWrapper.le(SysAccessLog::getCreatedTime, endDate);
        }
        Long denyCount = accessLogMapper.selectCount(denyWrapper);
        stats.put("denyCount", denyCount);

        LambdaQueryWrapper<SysAnomalyAlert> alertWrapper = new LambdaQueryWrapper<>();
        alertWrapper.eq(SysAnomalyAlert::getHandleStatus, 0);
        if (startDate != null) {
            alertWrapper.ge(SysAnomalyAlert::getCreatedTime, startDate);
        }
        if (endDate != null) {
            alertWrapper.le(SysAnomalyAlert::getCreatedTime, endDate);
        }
        Long unhandledAlertCount = anomalyAlertMapper.selectCount(alertWrapper);
        stats.put("unhandledAlertCount", unhandledAlertCount);

        return stats;
    }
}
