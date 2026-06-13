package com.example.datapermission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.RiskDashboardRequest;
import com.example.datapermission.dto.RiskDashboardResponse;
import com.example.datapermission.dto.RiskDashboardResponse.RiskDetail;
import com.example.datapermission.dto.RiskDetailRequest;
import com.example.datapermission.dto.RuleSimulationRequest;
import com.example.datapermission.dto.RuleSimulationResponse;
import com.example.datapermission.service.DeepRuleSimulationService;
import com.example.datapermission.service.RiskDashboardService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/risk")
@RequiredArgsConstructor
public class RiskDashboardController {

    private final RiskDashboardService riskDashboardService;
    private final DeepRuleSimulationService deepRuleSimulationService;

    @GetMapping("/dashboard")
    public Result<RiskDashboardResponse> getRiskDashboard(
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<Long> postIds,
            @RequestParam(required = false) List<String> resourceSensitivityLevels,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "org") String groupBy) {

        RiskDashboardRequest request = new RiskDashboardRequest();
        request.setOrgIds(orgIds);
        request.setPostIds(postIds);
        request.setResourceSensitivityLevels(resourceSensitivityLevels);
        request.setRiskType(riskType);
        request.setStartDate(startDate != null ? startDate.toLocalDate().toEpochDay() : null);
        request.setEndDate(endDate != null ? endDate.toLocalDate().toEpochDay() : null);
        request.setGroupBy(groupBy);

        RiskDashboardResponse response = riskDashboardService.getDashboardSummary(request);
        return Result.success(response);
    }

    @GetMapping("/dashboard/summary")
    public Result<Map<String, Object>> getRiskSummary(
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<Long> postIds) {

        RiskDashboardRequest request = new RiskDashboardRequest();
        request.setOrgIds(orgIds);
        request.setPostIds(postIds);

        RiskDashboardResponse response = riskDashboardService.getDashboardSummary(request);
        return Result.success(Map.of(
                "expiringCount", response.getSummary().getExpiringCount(),
                "unusedCount", response.getSummary().getUnusedCount(),
                "overGrantedCount", response.getSummary().getOverGrantedCount(),
                "abnormalDownloadCount", response.getSummary().getAbnormalDownloadCount(),
                "totalRisks", response.getSummary().getTotalRisks(),
                "riskScore", response.getSummary().getRiskScore()
        ));
    }

    @GetMapping("/dashboard/category/{category}")
    public Result<RiskDashboardResponse.RiskCategoryStats> getRiskCategoryStats(
            @PathVariable String category,
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<Long> postIds) {

        RiskDashboardRequest request = new RiskDashboardRequest();
        request.setOrgIds(orgIds);
        request.setPostIds(postIds);

        RiskDashboardResponse response = riskDashboardService.getDashboardSummary(request);

        return response.getCategoryStats().stream()
                .filter(stats -> stats.getCategory().equals(category))
                .findFirst()
                .map(Result::success)
                .orElse(Result.success(RiskDashboardResponse.RiskCategoryStats.builder()
                        .category(category)
                        .categoryName(category)
                        .count(0L)
                        .percentage(0.0)
                        .build()));
    }

    @GetMapping("/details")
    public Result<Page<RiskDetail>> getRiskDetails(
            @RequestParam String riskType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<Long> postIds,
            @RequestParam(required = false) List<String> sensitivityLevels,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "createdTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        RiskDetailRequest request = RiskDetailRequest.builder()
                .riskType(riskType)
                .orgIds(orgIds)
                .postIds(postIds)
                .sensitivityLevels(sensitivityLevels)
                .startDate(startDate != null ? startDate.toLocalDate().toEpochDay() : null)
                .endDate(endDate != null ? endDate.toLocalDate().toEpochDay() : null)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();

        Page<RiskDetail> page = riskDashboardService.getRiskDetails(request);
        return Result.success(page);
    }

    @GetMapping("/details/expiring")
    public Result<Page<RiskDetail>> getExpiringRiskDetails(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) List<Long> orgIds) {

        RiskDetailRequest request = RiskDetailRequest.builder()
                .riskType("EXPIRING")
                .orgIds(orgIds)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();

        Page<RiskDetail> page = riskDashboardService.getRiskDetails(request);
        return Result.success(page);
    }

    @GetMapping("/details/unused")
    public Result<Page<RiskDetail>> getUnusedRiskDetails(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) List<Long> orgIds) {

        RiskDetailRequest request = RiskDetailRequest.builder()
                .riskType("UNUSED")
                .orgIds(orgIds)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();

        Page<RiskDetail> page = riskDashboardService.getRiskDetails(request);
        return Result.success(page);
    }

    @GetMapping("/details/over-granted")
    public Result<Page<RiskDetail>> getOverGrantedRiskDetails(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) List<Long> orgIds) {

        RiskDetailRequest request = RiskDetailRequest.builder()
                .riskType("OVER_GRANTED")
                .orgIds(orgIds)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();

        Page<RiskDetail> page = riskDashboardService.getRiskDetails(request);
        return Result.success(page);
    }

    @GetMapping("/details/abnormal-download")
    public Result<Page<RiskDetail>> getAbnormalDownloadDetails(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) List<Long> orgIds) {

        RiskDetailRequest request = RiskDetailRequest.builder()
                .riskType("ABNORMAL_DOWNLOAD")
                .orgIds(orgIds)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();

        Page<RiskDetail> page = riskDashboardService.getRiskDetails(request);
        return Result.success(page);
    }

    @GetMapping("/matrix/organization")
    public Result<Map<String, Map<String, Long>>> getRiskMatrixByOrganization(
            @RequestParam(required = false) List<Long> orgIds) {

        RiskDashboardRequest request = new RiskDashboardRequest();
        request.setOrgIds(orgIds);

        RiskDashboardResponse response = riskDashboardService.getDashboardSummary(request);
        return Result.success(response.getCrossDimensionMatrix().getOrDefault("byOrganization", Map.of()));
    }

    @GetMapping("/matrix/post")
    public Result<Map<String, Map<String, Long>>> getRiskMatrixByPost(
            @RequestParam(required = false) List<Long> postIds) {

        RiskDashboardRequest request = new RiskDashboardRequest();
        request.setPostIds(postIds);

        RiskDashboardResponse response = riskDashboardService.getDashboardSummary(request);
        return Result.success(response.getCrossDimensionMatrix().getOrDefault("byPost", Map.of()));
    }

    @GetMapping("/matrix/sensitivity")
    public Result<Map<String, Map<String, Long>>> getRiskMatrixBySensitivity(
            @RequestParam(required = false) List<String> sensitivityLevels) {

        RiskDashboardRequest request = new RiskDashboardRequest();
        request.setResourceSensitivityLevels(sensitivityLevels);

        RiskDashboardResponse response = riskDashboardService.getDashboardSummary(request);
        return Result.success(response.getCrossDimensionMatrix().getOrDefault("bySensitivity", Map.of()));
    }

    @GetMapping("/trend")
    public Result<List<RiskDashboardResponse.TrendData>> getRiskTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {

        RiskDashboardRequest request = new RiskDashboardRequest();
        request.setStartDate(startDate != null ? startDate.toLocalDate().toEpochDay() : null);
        request.setEndDate(endDate != null ? endDate.toLocalDate().toEpochDay() : null);

        RiskDashboardResponse response = riskDashboardService.getDashboardSummary(request);
        return Result.success(response.getTrendData());
    }
}
