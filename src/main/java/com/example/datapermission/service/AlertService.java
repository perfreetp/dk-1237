package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.AlertHandleRequest;
import com.example.datapermission.entity.SysAccessLog;
import com.example.datapermission.entity.SysAnomalyAlert;
import com.example.datapermission.entity.SysUser;
import com.example.datapermission.enums.AlertAction;
import com.example.datapermission.enums.AlertDimension;
import com.example.datapermission.enums.RiskLevel;
import com.example.datapermission.mapper.SysAccessLogMapper;
import com.example.datapermission.mapper.SysAnomalyAlertMapper;
import com.example.datapermission.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final SysAnomalyAlertMapper alertMapper;
    private final SysAccessLogMapper accessLogMapper;
    private final SysUserMapper userMapper;
    private final AuditService auditService;

    @Transactional
    public void checkAndCreateAlerts(Long userId) {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        Map<String, Integer> dimensionValues = calculateDimensionValues(userId, today);
        int totalScore = calculateRiskScore(dimensionValues);

        if (totalScore < 30) {
            return;
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        List<JSONObject> triggeredDimensions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dimensionValues.entrySet()) {
            AlertDimension dimension = AlertDimension.valueOf(entry.getKey());
            int score = dimension.calculateScore(entry.getValue());
            if (score > 0) {
                JSONObject dimJson = new JSONObject();
                dimJson.put("dimension", entry.getKey());
                dimJson.put("threshold", dimension.getWarningThreshold());
                dimJson.put("actual", entry.getValue());
                dimJson.put("score", score);
                dimJson.put("description", dimension.getDescription());
                triggeredDimensions.add(dimJson);
            }
        }

        List<Long> relatedLogIds = getRelatedAccessLogIds(userId, today);

        SysAnomalyAlert alert = new SysAnomalyAlert();
        alert.setUserId(userId);
        alert.setAlertType("COMPREHENSIVE");
        alert.setAlertContent(String.format("用户[%s]存在异常访问行为，综合风险评分: %d", user.getUsername(), totalScore));
        alert.setAlertLevel(RiskLevel.fromScore(totalScore).ordinal() + 1);
        alert.setRiskScore(totalScore);
        alert.setTriggeredDimensions(JSON.toJSONString(triggeredDimensions));
        alert.setRelatedAccessLogs(JSON.toJSONString(relatedLogIds));
        alert.setSuggestions(generateSuggestions(dimensionValues, totalScore));
        alert.setHandleStatus(0);
        alertMapper.insert(alert);

        log.info("创建综合预警: 用户ID={}, 风险评分={}, 触发维度={}",
                userId, totalScore, triggeredDimensions.size());
    }

    private Map<String, Integer> calculateDimensionValues(Long userId, LocalDateTime today) {
        Map<String, Integer> values = new HashMap<>();

        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAccessLog::getUserId, userId)
                .ge(SysAccessLog::getCreatedTime, today);

        List<SysAccessLog> logs = accessLogMapper.selectList(wrapper);

        int downloadCount = 0;
        long dataVolume = 0;
        Set<String> sensitiveFields = new HashSet<>();
        int accessCount = 0;
        int offHoursCount = 0;

        for (SysAccessLog logEntry : logs) {
            if ("EXPORT".equals(logEntry.getOperationType())) {
                downloadCount++;
            }

            if (logEntry.getDataVolume() != null) {
                dataVolume += logEntry.getDataVolume();
            }

            if (logEntry.getSensitiveFieldsAccessed() != null) {
                JSONArray fields = JSON.parseArray(logEntry.getSensitiveFieldsAccessed());
                if (fields != null) {
                    for (int i = 0; i < fields.size(); i++) {
                        sensitiveFields.add(fields.getString(i));
                    }
                }
            }

            accessCount++;

            int hour = logEntry.getCreatedTime().getHour();
            if (hour >= 22 || hour < 6) {
                offHoursCount++;
            }
        }

        values.put(AlertDimension.DOWNLOAD_COUNT.name(), downloadCount);
        values.put(AlertDimension.DATA_VOLUME.name(), (int) dataVolume);
        values.put(AlertDimension.SENSITIVE_FIELD_COUNT.name(), sensitiveFields.size());
        values.put(AlertDimension.ACCESS_FREQUENCY.name(), accessCount);
        values.put(AlertDimension.OFF_HOURS_ACCESS.name(), offHoursCount);

        return values;
    }

    private int calculateRiskScore(Map<String, Integer> dimensionValues) {
        int totalScore = 0;

        for (Map.Entry<String, Integer> entry : dimensionValues.entrySet()) {
            try {
                AlertDimension dimension = AlertDimension.valueOf(entry.getKey());
                int score = dimension.calculateScore(entry.getValue());
                totalScore += (int) (score * dimension.getWeight());
            } catch (IllegalArgumentException e) {
                log.warn("未知的预警维度: {}", entry.getKey());
            }
        }

        return Math.min(totalScore, 100);
    }

    private List<Long> getRelatedAccessLogIds(Long userId, LocalDateTime today) {
        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAccessLog::getUserId, userId)
                .ge(SysAccessLog::getCreatedTime, today)
                .orderByDesc(SysAccessLog::getCreatedTime)
                .last("LIMIT 10");

        return accessLogMapper.selectList(wrapper).stream()
                .map(SysAccessLog::getId)
                .toList();
    }

    private String generateSuggestions(Map<String, Integer> dimensionValues, int totalScore) {
        List<String> suggestions = new ArrayList<>();

        Integer downloadCount = dimensionValues.get(AlertDimension.DOWNLOAD_COUNT.name());
        if (downloadCount != null && downloadCount > 100) {
            suggestions.add("建议立即联系用户确认业务需求");
        }

        Integer dataVolume = dimensionValues.get(AlertDimension.DATA_VOLUME.name());
        if (dataVolume != null && dataVolume > 10000) {
            suggestions.add("建议审查该用户的数据导出行为");
        }

        Integer sensitiveCount = dimensionValues.get(AlertDimension.SENSITIVE_FIELD_COUNT.name());
        if (sensitiveCount != null && sensitiveCount > 10) {
            suggestions.add("建议检查该用户对敏感字段的访问情况");
        }

        if (totalScore >= 80) {
            suggestions.add("建议临时限制该用户的数据导出功能");
        }

        return JSON.toJSONString(suggestions);
    }

    @Transactional
    public void handleAlert(Long alertId, Long handleBy, AlertHandleRequest request) {
        SysAnomalyAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new RuntimeException("预警记录不存在");
        }

        alert.setHandleStatus(1);
        alert.setHandleBy(handleBy);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleResult(request.getHandleResult());

        if (request.getRestrictActions() != null && !request.getRestrictActions().isEmpty()) {
            alert.setRestrictActions(JSON.toJSONString(request.getRestrictActions()));
        }

        alertMapper.updateById(alert);

        auditService.logPermissionChange(
                alertId,
                "ALERT_HANDLE",
                null,
                request,
                "预警处理",
                handleBy,
                null,
                null
        );
    }

    @Transactional
    public void blockUser(Long alertId, Long operatorId, String reason) {
        SysAnomalyAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new RuntimeException("预警记录不存在");
        }

        alert.setHandleStatus(1);
        alert.setHandleBy(operatorId);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleResult("用户已封禁: " + reason);
        alert.setRestrictActions("[\"ALL_ACCESS\"]");
        alertMapper.updateById(alert);

        log.info("用户已被封禁: 用户ID={}, 操作人={}, 原因={}", alert.getUserId(), operatorId, reason);
    }

    public Map<String, Object> getStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<SysAnomalyAlert> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            wrapper.ge(SysAnomalyAlert::getCreatedTime, startDate);
        }
        if (endDate != null) {
            wrapper.le(SysAnomalyAlert::getCreatedTime, endDate);
        }

        List<SysAnomalyAlert> alerts = alertMapper.selectList(wrapper);

        long totalCount = alerts.size();
        long pendingCount = alerts.stream().filter(a -> a.getHandleStatus() == 0).count();
        long handledCount = alerts.stream().filter(a -> a.getHandleStatus() == 1).count();

        long criticalCount = alerts.stream().filter(a -> a.getAlertLevel() >= 3).count();
        long highCount = alerts.stream().filter(a -> a.getAlertLevel() == 2).count();
        long mediumCount = alerts.stream().filter(a -> a.getAlertLevel() == 1).count();

        stats.put("totalCount", totalCount);
        stats.put("pendingCount", pendingCount);
        stats.put("handledCount", handledCount);
        stats.put("criticalCount", criticalCount);
        stats.put("highCount", highCount);
        stats.put("mediumCount", mediumCount);
        stats.put("handleRate", totalCount > 0 ? (double) handledCount / totalCount : 0);

        return stats;
    }
}
