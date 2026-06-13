package com.example.datapermission.service;

import com.example.datapermission.dto.BatchAccessCheckRequest;
import com.example.datapermission.dto.BatchAccessCheckRequest.*;
import com.example.datapermission.dto.BatchAccessCheckResponse.*;
import com.example.datapermission.dto.EnhancedAccessCheckRequest;
import com.example.datapermission.dto.EnhancedAccessCheckResponse;
import com.example.datapermission.entity.SysAccessStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAccessCheckService {

    private final EnhancedAccessCheckService accessCheckService;
    private final AccessStrategyService accessStrategyService;

    private static final int MAX_BATCH_SIZE = 100;
    private static final int THREAD_POOL_SIZE = 10;

    public BatchAccessCheckResponse checkBatch(BatchAccessCheckRequest request) {
        long startTime = System.currentTimeMillis();
        BatchAccessCheckResponse response = new BatchAccessCheckResponse();

        if (request.getItems() == null || request.getItems().isEmpty()) {
            response.setTotalCount(0);
            response.setSuccessCount(0);
            response.setFailureCount(0);
            response.setPartialCount(0);
            response.setResults(List.of());
            return response;
        }

        SysAccessStrategy matchedStrategy = null;
        if (request.getCallerCode() != null) {
            matchedStrategy = accessStrategyService.matchStrategy(
                    request.getCallerCode(),
                    request.getTenantId(),
                    request.getResourceDomain()
            );
            response.setMatchedStrategy(convertStrategyInfo(matchedStrategy));
        }

        List<BatchCheckResult> results = new ArrayList<>();

        if (Boolean.TRUE.equals(request.getParallel())) {
            results = executeParallel(request.getItems(), matchedStrategy);
        } else {
            results = executeSequential(request.getItems(), matchedStrategy);
        }

        int successCount = (int) results.stream().filter(BatchCheckResult::getSuccess).count();
        int failureCount = (int) results.stream().filter(r -> !r.getSuccess()).count();
        int partialCount = (int) results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getSuccess()) && "PARTIAL".equals(r.getAccessDecision()))
                .count();

        response.setTotalCount(results.size());
        response.setSuccessCount(successCount);
        response.setFailureCount(failureCount);
        response.setPartialCount(partialCount);
        response.setTotalExecutionTime(System.currentTimeMillis() - startTime);
        response.setResults(results);
        response.setSummary(buildSummary(request.getItems(), results));

        return response;
    }

    private List<BatchCheckResult> executeParallel(List<AccessCheckItem> items, SysAccessStrategy strategy) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<BatchCheckResult>> futures = new ArrayList<>();

        for (AccessCheckItem item : items) {
            Future<BatchCheckResult> future = executor.submit(() -> processSingleItem(item, strategy));
            futures.add(future);
        }

        List<BatchCheckResult> results = new ArrayList<>();
        for (Future<BatchCheckResult> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("批量校验执行异常", e);
                BatchCheckResult errorResult = new BatchCheckResult();
                errorResult.setItemId("unknown");
                errorResult.setSuccess(false);
                errorResult.setErrorMessage("执行超时或异常: " + e.getMessage());
                results.add(errorResult);
            }
        }

        executor.shutdown();
        return results;
    }

    private List<BatchCheckResult> executeSequential(List<AccessCheckItem> items, SysAccessStrategy strategy) {
        List<BatchCheckResult> results = new ArrayList<>();
        for (AccessCheckItem item : items) {
            results.add(processSingleItem(item, strategy));
        }
        return results;
    }

    private BatchCheckResult processSingleItem(AccessCheckItem item, SysAccessStrategy strategy) {
        long itemStartTime = System.currentTimeMillis();
        BatchCheckResult result = new BatchCheckResult();
        result.setItemId(item.getItemId());

        try {
            EnhancedAccessCheckRequest checkRequest = convertToCheckRequest(item, strategy);
            EnhancedAccessCheckResponse checkResponse = accessCheckService.checkAccess(checkRequest);

            result.setSuccess(true);
            result.setAccessDecision(checkResponse.getAccessDecision());
            result.setAllowed(checkResponse.getAllowed());
            result.setDeniedReason(checkResponse.getDeniedReason());

            if (strategy != null) {
                result.setAppliedStrategy(convertStrategyInfo(strategy));
                if ("DENY".equals(checkResponse.getAccessDecision()) &&
                        (checkResponse.getDeniedReason() == null || checkResponse.getDeniedReason().isEmpty())) {
                    result.setDeniedReason(strategy.getFallbackDenyMessage());
                }
            }

            if (checkResponse.getAccessibleScope() != null) {
                result.setAccessibleScope(convertScope(checkResponse.getAccessibleScope()));
            }

            if (checkResponse.getFieldPermissions() != null) {
                result.setHiddenFields(checkResponse.getFieldPermissions().stream()
                        .filter(fp -> !Boolean.TRUE.equals(fp.getAllowed()) && !Boolean.TRUE.equals(fp.getMasked()))
                        .map(fp -> fp.getField())
                        .collect(Collectors.toList()));

                result.setMaskedFields(checkResponse.getFieldPermissions().stream()
                        .filter(fp -> Boolean.TRUE.equals(fp.getMasked()))
                        .map(fp -> {
                            MaskedFieldResult masked = new MaskedFieldResult();
                            masked.setField(fp.getField());
                            masked.setMaskedValue(fp.getMaskedValue());
                            masked.setReason(fp.getReason());
                            return masked;
                        })
                        .collect(Collectors.toList()));
            }

            if (checkResponse.getSqlFilters() != null) {
                result.setSqlFilters(convertSqlFilters(checkResponse.getSqlFilters()));
            }

            if (checkResponse.getApplyPermission() != null) {
                result.setApplyPermission(convertApplyPermission(checkResponse.getApplyPermission()));
            }

        } catch (Exception e) {
            log.error("处理校验项异常: itemId={}", item.getItemId(), e);
            result.setSuccess(false);
            result.setErrorMessage("处理异常: " + e.getMessage());
        }

        result.setExecutionTime(System.currentTimeMillis() - itemStartTime);
        return result;
    }

    private EnhancedAccessCheckRequest convertToCheckRequest(AccessCheckItem item, SysAccessStrategy strategy) {
        EnhancedAccessCheckRequest request = new EnhancedAccessCheckRequest();
        request.setUserId(item.getUserId());
        request.setResourceCode(item.getResourceCode());
        request.setOperationType(item.getOperationType());
        request.setRequestedFields(item.getRequestedFields());
        request.setVersion("v2");
        request.setReturnSqlFilter(true);
        request.setReturnAppliedRules(true);

        if (strategy != null) {
            if (strategy.getDefaultFieldAccessLevel() != null) {
                request.setFieldAccessLevel(strategy.getDefaultFieldAccessLevel());
            }
            if (strategy.getDefaultDesensitizationLevel() != null) {
                request.setDesensitizationLevel(strategy.getDefaultDesensitizationLevel());
            }
        }

        if (item.getComplexConditions() != null) {
            EnhancedAccessCheckRequest.ComplexConditions conditions = new EnhancedAccessCheckRequest.ComplexConditions();

            if (item.getComplexConditions().getTimeRange() != null) {
                EnhancedAccessCheckRequest.TimeRange timeRange = new EnhancedAccessCheckRequest.TimeRange();
                timeRange.setField(item.getComplexConditions().getTimeRange().getField());
                timeRange.setStartTime(item.getComplexConditions().getTimeRange().getStartTime());
                timeRange.setEndTime(item.getComplexConditions().getTimeRange().getEndTime());
                conditions.setTimeRange(timeRange);
            }

            if (item.getComplexConditions().getCustomerLevel() != null) {
                EnhancedAccessCheckRequest.CustomerLevel customerLevel = new EnhancedAccessCheckRequest.CustomerLevel();
                customerLevel.setField(item.getComplexConditions().getCustomerLevel().getField());
                customerLevel.setLevels(item.getComplexConditions().getCustomerLevel().getLevels());
                conditions.setCustomerLevel(customerLevel);
            }

            if (item.getComplexConditions().getProjectScope() != null) {
                EnhancedAccessCheckRequest.ProjectScope projectScope = new EnhancedAccessCheckRequest.ProjectScope();
                projectScope.setField(item.getComplexConditions().getProjectScope().getField());
                projectScope.setProjectIds(item.getComplexConditions().getProjectScope().getProjectIds());
                conditions.setProjectScope(projectScope);
            }

            request.setComplexConditions(conditions);
        }

        return request;
    }

    private StrategyInfo convertStrategyInfo(SysAccessStrategy strategy) {
        if (strategy == null) return null;

        StrategyInfo info = new StrategyInfo();
        info.setStrategyId(strategy.getId());
        info.setStrategyCode(strategy.getStrategyCode());
        info.setStrategyName(strategy.getStrategyName());
        info.setCallerCode(strategy.getCallerCode());
        info.setTenantId(strategy.getTenantId());
        info.setResourceDomain(strategy.getResourceDomain());
        return info;
    }

    private AccessScopeResult convertScope(EnhancedAccessCheckResponse.AccessScope scope) {
        AccessScopeResult result = new AccessScopeResult();
        result.setOrgIds(scope.getOrgIds());
        result.setOrgType(scope.getOrgType());
        result.setProjectIds(scope.getProjectIds());
        result.setCustomerLevels(scope.getCustomerLevels());

        if (scope.getTimeRange() != null) {
            TimeRangeResult timeRange = new TimeRangeResult();
            timeRange.setStartTime(scope.getTimeRange().getStartTime());
            timeRange.setEndTime(scope.getTimeRange().getEndTime());
            result.setTimeRange(timeRange);
        }

        return result;
    }

    private SqlFilterResult convertSqlFilters(EnhancedAccessCheckResponse.SqlFilters filters) {
        SqlFilterResult result = new SqlFilterResult();
        result.setWhereClause(filters.getWhereClause());
        result.setParameters(filters.getParameters());
        result.setAppliedConditions(filters.getAppliedConditions());
        return result;
    }

    private ApplyPermissionResult convertApplyPermission(EnhancedAccessCheckResponse.ApplyPermission permission) {
        if (permission == null) return null;

        ApplyPermissionResult result = new ApplyPermissionResult();
        result.setCanApply(permission.getCanApply());
        result.setApplyUrl(permission.getApplyUrl());

        if (permission.getPermissionGroups() != null) {
            result.setPermissionGroups(permission.getPermissionGroups().stream()
                    .map(group -> {
                        PermissionGroupResult groupResult = new PermissionGroupResult();
                        groupResult.setSensitivityLevel(group.getSensitivityLevel());
                        groupResult.setLevelName(group.getLevelName());

                        if (group.getOptions() != null) {
                            groupResult.setOptions(group.getOptions().stream()
                                    .map(opt -> {
                                        PermissionOptionResult optResult = new PermissionOptionResult();
                                        optResult.setTargetFields(opt.getTargetFields());
                                        optResult.setRequiredLevel(opt.getRequiredLevel());
                                        optResult.setApprovalLevel(opt.getApprovalLevel());
                                        optResult.setValidityPeriod(opt.getValidityPeriod());
                                        return optResult;
                                    })
                                    .collect(Collectors.toList()));
                        }
                        return groupResult;
                    })
                    .collect(Collectors.toList()));
        }

        return result;
    }

    private BatchSummary buildSummary(List<AccessCheckItem> items, List<BatchCheckResult> results) {
        BatchSummary summary = new BatchSummary();

        Set<Long> users = items.stream()
                .map(AccessCheckItem::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> resources = items.stream()
                .map(AccessCheckItem::getResourceCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        summary.setTotalUsers(users.size());
        summary.setTotalResources(resources.size());
        summary.setAllowCount((int) results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getSuccess()) && "ALLOW".equals(r.getAccessDecision()))
                .count());
        summary.setDenyCount((int) results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getSuccess()) && "DENY".equals(r.getAccessDecision()))
                .count());
        summary.setPartialCount((int) results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getSuccess()) && "PARTIAL".equals(r.getAccessDecision()))
                .count());
        summary.setErrorCount((int) results.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getSuccess()))
                .count());

        return summary;
    }
}
