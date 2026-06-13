package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.RiskDashboardRequest;
import com.example.datapermission.dto.RiskDashboardResponse;
import com.example.datapermission.dto.RiskDashboardResponse.*;
import com.example.datapermission.dto.RiskDetailRequest;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskDashboardService {

    private final SysUserPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;
    private final SysResourceMapper resourceMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysPostMapper postMapper;
    private final SysAnomalyAlertMapper anomalyAlertMapper;
    private final SysAccessLogMapper accessLogMapper;
    private final SysSensitiveFieldMapper sensitiveFieldMapper;

    public RiskDashboardResponse getDashboardSummary(RiskDashboardRequest request) {
        List<Long> userIds = getFilteredUserIds(request);

        RiskSummary summary = buildRiskSummary(userIds, request);
        List<RiskCategoryStats> categoryStats = buildCategoryStats(userIds, request);
        Map<String, Map<String, Long>> crossMatrix = buildCrossDimensionMatrix(userIds, request);
        List<TrendData> trendData = buildTrendData(request);

        return RiskDashboardResponse.builder()
                .summary(summary)
                .categoryStats(categoryStats)
                .crossDimensionMatrix(crossMatrix)
                .trendData(trendData)
                .build();
    }

    private RiskSummary buildRiskSummary(List<Long> userIds, RiskDashboardRequest request) {
        long expiringCount = countExpiringPermissions(userIds, request);
        long unusedCount = countUnusedPermissions(userIds, request);
        long overGrantedCount = countOverGrantedPermissions(userIds, request);
        long abnormalDownloadCount = countAbnormalDownloads(request);
        long totalRisks = expiringCount + unusedCount + overGrantedCount + abnormalDownloadCount;

        double riskScore = calculateOverallRiskScore(expiringCount, unusedCount, overGrantedCount, abnormalDownloadCount, userIds.size());

        return RiskSummary.builder()
                .expiringCount(expiringCount)
                .unusedCount(unusedCount)
                .overGrantedCount(overGrantedCount)
                .abnormalDownloadCount(abnormalDownloadCount)
                .totalRisks(totalRisks)
                .riskScore(riskScore)
                .build();
    }

    private List<RiskCategoryStats> buildCategoryStats(List<Long> userIds, RiskDashboardRequest request) {
        List<RiskCategoryStats> stats = new ArrayList<>();

        stats.add(buildExpiringStats(userIds, request));
        stats.add(buildUnusedStats(userIds, request));
        stats.add(buildOverGrantedStats(userIds, request));
        stats.add(buildAbnormalDownloadStats(request));

        return stats;
    }

    private RiskCategoryStats buildExpiringStats(List<Long> userIds, RiskDashboardRequest request) {
        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapper(userIds, request);
        wrapper.apply("end_time IS NOT NULL AND DATEDIFF(end_time, NOW()) BETWEEN 0 AND 7");
        wrapper.eq(SysUserPermission::getStatus, 1);

        long count = permissionMapper.selectCount(wrapper);
        List<RiskDetail> details = getExpiringDetails(userIds, request);

        return RiskCategoryStats.builder()
                .category("EXPIRING")
                .categoryName("即将到期")
                .count(count)
                .percentage(count > 0 ? 100.0 * count / Math.max(1, count + 1) : 0.0)
                .details(details)
                .build();
    }

    private RiskCategoryStats buildUnusedStats(List<Long> userIds, RiskDashboardRequest request) {
        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapper(userIds, request);
        wrapper.apply("last_used_time IS NULL OR DATEDIFF(NOW(), last_used_time) > 90");
        wrapper.eq(SysUserPermission::getStatus, 1);

        long count = permissionMapper.selectCount(wrapper);
        List<RiskDetail> details = getUnusedDetails(userIds, request);

        return RiskCategoryStats.builder()
                .category("UNUSED")
                .categoryName("长期未使用")
                .count(count)
                .percentage(count > 0 ? 100.0 * count / Math.max(1, count + 1) : 0.0)
                .details(details)
                .build();
    }

    private RiskCategoryStats buildOverGrantedStats(List<Long> userIds, RiskDashboardRequest request) {
        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapper(userIds, request);
        wrapper.ge(SysUserPermission::getFieldAccessLevel, 5);
        wrapper.eq(SysUserPermission::getStatus, 1);

        long count = permissionMapper.selectCount(wrapper);
        List<RiskDetail> details = getOverGrantedDetails(userIds, request);

        return RiskCategoryStats.builder()
                .category("OVER_GRANTED")
                .categoryName("越权申请")
                .count(count)
                .percentage(count > 0 ? 100.0 * count / Math.max(1, count + 1) : 0.0)
                .details(details)
                .build();
    }

    private RiskCategoryStats buildAbnormalDownloadStats(RiskDashboardRequest request) {
        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("operation_type = 'EXPORT' OR operation_type = 'DOWNLOAD'");
        wrapper.apply("record_count > 10000 OR data_volume > 104857600");
        wrapper.apply("created_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)");

        if (request.getOrgIds() != null && !request.getOrgIds().isEmpty()) {
            List<Long> userIds = getUserIdsByOrgIds(request.getOrgIds());
            wrapper.in(SysAccessLog::getUserId, userIds);
        }

        long count = accessLogMapper.selectCount(wrapper);
        List<RiskDetail> details = getAbnormalDownloadDetails(request);

        return RiskCategoryStats.builder()
                .category("ABNORMAL_DOWNLOAD")
                .categoryName("异常下载")
                .count(count)
                .percentage(count > 0 ? 100.0 * count / Math.max(1, count + 1) : 0.0)
                .details(details)
                .build();
    }

    private Map<String, Map<String, Long>> buildCrossDimensionMatrix(List<Long> userIds, RiskDashboardRequest request) {
        Map<String, Map<String, Long>> matrix = new HashMap<>();

        Map<String, Long> byOrg = countByOrganization(userIds, request);
        Map<String, Long> byPost = countByPost(userIds, request);
        Map<String, Long> bySensitivity = countBySensitivityLevel(userIds, request);

        matrix.put("byOrganization", byOrg);
        matrix.put("byPost", byPost);
        matrix.put("bySensitivity", bySensitivity);

        return matrix;
    }

    private Map<String, Long> countByOrganization(List<Long> userIds, RiskDashboardRequest request) {
        if (userIds.isEmpty()) return new HashMap<>();

        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(SysUser::getId, userIds);
        if (request.getOrgIds() != null && !request.getOrgIds().isEmpty()) {
            userWrapper.in(SysUser::getOrgId, request.getOrgIds());
        }

        Map<Long, Long> userOrgCount = userMapper.selectList(userWrapper).stream()
                .collect(Collectors.groupingBy(SysUser::getOrgId, Collectors.counting()));

        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : userOrgCount.entrySet()) {
            SysOrganization org = organizationMapper.selectById(entry.getKey());
            String orgName = org != null ? org.getOrgName() : "未知";
            result.put(orgName, entry.getValue());
        }

        return result;
    }

    private Map<String, Long> countByPost(List<Long> userIds, RiskDashboardRequest request) {
        if (userIds.isEmpty()) return new HashMap<>();

        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(SysUser::getId, userIds);

        Map<Long, Long> userPostCount = userMapper.selectList(userWrapper).stream()
                .collect(Collectors.groupingBy(SysUser::getPostId, Collectors.counting()));

        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : userPostCount.entrySet()) {
            SysPost post = postMapper.selectById(entry.getKey());
            String postName = post != null ? post.getPostName() : "未知";
            result.put(postName, entry.getValue());
        }

        return result;
    }

    private Map<String, Long> countBySensitivityLevel(List<Long> userIds, RiskDashboardRequest request) {
        if (userIds.isEmpty()) return new HashMap<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapper(userIds, request);
        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);

        return permissions.stream()
                .map(p -> {
                    SysResource resource = resourceMapper.selectById(p.getResourceId());
                    return resource != null ? resource.getSensitivityLevel() : "UNKNOWN";
                })
                .collect(Collectors.groupingBy(level -> level, Collectors.counting()));
    }

    private List<TrendData> buildTrendData(RiskDashboardRequest request) {
        List<TrendData> trends = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.toString();

            trends.add(TrendData.builder()
                    .date(dateStr)
                    .metric("expiring")
                    .value(getDailyExpiringCount(date))
                    .build());

            trends.add(TrendData.builder()
                    .date(dateStr)
                    .metric("unused")
                    .value(getDailyUnusedCount(date))
                    .build());

            trends.add(TrendData.builder()
                    .date(dateStr)
                    .metric("overGranted")
                    .value(getDailyOverGrantedCount(date))
                    .build());

            trends.add(TrendData.builder()
                    .date(dateStr)
                    .metric("abnormalDownload")
                    .value(getDailyAbnormalDownloadCount(date))
                    .build());
        }

        return trends;
    }

    public Page<RiskDetail> getRiskDetails(RiskDetailRequest request) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;

        Page<RiskDetail> page = new Page<>(pageNum, pageSize);
        List<RiskDetail> details = new ArrayList<>();

        switch (request.getRiskType()) {
            case "EXPIRING":
                details = getExpiringDetailsForPage(request);
                break;
            case "UNUSED":
                details = getUnusedDetailsForPage(request);
                break;
            case "OVER_GRANTED":
                details = getOverGrantedDetailsForPage(request);
                break;
            case "ABNORMAL_DOWNLOAD":
                details = getAbnormalDownloadDetailsForPage(request);
                break;
        }

        page.setRecords(details);
        page.setTotal(details.size());

        return page;
    }

    private List<RiskDetail> getExpiringDetails(List<Long> userIds, RiskDashboardRequest request) {
        return getExpiringDetailsForPage(RiskDetailRequest.builder()
                .orgIds(request.getOrgIds())
                .postIds(request.getPostIds())
                .sensitivityLevels(request.getResourceSensitivityLevels())
                .pageSize(5)
                .build());
    }

    private List<RiskDetail> getExpiringDetailsForPage(RiskDetailRequest request) {
        List<Long> userIds = getFilteredUserIdsFromDetail(request);
        if (userIds.isEmpty()) return new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapperFromDetail(userIds, request);
        wrapper.apply("end_time IS NOT NULL AND DATEDIFF(end_time, NOW()) BETWEEN 0 AND 7");
        wrapper.eq(SysUserPermission::getStatus, 1);
        wrapper.last("LIMIT " + (request.getPageSize() != null ? request.getPageSize() : 10));

        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);
        return convertToRiskDetails(permissions, "EXPIRING");
    }

    private List<RiskDetail> getUnusedDetails(List<Long> userIds, RiskDashboardRequest request) {
        return getUnusedDetailsForPage(RiskDetailRequest.builder()
                .orgIds(request.getOrgIds())
                .postIds(request.getPostIds())
                .sensitivityLevels(request.getResourceSensitivityLevels())
                .pageSize(5)
                .build());
    }

    private List<RiskDetail> getUnusedDetailsForPage(RiskDetailRequest request) {
        List<Long> userIds = getFilteredUserIdsFromDetail(request);
        if (userIds.isEmpty()) return new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapperFromDetail(userIds, request);
        wrapper.apply("last_used_time IS NULL OR DATEDIFF(NOW(), last_used_time) > 90");
        wrapper.eq(SysUserPermission::getStatus, 1);
        wrapper.last("LIMIT " + (request.getPageSize() != null ? request.getPageSize() : 10));

        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);
        return convertToRiskDetails(permissions, "UNUSED");
    }

    private List<RiskDetail> getOverGrantedDetails(List<Long> userIds, RiskDashboardRequest request) {
        return getOverGrantedDetailsForPage(RiskDetailRequest.builder()
                .orgIds(request.getOrgIds())
                .postIds(request.getPostIds())
                .sensitivityLevels(request.getResourceSensitivityLevels())
                .pageSize(5)
                .build());
    }

    private List<RiskDetail> getOverGrantedDetailsForPage(RiskDetailRequest request) {
        List<Long> userIds = getFilteredUserIdsFromDetail(request);
        if (userIds.isEmpty()) return new ArrayList<>();

        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapperFromDetail(userIds, request);
        wrapper.ge(SysUserPermission::getFieldAccessLevel, 5);
        wrapper.eq(SysUserPermission::getStatus, 1);
        wrapper.last("LIMIT " + (request.getPageSize() != null ? request.getPageSize() : 10));

        List<SysUserPermission> permissions = permissionMapper.selectList(wrapper);
        return convertToRiskDetails(permissions, "OVER_GRANTED");
    }

    private List<RiskDetail> getAbnormalDownloadDetails(RiskDashboardRequest request) {
        return getAbnormalDownloadDetailsForPage(RiskDetailRequest.builder()
                .orgIds(request.getOrgIds())
                .pageSize(5)
                .build());
    }

    private List<RiskDetail> getAbnormalDownloadDetailsForPage(RiskDetailRequest request) {
        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("operation_type = 'EXPORT' OR operation_type = 'DOWNLOAD'");
        wrapper.apply("(record_count > 10000 OR data_volume > 104857600)");
        wrapper.apply("created_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)");

        if (request.getOrgIds() != null && !request.getOrgIds().isEmpty()) {
            List<Long> userIds = getUserIdsByOrgIds(request.getOrgIds());
            wrapper.in(SysAccessLog::getUserId, userIds);
        }

        wrapper.last("LIMIT " + (request.getPageSize() != null ? request.getPageSize() : 10));

        List<SysAccessLog> logs = accessLogMapper.selectList(wrapper);
        return logs.stream().map(log -> {
            SysUser user = userMapper.selectById(log.getUserId());
            return RiskDetail.builder()
                    .id(log.getId())
                    .userId(log.getUserId())
                    .userName(user != null ? user.getUsername() : "未知")
                    .riskType("ABNORMAL_DOWNLOAD")
                    .riskLevel(3)
                    .riskDescription("异常下载: " + log.getRecordCount() + " 条记录, " + formatDataVolume(log.getDataVolume()))
                    .suggestAction("建议审查该用户的下载行为")
                    .build();
        }).collect(Collectors.toList());
    }

    private List<RiskDetail> convertToRiskDetails(List<SysUserPermission> permissions, String riskType) {
        return permissions.stream().map(perm -> {
            SysUser user = userMapper.selectById(perm.getUserId());
            SysResource resource = resourceMapper.selectById(perm.getResourceId());

            String riskDescription = "";
            String suggestAction = "";
            int riskLevel = 1;

            switch (riskType) {
                case "EXPIRING":
                    long daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), perm.getEndTime());
                    riskDescription = "权限将在 " + daysRemaining + " 天后到期";
                    suggestAction = "建议续期或清理";
                    riskLevel = daysRemaining <= 3 ? 3 : 1;
                    break;
                case "UNUSED":
                    long unusedDays = perm.getLastUsedTime() != null ?
                            ChronoUnit.DAYS.between(perm.getLastUsedTime(), LocalDateTime.now()) : 999;
                    riskDescription = "权限已 " + unusedDays + " 天未使用";
                    suggestAction = "建议评估是否需要保留";
                    riskLevel = unusedDays > 180 ? 3 : 2;
                    break;
                case "OVER_GRANTED":
                    riskDescription = "权限等级过高(Level " + perm.getFieldAccessLevel() + ")";
                    suggestAction = "建议降低权限等级或增加审批流程";
                    riskLevel = 3;
                    break;
            }

            return RiskDetail.builder()
                    .id(perm.getId())
                    .userId(perm.getUserId())
                    .userName(user != null ? user.getUsername() : "未知")
                    .orgName(getOrgName(user != null ? user.getOrgId() : null))
                    .postName(getPostName(user != null ? user.getPostId() : null))
                    .resourceName(resource != null ? resource.getResourceName() : "未知")
                    .riskType(riskType)
                    .riskLevel(riskLevel)
                    .riskDescription(riskDescription)
                    .suggestAction(suggestAction)
                    .permissionId(perm.getId())
                    .build();
        }).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<SysUserPermission> buildPermissionWrapper(List<Long> userIds, RiskDashboardRequest request) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        if (!userIds.isEmpty()) {
            wrapper.in(SysUserPermission::getUserId, userIds);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<SysUserPermission> buildPermissionWrapperFromDetail(List<Long> userIds, RiskDetailRequest request) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        if (!userIds.isEmpty()) {
            wrapper.in(SysUserPermission::getUserId, userIds);
        }
        return wrapper;
    }

    private List<Long> getFilteredUserIds(RiskDashboardRequest request) {
        List<Long> userIds = new ArrayList<>();

        if (request.getOrgIds() != null && !request.getOrgIds().isEmpty()) {
            userIds.addAll(getUserIdsByOrgIds(request.getOrgIds()));
        }

        if (request.getPostIds() != null && !request.getPostIds().isEmpty()) {
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(SysUser::getPostId, request.getPostIds());
            List<Long> postUserIds = userMapper.selectList(userWrapper).stream()
                    .map(SysUser::getId)
                    .collect(Collectors.toList());
            userIds.addAll(postUserIds);
        }

        return userIds.stream().distinct().collect(Collectors.toList());
    }

    private List<Long> getFilteredUserIdsFromDetail(RiskDetailRequest request) {
        List<Long> userIds = new ArrayList<>();

        if (request.getOrgIds() != null && !request.getOrgIds().isEmpty()) {
            userIds.addAll(getUserIdsByOrgIds(request.getOrgIds()));
        }

        if (request.getPostIds() != null && !request.getPostIds().isEmpty()) {
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(SysUser::getPostId, request.getPostIds());
            List<Long> postUserIds = userMapper.selectList(userWrapper).stream()
                    .map(SysUser::getId)
                    .collect(Collectors.toList());
            userIds.addAll(postUserIds);
        }

        return userIds.stream().distinct().collect(Collectors.toList());
    }

    private List<Long> getUserIdsByOrgIds(List<Long> orgIds) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUser::getOrgId, orgIds);
        return userMapper.selectList(wrapper).stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());
    }

    private long countExpiringPermissions(List<Long> userIds, RiskDashboardRequest request) {
        if (userIds.isEmpty()) return 0;
        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapper(userIds, request);
        wrapper.apply("end_time IS NOT NULL AND DATEDIFF(end_time, NOW()) BETWEEN 0 AND 7");
        wrapper.eq(SysUserPermission::getStatus, 1);
        return permissionMapper.selectCount(wrapper);
    }

    private long countUnusedPermissions(List<Long> userIds, RiskDashboardRequest request) {
        if (userIds.isEmpty()) return 0;
        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapper(userIds, request);
        wrapper.apply("last_used_time IS NULL OR DATEDIFF(NOW(), last_used_time) > 90");
        wrapper.eq(SysUserPermission::getStatus, 1);
        return permissionMapper.selectCount(wrapper);
    }

    private long countOverGrantedPermissions(List<Long> userIds, RiskDashboardRequest request) {
        if (userIds.isEmpty()) return 0;
        LambdaQueryWrapper<SysUserPermission> wrapper = buildPermissionWrapper(userIds, request);
        wrapper.ge(SysUserPermission::getFieldAccessLevel, 5);
        wrapper.eq(SysUserPermission::getStatus, 1);
        return permissionMapper.selectCount(wrapper);
    }

    private long countAbnormalDownloads(RiskDashboardRequest request) {
        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("operation_type = 'EXPORT' OR operation_type = 'DOWNLOAD'");
        wrapper.apply("(record_count > 10000 OR data_volume > 104857600)");
        wrapper.apply("created_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)");

        if (request.getOrgIds() != null && !request.getOrgIds().isEmpty()) {
            List<Long> userIds = getUserIdsByOrgIds(request.getOrgIds());
            wrapper.in(SysAccessLog::getUserId, userIds);
        }

        return accessLogMapper.selectCount(wrapper);
    }

    private double calculateOverallRiskScore(long expiring, long unused, long overGranted, long abnormal, long totalUsers) {
        if (totalUsers == 0) return 0.0;

        double expiringScore = Math.min(expiring * 10.0 / totalUsers, 30);
        double unusedScore = Math.min(unused * 5.0 / totalUsers, 25);
        double overGrantedScore = Math.min(overGranted * 20.0 / totalUsers, 25);
        double abnormalScore = Math.min(abnormal * 20.0 / totalUsers, 20);

        return Math.min(expiringScore + unusedScore + overGrantedScore + abnormalScore, 100);
    }

    private long getDailyExpiringCount(LocalDate date) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("DATE(end_time) = {0}", date);
        wrapper.eq(SysUserPermission::getStatus, 1);
        return permissionMapper.selectCount(wrapper);
    }

    private long getDailyUnusedCount(LocalDate date) {
        return 0;
    }

    private long getDailyOverGrantedCount(LocalDate date) {
        return 0;
    }

    private long getDailyAbnormalDownloadCount(LocalDate date) {
        LambdaQueryWrapper<SysAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("DATE(created_time) = {0}", date);
        wrapper.apply("(record_count > 10000 OR data_volume > 104857600)");
        return accessLogMapper.selectCount(wrapper);
    }

    private String getOrgName(Long orgId) {
        if (orgId == null) return "未知";
        SysOrganization org = organizationMapper.selectById(orgId);
        return org != null ? org.getOrgName() : "未知";
    }

    private String getPostName(Long postId) {
        if (postId == null) return "未知";
        SysPost post = postMapper.selectById(postId);
        return post != null ? post.getPostName() : "未知";
    }

    private String formatDataVolume(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
