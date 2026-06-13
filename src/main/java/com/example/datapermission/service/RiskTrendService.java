package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.RiskSubscriptionRequest;
import com.example.datapermission.dto.RiskTrendResponse;
import com.example.datapermission.dto.RiskTrendResponse.*;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskTrendService {

    private final SysRiskTrendMapper trendMapper;
    private final SysRiskSubscriptionMapper subscriptionMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysResourceMapper resourceMapper;
    private final RiskDashboardService riskDashboardService;

    public RiskTrendResponse getRiskTrend(String riskType, List<Long> orgIds, List<Long> postIds, int days) {
        RiskTrendResponse response = new RiskTrendResponse();
        response.setRiskType(riskType);
        response.setRiskTypeName(getRiskTypeName(riskType));

        List<TrendPoint> trendPoints = new ArrayList<>();
        LocalDate today = LocalDate.now();

        long previousTotal = 0;
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.toString();

            long count = calculateRiskCount(riskType, date, orgIds, postIds);
            double changeRate = 0.0;
            String changeDirection = "STABLE";

            if (previousTotal > 0) {
                changeRate = ((double) (count - previousTotal) / previousTotal) * 100;
                if (changeRate > 0) {
                    changeDirection = "UP";
                } else if (changeRate < 0) {
                    changeDirection = "DOWN";
                }
            }

            TrendPoint point = TrendPoint.builder()
                    .date(dateStr)
                    .count(count)
                    .changeRate(Math.round(changeRate * 100.0) / 100.0)
                    .changeDirection(changeDirection)
                    .build();
            trendPoints.add(point);

            previousTotal = trendPoints.stream()
                    .mapToLong(TrendPoint::getCount)
                    .sum();
        }

        response.setTrendPoints(trendPoints);

        long currentCount = trendPoints.isEmpty() ? 0 : trendPoints.get(trendPoints.size() - 1).getCount();
        long previousCount = trendPoints.size() > 1 ?
                trendPoints.stream()
                        .limit(trendPoints.size() - 1)
                        .mapToLong(TrendPoint::getCount)
                        .sum() : 0;

        double totalChangeRate = previousCount > 0 ?
                ((double) (currentCount - previousCount) / previousCount) * 100 : 0;

        TrendSummary summary = TrendSummary.builder()
                .currentCount(currentCount)
                .previousCount(previousCount)
                .changeRate(Math.round(totalChangeRate * 100.0) / 100.0)
                .trend(totalChangeRate > 0 ? "RISING" : (totalChangeRate < 0 ? "DECLINING" : "STABLE"))
                .isImproving(riskType.equals("EXPIRING") || riskType.equals("UNUSED") ?
                        totalChangeRate < 0 : totalChangeRate < 0)
                .build();

        response.setSummary(summary);

        List<RiskItemDetail> recentItems = getRecentRiskItems(riskType, orgIds, postIds, 10);
        response.setRecentItems(recentItems);

        return response;
    }

    @Transactional
    public SysRiskSubscription createSubscription(RiskSubscriptionRequest request, Long createdBy) {
        SysUser subscriber = userMapper.selectById(request.getSubscriberId());
        if (subscriber == null) {
            throw new RuntimeException("订阅用户不存在");
        }

        SysRiskSubscription subscription = new SysRiskSubscription();
        subscription.setSubscriptionCode(generateSubscriptionCode());
        subscription.setSubscriptionName(request.getSubscriptionName());
        subscription.setSubscriberId(request.getSubscriberId());
        subscription.setSubscriberName(subscriber.getUsername());
        subscription.setSubscriptionType(request.getSubscriptionType());
        subscription.setTargetOrgIds(request.getTargetOrgIds());
        subscription.setTargetPostIds(request.getTargetPostIds());
        subscription.setRiskTypes(request.getRiskTypes());
        subscription.setFrequency(request.getFrequency());
        subscription.setDeliveryMethod(request.getDeliveryMethod());
        subscription.setDeliveryAddress(request.getDeliveryAddress());
        subscription.setEnabled(1);
        subscription.setNextSendTime(calculateNextSendTime(request.getFrequency()));
        subscription.setCreatedBy(createdBy);
        subscription.setCreatedTime(LocalDateTime.now());

        subscriptionMapper.insert(subscription);
        return subscription;
    }

    public List<SysRiskSubscription> listSubscriptions(Long subscriberId) {
        LambdaQueryWrapper<SysRiskSubscription> wrapper = new LambdaQueryWrapper<>();

        if (subscriberId != null) {
            wrapper.eq(SysRiskSubscription::getSubscriberId, subscriberId);
        }

        wrapper.orderByDesc(SysRiskSubscription::getCreatedTime);
        return subscriptionMapper.selectList(wrapper);
    }

    @Transactional
    public SysRiskSubscription updateSubscription(Long id, RiskSubscriptionRequest request) {
        SysRiskSubscription subscription = subscriptionMapper.selectById(id);
        if (subscription == null) {
            throw new RuntimeException("订阅不存在");
        }

        if (request.getSubscriptionName() != null) {
            subscription.setSubscriptionName(request.getSubscriptionName());
        }
        if (request.getTargetOrgIds() != null) {
            subscription.setTargetOrgIds(request.getTargetOrgIds());
        }
        if (request.getTargetPostIds() != null) {
            subscription.setTargetPostIds(request.getTargetPostIds());
        }
        if (request.getRiskTypes() != null) {
            subscription.setRiskTypes(request.getRiskTypes());
        }
        if (request.getFrequency() != null) {
            subscription.setFrequency(request.getFrequency());
            subscription.setNextSendTime(calculateNextSendTime(request.getFrequency()));
        }
        if (request.getDeliveryMethod() != null) {
            subscription.setDeliveryMethod(request.getDeliveryMethod());
        }
        if (request.getDeliveryAddress() != null) {
            subscription.setDeliveryAddress(request.getDeliveryAddress());
        }

        subscription.setUpdatedTime(LocalDateTime.now());
        subscriptionMapper.updateById(subscription);
        return subscription;
    }

    @Transactional
    public void deleteSubscription(Long id) {
        subscriptionMapper.deleteById(id);
    }

    @Transactional
    public void toggleSubscription(Long id, boolean enabled) {
        SysRiskSubscription subscription = subscriptionMapper.selectById(id);
        if (subscription != null) {
            subscription.setEnabled(enabled ? 1 : 0);
            subscription.setUpdatedTime(LocalDateTime.now());
            subscriptionMapper.updateById(subscription);
        }
    }

    public Map<String, Object> generateSubscriptionReport(SysRiskSubscription subscription) {
        Map<String, Object> report = new HashMap<>();

        report.put("subscriptionCode", subscription.getSubscriptionCode());
        report.put("subscriptionName", subscription.getSubscriptionName());
        report.put("subscriberName", subscription.getSubscriberName());
        report.put("generatedTime", LocalDateTime.now());

        Map<String, Object> summary = new HashMap<>();
        List<Map<String, Object>> riskSummaries = new ArrayList<>();

        List<String> riskTypes = subscription.getRiskTypes();
        if (riskTypes == null || riskTypes.isEmpty()) {
            riskTypes = Arrays.asList("EXPIRING", "UNUSED", "OVER_GRANTED", "ABNORMAL_DOWNLOAD");
        }

        for (String riskType : riskTypes) {
            RiskTrendResponse trend = getRiskTrend(riskType,
                    subscription.getTargetOrgIds(),
                    subscription.getTargetPostIds(),
                    7);

            Map<String, Object> riskSummary = new HashMap<>();
            riskSummary.put("riskType", riskType);
            riskSummary.put("riskTypeName", getRiskTypeName(riskType));
            riskSummary.put("currentCount", trend.getSummary().getCurrentCount());
            riskSummary.put("changeRate", trend.getSummary().getChangeRate());
            riskSummary.put("trend", trend.getSummary().getTrend());
            riskSummary.put("isImproving", trend.getSummary().getIsImproving());
            riskSummary.put("recentItems", trend.getRecentItems());

            riskSummaries.add(riskSummary);
        }

        report.put("riskSummaries", riskSummaries);

        long totalCurrent = riskSummaries.stream()
                .mapToLong(m -> ((Number) m.get("currentCount")).longValue())
                .sum();
        report.put("totalCurrentCount", totalCurrent);

        return report;
    }

    private long calculateRiskCount(String riskType, LocalDate date, List<Long> orgIds, List<Long> postIds) {
        List<Long> userIds = getFilteredUserIds(orgIds, postIds);
        if (userIds.isEmpty()) return 0;

        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUserPermission::getUserId, userIds);
        wrapper.eq(SysUserPermission::getStatus, 1);

        switch (riskType) {
            case "EXPIRING":
                wrapper.apply("DATE(end_time) = {0}", date);
                break;
            case "UNUSED":
                wrapper.apply("last_used_time IS NULL OR DATE(last_used_time) < DATE_SUB({0}, INTERVAL 90 DAY)", date);
                break;
            case "OVER_GRANTED":
                wrapper.ge(SysUserPermission::getFieldAccessLevel, 5);
                break;
            default:
                return 0;
        }

        return 0;
    }

    private List<RiskItemDetail> getRecentRiskItems(String riskType, List<Long> orgIds, List<Long> postIds, int limit) {
        List<Long> userIds = getFilteredUserIds(orgIds, postIds);
        if (userIds.isEmpty()) return new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUserPermission::getUserId, userIds);
        wrapper.eq(SysUserPermission::getStatus, 1);
        wrapper.last("LIMIT " + limit);

        List<SysUserPermission> permissions = new ArrayList<>();

        switch (riskType) {
            case "EXPIRING":
                wrapper.apply("end_time IS NOT NULL AND DATEDIFF(end_time, NOW()) BETWEEN 0 AND 7");
                permissions = permissionMapper.selectList(wrapper);
                break;
            case "UNUSED":
                wrapper.apply("last_used_time IS NULL OR DATEDIFF(NOW(), last_used_time) > 90");
                permissions = permissionMapper.selectList(wrapper);
                break;
            case "OVER_GRANTED":
                wrapper.ge(SysUserPermission::getFieldAccessLevel, 5);
                permissions = permissionMapper.selectList(wrapper);
                break;
        }

        return permissions.stream().map(perm -> {
            SysUser user = userMapper.selectById(perm.getUserId());
            SysResource resource = resourceMapper.selectById(perm.getResourceId());
            SysOrganization org = user != null ? organizationMapper.selectById(user.getOrgId()) : null;

            String riskDescription = "";
            switch (riskType) {
                case "EXPIRING":
                    long daysRemaining = perm.getEndTime() != null ?
                            ChronoUnit.DAYS.between(LocalDateTime.now(), perm.getEndTime()) : 0;
                    riskDescription = "权限将在 " + daysRemaining + " 天后到期";
                    break;
                case "UNUSED":
                    long unusedDays = perm.getLastUsedTime() != null ?
                            ChronoUnit.DAYS.between(perm.getLastUsedTime(), LocalDateTime.now()) : 999;
                    riskDescription = "权限已 " + unusedDays + " 天未使用";
                    break;
                case "OVER_GRANTED":
                    riskDescription = "权限等级过高(Level " + perm.getFieldAccessLevel() + ")";
                    break;
            }

            return RiskItemDetail.builder()
                    .id(perm.getId())
                    .userName(user != null ? user.getUsername() : "未知")
                    .orgName(org != null ? org.getOrgName() : "未知")
                    .resourceName(resource != null ? resource.getResourceName() : "未知")
                    .riskDescription(riskDescription)
                    .status(riskType)
                    .handleStatus("PENDING")
                    .createdTime(perm.getCreatedTime())
                    .build();
        }).collect(Collectors.toList());
    }

    private List<Long> getFilteredUserIds(List<Long> orgIds, List<Long> postIds) {
        List<Long> userIds = new ArrayList<>();

        if (orgIds != null && !orgIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(SysUser::getOrgId, orgIds);
            userIds.addAll(userMapper.selectList(wrapper).stream()
                    .map(SysUser::getId)
                    .collect(Collectors.toList()));
        }

        if (postIds != null && !postIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(SysUser::getPostId, postIds);
            userIds.addAll(userMapper.selectList(wrapper).stream()
                    .map(SysUser::getId)
                    .collect(Collectors.toList()));
        }

        return userIds.stream().distinct().collect(Collectors.toList());
    }

    private String generateSubscriptionCode() {
        return "SUB-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                String.format("%04d", new Random().nextInt(10000));
    }

    private LocalDateTime calculateNextSendTime(String frequency) {
        LocalDateTime now = LocalDateTime.now();
        switch (frequency) {
            case "DAILY":
                return now.plusDays(1).withHour(9).withMinute(0).withSecond(0);
            case "WEEKLY":
                return now.plusWeeks(1).withHour(9).withMinute(0).withSecond(0);
            case "MONTHLY":
                return now.plusMonths(1).withDayOfMonth(1).withHour(9).withMinute(0).withSecond(0);
            default:
                return now.plusDays(1).withHour(9).withMinute(0).withSecond(0);
        }
    }

    private String getRiskTypeName(String riskType) {
        switch (riskType) {
            case "EXPIRING": return "即将到期";
            case "UNUSED": return "长期未使用";
            case "OVER_GRANTED": return "越权申请";
            case "ABNORMAL_DOWNLOAD": return "异常下载";
            default: return riskType;
        }
    }
}
