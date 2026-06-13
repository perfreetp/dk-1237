package com.example.datapermission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datapermission.entity.SysOrganization;
import com.example.datapermission.exception.BusinessException;
import com.example.datapermission.mapper.SysOrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysOrganizationService extends ServiceImpl<SysOrganizationMapper, SysOrganization> {

    private final SysOrganizationMapper organizationMapper;

    public SysOrganization getById(Long id) {
        SysOrganization org = organizationMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(404, "组织不存在");
        }
        return org;
    }

    public List<SysOrganization> getByParentId(Long parentId) {
        LambdaQueryWrapper<SysOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOrganization::getParentId, parentId)
                .eq(SysOrganization::getStatus, 1)
                .orderByAsc(SysOrganization::getSortOrder);
        return organizationMapper.selectList(wrapper);
    }

    public List<SysOrganization> getChildren(Long orgId, Integer depth) {
        List<SysOrganization> result = new ArrayList<>();
        collectChildren(orgId, depth, 1, result);
        return result;
    }

    private void collectChildren(Long parentId, Integer maxDepth, int currentDepth, List<SysOrganization> result) {
        if (maxDepth != null && currentDepth > maxDepth) {
            return;
        }

        List<SysOrganization> children = getByParentId(parentId);
        for (SysOrganization child : children) {
            result.add(child);
            collectChildren(child.getId(), maxDepth, currentDepth + 1, result);
        }
    }

    public List<Long> getOrgIdsInHierarchy(Long orgId) {
        List<Long> orgIds = new ArrayList<>();
        orgIds.add(orgId);
        List<SysOrganization> children = getChildren(orgId, null);
        for (SysOrganization org : children) {
            orgIds.add(org.getId());
        }
        return orgIds;
    }

    @Transactional
    public SysOrganization create(SysOrganization organization) {
        checkOrgCodeUnique(organization.getOrgCode(), null);
        organizationMapper.insert(organization);
        return organization;
    }

    @Transactional
    public SysOrganization update(Long id, SysOrganization organization) {
        SysOrganization existing = getById(id);
        checkOrgCodeUnique(organization.getOrgCode(), id);
        existing.setOrgCode(organization.getOrgCode());
        existing.setOrgName(organization.getOrgName());
        existing.setOrgType(organization.getOrgType());
        existing.setParentId(organization.getParentId());
        existing.setHierarchyLevel(organization.getHierarchyLevel());
        existing.setSortOrder(organization.getSortOrder());
        existing.setStatus(organization.getStatus());
        organizationMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        SysOrganization org = getById(id);
        List<SysOrganization> children = getByParentId(id);
        if (!children.isEmpty()) {
            throw new BusinessException(400, "该组织存在子组织，无法删除");
        }
        organizationMapper.deleteById(id);
    }

    private void checkOrgCodeUnique(String orgCode, Long excludeId) {
        LambdaQueryWrapper<SysOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOrganization::getOrgCode, orgCode);
        if (excludeId != null) {
            wrapper.ne(SysOrganization::getId, excludeId);
        }
        Long count = organizationMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "组织编码已存在");
        }
    }

    public List<SysOrganization> getAllActive() {
        LambdaQueryWrapper<SysOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOrganization::getStatus, 1)
                .orderByAsc(SysOrganization::getHierarchyLevel, SysOrganization::getSortOrder);
        return organizationMapper.selectList(wrapper);
    }
}
