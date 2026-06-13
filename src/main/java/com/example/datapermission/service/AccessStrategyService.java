package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.AccessStrategyRequest;
import com.example.datapermission.entity.SysAccessStrategy;
import com.example.datapermission.mapper.SysAccessStrategyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessStrategyService {

    private final SysAccessStrategyMapper strategyMapper;

    @Transactional
    public SysAccessStrategy createStrategy(AccessStrategyRequest request) {
        SysAccessStrategy strategy = new SysAccessStrategy();
        strategy.setStrategyCode(generateStrategyCode());
        strategy.setStrategyName(request.getStrategyName());
        strategy.setCallerCode(request.getCallerCode());
        strategy.setTenantId(request.getTenantId());
        strategy.setResourceDomain(request.getResourceDomain());
        strategy.setDefaultDesensitizationLevel(request.getDefaultDesensitizationLevel() != null ? request.getDefaultDesensitizationLevel() : 1);
        strategy.setDefaultOrgScopeType(request.getDefaultOrgScopeType() != null ? request.getDefaultOrgScopeType() : 0);
        strategy.setDefaultOrgScopeValue(request.getDefaultOrgScopeValue());
        strategy.setDefaultFieldAccessLevel(request.getDefaultFieldAccessLevel() != null ? request.getDefaultFieldAccessLevel() : 1);
        strategy.setFallbackDenyMessage(request.getFallbackDenyMessage() != null ? request.getFallbackDenyMessage() : "暂无访问权限，请联系管理员申请");
        strategy.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        strategy.setStatus(1);
        strategy.setExtendsConfig(request.getExtendsConfig() != null ? JSON.toJSONString(request.getExtendsConfig()) : null);
        strategy.setCreatedTime(java.time.LocalDateTime.now());

        strategyMapper.insert(strategy);
        return strategy;
    }

    public SysAccessStrategy getStrategy(Long id) {
        return strategyMapper.selectById(id);
    }

    public List<SysAccessStrategy> listStrategies(String callerCode, String tenantId, String resourceDomain) {
        LambdaQueryWrapper<SysAccessStrategy> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(callerCode)) {
            wrapper.eq(SysAccessStrategy::getCallerCode, callerCode);
        }
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(SysAccessStrategy::getTenantId, tenantId);
        }
        if (StringUtils.hasText(resourceDomain)) {
            wrapper.eq(SysAccessStrategy::getResourceDomain, resourceDomain);
        }

        wrapper.orderByAsc(SysAccessStrategy::getPriority);
        return strategyMapper.selectList(wrapper);
    }

    public SysAccessStrategy matchStrategy(String callerCode, String tenantId, String resourceDomain) {
        SysAccessStrategy strategy = strategyMapper.selectByCallerAndDomain(callerCode, tenantId, resourceDomain);

        if (strategy == null) {
            strategy = strategyMapper.selectOne(new LambdaQueryWrapper<SysAccessStrategy>()
                    .eq(SysAccessStrategy::getCallerCode, callerCode)
                    .eq(SysAccessStrategy::getTenantId, tenantId)
                    .isNull(SysAccessStrategy::getResourceDomain)
                    .orderByAsc(SysAccessStrategy::getPriority)
                    .last("LIMIT 1"));
        }

        if (strategy == null) {
            strategy = strategyMapper.selectOne(new LambdaQueryWrapper<SysAccessStrategy>()
                    .eq(SysAccessStrategy::getCallerCode, callerCode)
                    .isNull(SysAccessStrategy::getTenantId)
                    .isNull(SysAccessStrategy::getResourceDomain)
                    .orderByAsc(SysAccessStrategy::getPriority)
                    .last("LIMIT 1"));
        }

        if (strategy == null) {
            strategy = strategyMapper.selectOne(new LambdaQueryWrapper<SysAccessStrategy>()
                    .isNull(SysAccessStrategy::getCallerCode)
                    .isNull(SysAccessStrategy::getTenantId)
                    .isNull(SysAccessStrategy::getResourceDomain)
                    .orderByAsc(SysAccessStrategy::getPriority)
                    .last("LIMIT 1"));
        }

        return strategy;
    }

    @Transactional
    public SysAccessStrategy updateStrategy(Long id, AccessStrategyRequest request) {
        SysAccessStrategy strategy = strategyMapper.selectById(id);
        if (strategy == null) {
            throw new RuntimeException("策略不存在");
        }

        if (request.getStrategyName() != null) {
            strategy.setStrategyName(request.getStrategyName());
        }
        if (request.getDefaultDesensitizationLevel() != null) {
            strategy.setDefaultDesensitizationLevel(request.getDefaultDesensitizationLevel());
        }
        if (request.getDefaultOrgScopeType() != null) {
            strategy.setDefaultOrgScopeType(request.getDefaultOrgScopeType());
        }
        if (request.getDefaultOrgScopeValue() != null) {
            strategy.setDefaultOrgScopeValue(request.getDefaultOrgScopeValue());
        }
        if (request.getDefaultFieldAccessLevel() != null) {
            strategy.setDefaultFieldAccessLevel(request.getDefaultFieldAccessLevel());
        }
        if (request.getFallbackDenyMessage() != null) {
            strategy.setFallbackDenyMessage(request.getFallbackDenyMessage());
        }
        if (request.getPriority() != null) {
            strategy.setPriority(request.getPriority());
        }
        if (request.getExtendsConfig() != null) {
            strategy.setExtendsConfig(JSON.toJSONString(request.getExtendsConfig()));
        }

        strategy.setUpdatedTime(java.time.LocalDateTime.now());
        strategyMapper.updateById(strategy);
        return strategy;
    }

    @Transactional
    public void deleteStrategy(Long id) {
        strategyMapper.deleteById(id);
    }

    public String getFallbackDenyMessage(SysAccessStrategy strategy) {
        if (strategy != null && StringUtils.hasText(strategy.getFallbackDenyMessage())) {
            return strategy.getFallbackDenyMessage();
        }
        return "暂无访问权限，请联系管理员申请";
    }

    public JSONObject getStrategyExtends(SysAccessStrategy strategy) {
        if (strategy != null && StringUtils.hasText(strategy.getExtendsConfig())) {
            return JSON.parseObject(strategy.getExtendsConfig());
        }
        return new JSONObject();
    }

    private String generateStrategyCode() {
        return "STR" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) +
                String.format("%04d", (int)(Math.random() * 10000));
    }
}
