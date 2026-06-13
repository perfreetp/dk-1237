package com.example.datapermission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datapermission.entity.SysResource;
import com.example.datapermission.exception.BusinessException;
import com.example.datapermission.mapper.SysResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysResourceService extends ServiceImpl<SysResourceMapper, SysResource> {

    private final SysResourceMapper resourceMapper;

    public SysResource getById(Long id) {
        SysResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在");
        }
        return resource;
    }

    public SysResource getByCode(String code) {
        LambdaQueryWrapper<SysResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysResource::getResourceCode, code);
        SysResource resource = resourceMapper.selectOne(wrapper);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在: " + code);
        }
        return resource;
    }

    public List<SysResource> getAll() {
        LambdaQueryWrapper<SysResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysResource::getResourceCode);
        return resourceMapper.selectList(wrapper);
    }

    public List<SysResource> getByType(String resourceType) {
        LambdaQueryWrapper<SysResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysResource::getResourceType, resourceType)
                .orderByAsc(SysResource::getResourceCode);
        return resourceMapper.selectList(wrapper);
    }

    @Transactional
    public SysResource create(SysResource resource) {
        checkResourceCodeUnique(resource.getResourceCode(), null);
        resourceMapper.insert(resource);
        return resource;
    }

    @Transactional
    public SysResource update(Long id, SysResource resource) {
        SysResource existing = getById(id);
        checkResourceCodeUnique(resource.getResourceCode(), id);
        existing.setResourceCode(resource.getResourceCode());
        existing.setResourceName(resource.getResourceName());
        existing.setResourceType(resource.getResourceType());
        existing.setDescription(resource.getDescription());
        existing.setSensitivityLevel(resource.getSensitivityLevel());
        resourceMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        resourceMapper.deleteById(id);
    }

    private void checkResourceCodeUnique(String resourceCode, Long excludeId) {
        LambdaQueryWrapper<SysResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysResource::getResourceCode, resourceCode);
        if (excludeId != null) {
            wrapper.ne(SysResource::getId, excludeId);
        }
        Long count = resourceMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "资源编码已存在");
        }
    }
}
