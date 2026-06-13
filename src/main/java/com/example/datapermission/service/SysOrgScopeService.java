package com.example.datapermission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datapermission.dto.OrgScopeDTO;
import com.example.datapermission.entity.SysOrgScope;
import com.example.datapermission.entity.SysOrganization;
import com.example.datapermission.exception.BusinessException;
import com.example.datapermission.mapper.SysOrgScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysOrgScopeService extends ServiceImpl<SysOrgScopeMapper, SysOrgScope> {

    private final SysOrgScopeMapper orgScopeMapper;
    private final SysOrganizationService organizationService;

    public SysOrgScope getById(Long id) {
        SysOrgScope scope = orgScopeMapper.selectById(id);
        if (scope == null) {
            throw new BusinessException(404, "组织范围授权不存在");
        }
        return scope;
    }

    public Page<OrgScopeDTO> pageQuery(Integer pageNum, Integer pageSize, Long sourceOrgId, String grantType) {
        Page<SysOrgScope> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysOrgScope> wrapper = new LambdaQueryWrapper<>();

        if (sourceOrgId != null) {
            wrapper.eq(SysOrgScope::getSourceOrgId, sourceOrgId);
        }
        if (grantType != null && !grantType.isEmpty()) {
            wrapper.eq(SysOrgScope::getGrantType, grantType);
        }
        wrapper.orderByDesc(SysOrgScope::getCreatedTime);
        Page<SysOrgScope> result = orgScopeMapper.selectPage(page, wrapper);

        Page<OrgScopeDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<OrgScopeDTO> dtoList = result.getRecords().stream().map(this::convertToDTO).toList();
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    private OrgScopeDTO convertToDTO(SysOrgScope scope) {
        OrgScopeDTO dto = new OrgScopeDTO();
        dto.setId(scope.getId());
        dto.setGrantType(scope.getGrantType());
        dto.setSourceOrgId(scope.getSourceOrgId());
        dto.setTargetOrgType(scope.getTargetOrgType());
        dto.setHierarchyDepth(scope.getHierarchyDepth());
        dto.setStartTime(scope.getStartTime());
        dto.setEndTime(scope.getEndTime());
        dto.setStatus(scope.getStatus());
        dto.setCreatedTime(scope.getCreatedTime());

        try {
            SysOrganization sourceOrg = organizationService.getById(scope.getSourceOrgId());
            dto.setSourceOrgName(sourceOrg.getOrgName());
        } catch (Exception e) {
            dto.setSourceOrgName("未知");
        }

        if (scope.getTargetOrgId() != null) {
            try {
                SysOrganization targetOrg = organizationService.getById(scope.getTargetOrgId());
                dto.setTargetOrgId(targetOrg.getId());
                dto.setTargetOrgName(targetOrg.getOrgName());
            } catch (Exception e) {
                dto.setTargetOrgName("未知");
            }
        }

        if (scope.getStatus() != null) {
            dto.setStatusName(scope.getStatus() == 1 ? "生效中" : "已失效");
        }
        return dto;
    }

    public List<SysOrgScope> getActiveBySourceOrgId(Long sourceOrgId) {
        return orgScopeMapper.selectActiveBySourceOrgId(sourceOrgId);
    }

    public List<SysOrgScope> getByGrantType(String grantType) {
        return orgScopeMapper.selectByGrantType(grantType);
    }

    @Transactional
    public SysOrgScope create(SysOrgScope scope) {
        if (scope.getStartTime() != null && scope.getEndTime() != null) {
            if (scope.getStartTime().isAfter(scope.getEndTime())) {
                throw new BusinessException(400, "生效时间不能晚于失效时间");
            }
        }
        orgScopeMapper.insert(scope);
        return scope;
    }

    @Transactional
    public SysOrgScope update(Long id, SysOrgScope scope) {
        SysOrgScope existing = getById(id);
        if (scope.getStartTime() != null && scope.getEndTime() != null) {
            if (scope.getStartTime().isAfter(scope.getEndTime())) {
                throw new BusinessException(400, "生效时间不能晚于失效时间");
            }
        }
        existing.setGrantType(scope.getGrantType());
        existing.setSourceOrgId(scope.getSourceOrgId());
        existing.setTargetOrgId(scope.getTargetOrgId());
        existing.setTargetOrgType(scope.getTargetOrgType());
        existing.setHierarchyDepth(scope.getHierarchyDepth());
        existing.setStartTime(scope.getStartTime());
        existing.setEndTime(scope.getEndTime());
        existing.setStatus(scope.getStatus());
        orgScopeMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        orgScopeMapper.deleteById(id);
    }

    public List<Long> getVisibleOrgIds(Long sourceOrgId, String grantType, Integer depth) {
        SysOrganization sourceOrg = organizationService.getById(sourceOrgId);

        if ("HEADQUARTER_VIEW_SUB".equals(grantType)) {
            if (depth == null || depth > 0) {
                return organizationService.getOrgIdsInHierarchy(sourceOrgId);
            }
        }

        List<SysOrgScope> scopes = getActiveBySourceOrgId(sourceOrgId);
        List<Long> result = new java.util.ArrayList<>();
        result.add(sourceOrgId);

        for (SysOrgScope scope : scopes) {
            if ("HEADQUARTER_VIEW_SUB".equals(scope.getGrantType())) {
                Integer hierarchyDepth = scope.getHierarchyDepth();
                if (hierarchyDepth == null) {
                    hierarchyDepth = Integer.MAX_VALUE;
                }
                List<SysOrganization> children = organizationService.getChildren(sourceOrgId, hierarchyDepth);
                for (SysOrganization org : children) {
                    result.add(org.getId());
                }
            }
        }

        return result.stream().distinct().toList();
    }
}
