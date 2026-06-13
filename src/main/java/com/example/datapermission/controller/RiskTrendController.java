package com.example.datapermission.controller;

import com.example.datapermission.dto.RiskSubscriptionRequest;
import com.example.datapermission.dto.RiskTrendResponse;
import com.example.datapermission.entity.SysRiskSubscription;
import com.example.datapermission.service.RiskTrendService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/risk-trend")
@RequiredArgsConstructor
public class RiskTrendController {

    private final RiskTrendService trendService;

    @GetMapping("/trend/{riskType}")
    public Result<RiskTrendResponse> getRiskTrend(
            @PathVariable String riskType,
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(required = false) List<Long> postIds,
            @RequestParam(defaultValue = "7") int days) {
        RiskTrendResponse response = trendService.getRiskTrend(riskType, orgIds, postIds, days);
        return Result.success(response);
    }

    @GetMapping("/trend/expiring")
    public Result<RiskTrendResponse> getExpiringTrend(
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(defaultValue = "7") int days) {
        RiskTrendResponse response = trendService.getRiskTrend("EXPIRING", orgIds, null, days);
        return Result.success(response);
    }

    @GetMapping("/trend/unused")
    public Result<RiskTrendResponse> getUnusedTrend(
            @RequestParam(required = false) List<Long> orgIds,
            @RequestParam(defaultValue = "7") int days) {
        RiskTrendResponse response = trendService.getRiskTrend("UNUSED", orgIds, null, days);
        return Result.success(response);
    }

    @PostMapping("/subscription")
    public Result<SysRiskSubscription> createSubscription(
            @RequestBody RiskSubscriptionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        SysRiskSubscription subscription = trendService.createSubscription(request, userId);
        return Result.success(subscription);
    }

    @GetMapping("/subscription")
    public Result<List<SysRiskSubscription>> listSubscriptions(
            @RequestParam(required = false) Long subscriberId) {
        List<SysRiskSubscription> subscriptions = trendService.listSubscriptions(subscriberId);
        return Result.success(subscriptions);
    }

    @PutMapping("/subscription/{id}")
    public Result<SysRiskSubscription> updateSubscription(
            @PathVariable Long id,
            @RequestBody RiskSubscriptionRequest request) {
        SysRiskSubscription subscription = trendService.updateSubscription(id, request);
        return Result.success(subscription);
    }

    @DeleteMapping("/subscription/{id}")
    public Result<Void> deleteSubscription(@PathVariable Long id) {
        trendService.deleteSubscription(id);
        return Result.success();
    }

    @PutMapping("/subscription/{id}/toggle")
    public Result<Void> toggleSubscription(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        trendService.toggleSubscription(id, enabled);
        return Result.success();
    }

    @GetMapping("/subscription/{id}/report")
    public Result<Map<String, Object>> getSubscriptionReport(@PathVariable Long id) {
        SysRiskSubscription subscription = new SysRiskSubscription();
        subscription.setId(id);
        Map<String, Object> report = trendService.generateSubscriptionReport(subscription);
        return Result.success(report);
    }
}
