package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.AlertDetailResponse;
import com.example.datapermission.dto.PermissionExportRequest;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDetailService {

    private final SysAnomalyAlertMapper alertMapper;
    private final SysAccessLogMapper accessLogMapper;
    private final SysUserMapper userMapper;
    private final SysResourceMapper resourceMapper;

    public AlertDetailResponse getAlertDetail(Long alertId) {
        SysAnomalyAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new RuntimeException("预警记录不存在");
        }

        AlertDetailResponse response = new AlertDetailResponse();
        response.setAlertId(alert.getId());
        response.setUserId(alert.getUserId());
        response.setAlertType(alert.getAlertType());
        response.setAlertContent(alert.getAlertContent());
        response.setAlertLevel(alert.getAlertLevel());
        response.setRiskScore(alert.getRiskScore());
        response.setCreatedTime(alert.getCreatedTime());
        response.setHandleStatus(alert.getHandleStatus() == 0 ? "PENDING" : "HANDLED");
        response.setHandleStatusName(alert.getHandleStatus() == 0 ? "待处理" : "已处理");
        response.setHandleTime(alert.getHandleTime());
        response.setHandleResult(alert.getHandleResult());

        if (alert.getUserId() != null) {
            SysUser user = userMapper.selectById(alert.getUserId());
            if (user != null) {
                response.setUserName(user.getUsername());
            }
        }

        if (alert.getTriggeredDimensions() != null) {
            response.setTriggeredDimensions(parseTriggeredDimensions(alert.getTriggeredDimensions()));
        }

        response.setRelatedAccessLogs(queryRelatedAccessLogs(alert));
        response.setSuggestions(parseSuggestions(alert.getSuggestions()));

        response.setUserBehaviorStats(calculateUserBehaviorStats(alert.getUserId()));

        return response;
    }

    private List<AlertDetailResponse.TriggeredDimension> parseTriggeredDimensions(String json) {
        List<AlertDetailResponse.TriggeredDimension> dimensions = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(json);
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    AlertDetailResponse.TriggeredDimension dim = new AlertDetailResponse.TriggeredDimension();
                    dim.setDimension(obj.getString("dimension"));
                    dim.setDimensionName(getDimensionName(obj.getString("dimension")));
                    dim.setThreshold(obj.getInteger("threshold"));
                    dim.setActual(obj.getInteger("actual"));
                    dim.setScore(obj.getInteger("score"));
                    dim.setDescription(obj.getString("description"));
                    dimensions.add(dim);
                }
            }
        } catch (Exception e) {
            log.error("解析触发维度失败", e);
        }
        return dimensions;
    }

    private String getDimensionName(String dimension) {
        switch (dimension) {
            case "DOWNLOAD_COUNT": return "下载次数";
            case "DATA_VOLUME": return "数据量";
            case "SENSITIVE_FIELD_COUNT": return "敏感字段数量";
            case "ACCESS_FREQUENCY": return "访问频率";
            case "OFF_HOURS_ACCESS": return "非工作时间访问";
            default: return dimension;
        }
    }

    private List<AlertDetailResponse.AccessLogDetail> queryRelatedAccessLogs(SysAnomalyAlert alert) {
        List<AlertDetailResponse.AccessLogDetail> logs = new ArrayList<>();

        try {
            if (alert.getRelatedAccessLogs() != null) {
                List<Long> logIds = JSON.parseArray(alert.getRelatedAccessLogs(), Long.class);
                if (logIds != null && !logIds.isEmpty()) {
                    for (Long logId : logIds) {
                        SysAccessLog accessLog = accessLogMapper.selectById(logId);
                        if (accessLog != null) {
                            logs.add(convertToAccessLogDetail(accessLog));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询关联访问日志失败", e);
        }

        if (logs.isEmpty()) {
            LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysAccessLog::getUserId, alert.getUserId())
                    .ge(SysAccessLog::getCreatedTime, alert.getCreatedTime().minusHours(1))
                    .le(SysAccessLog::getCreatedTime, alert.getCreatedTime().plusHours(1))
                    .orderByDesc(SysAccessLog::getCreatedTime)
                    .last("LIMIT 10");

            List<SysAccessLog> recentLogs = accessLogMapper.selectList(wrapper);
            for (SysAccessLog log : recentLogs) {
                logs.add(convertToAccessLogDetail(log));
            }
        }

        return logs;
    }

    private AlertDetailResponse.AccessLogDetail convertToAccessLogDetail(SysAccessLog accessLog) {
        AlertDetailResponse.AccessLogDetail detail = new AlertDetailResponse.AccessLogDetail();
        detail.setLogId(accessLog.getId());
        detail.setAccessTime(accessLog.getCreatedTime());
        detail.setOperationType(accessLog.getOperationType());
        detail.setAccessDecision(accessLog.getAccessDecision());
        detail.setRecordCount(accessLog.getRecordCount());
        detail.setDataVolume(accessLog.getDataVolume());
        detail.setClientIp(accessLog.getClientIp());
        detail.setExecutionTimeMs(accessLog.getExecutionTimeMs());
        detail.setViewUrl("/audit/access-log/" + accessLog.getId());

        if (accessLog.getResourceId() != null) {
            SysResource resource = resourceMapper.selectById(accessLog.getResourceId());
            if (resource != null) {
                detail.setResourceCode(resource.getResourceCode());
                detail.setResourceName(resource.getResourceName());
            }
        }

        if (accessLog.getSensitiveFieldsAccessed() != null) {
            try {
                List<String> fields = JSON.parseArray(accessLog.getSensitiveFieldsAccessed(), String.class);
                detail.setSensitiveFieldsAccessed(fields);
            } catch (Exception e) {
                log.error("解析敏感字段失败", e);
            }
        }

        return detail;
    }

    private List<String> parseSuggestions(String json) {
        List<String> suggestions = new ArrayList<>();
        try {
            if (json != null) {
                suggestions = JSON.parseArray(json, String.class);
            }
        } catch (Exception e) {
            log.error("解析建议失败", e);
        }
        return suggestions;
    }

    private AlertDetailResponse.UserBehaviorStats calculateUserBehaviorStats(Long userId) {
        AlertDetailResponse.UserBehaviorStats stats = new AlertDetailResponse.UserBehaviorStats();

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAccessLog::getUserId, userId)
                .ge(SysAccessLog::getCreatedTime, thirtyDaysAgo);

        List<SysAccessLog> logs = accessLogMapper.selectList(wrapper);

        int totalAccess = logs.size();
        int totalDownload = (int) logs.stream().filter(l -> "EXPORT".equals(l.getOperationType())).count();

        stats.setTotalAccessCount(totalAccess);
        stats.setTotalDownloadCount(totalDownload);
        stats.setAvgDailyAccess(totalAccess / 30);
        stats.setAvgDailyDownload(totalDownload / 30);

        if (!logs.isEmpty()) {
            stats.setLastAccessTime(logs.get(0).getCreatedTime());
        }

        LocalDateTime sevenDaysBefore = LocalDateTime.now().minusDays(7);
        int recentAccess = (int) logs.stream()
                .filter(l -> l.getCreatedTime().isAfter(sevenDaysBefore))
                .count();

        if (recentAccess > 0) {
            double anomalyScore = (recentAccess / 30.0) * 100;
            stats.setAnomalyScore(Math.min(anomalyScore, 100.0));
        } else {
            stats.setAnomalyScore(0.0);
        }

        return stats;
    }
}
