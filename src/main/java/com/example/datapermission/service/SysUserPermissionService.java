package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datapermission.dto.UserPermissionDTO;
import com.example.datapermission.entity.SysOrganization;
import com.example.datapermission.entity.SysPermissionTemplate;
import com.example.datapermission.entity.SysResource;
import com.example.datapermission.entity.SysUser;
import com.example.datapermission.entity.SysUserPermission;
import com.example.datapermission.exception.BusinessException;
import com.example.datapermission.mapper.SysUserPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserPermissionService extends ServiceImpl<SysUserPermissionMapper, SysUserPermission> {

    private final SysUserPermissionMapper permissionMapper;
    private final SysUserService userService;
    private final SysResourceService resourceService;
    private final SysOrganizationService organizationService;
    private final SysPermissionTemplateService templateService;

    public SysUserPermission getById(Long id) {
        SysUserPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(404, "用户权限不存在");
        }
        return permission;
    }

    public UserPermissionDTO getDTOById(Long id) {
        SysUserPermission permission = getById(id);
        return convertToDTO(permission);
    }

    public Page<UserPermissionDTO> pageQuery(Long userId, Long resourceId, Integer pageNum, Integer pageSize) {
        Page<SysUserPermission> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(SysUserPermission::getUserId, userId);
        }
        if (resourceId != null) {
            wrapper.eq(SysUserPermission::getResourceId, resourceId);
        }
        wrapper.orderByDesc(SysUserPermission::getCreatedTime);
        Page<SysUserPermission> result = permissionMapper.selectPage(page, wrapper);

        Page<UserPermissionDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<UserPermissionDTO> dtoList = result.getRecords().stream().map(this::convertToDTO).toList();
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    private UserPermissionDTO convertToDTO(SysUserPermission permission) {
        UserPermissionDTO dto = new UserPermissionDTO();
        dto.setId(permission.getId());
        dto.setUserId(permission.getUserId());
        dto.setResourceId(permission.getResourceId());
        dto.setOrgScopeType(permission.getOrgScopeType());
        dto.setPermissionTemplateId(permission.getPermissionTemplateId());
        dto.setOperationTypes(permission.getOperationTypes());
        dto.setFieldAccessLevel(permission.getFieldAccessLevel());
        dto.setDesensitizationEnabled(permission.getDesensitizationEnabled());
        dto.setStartTime(permission.getStartTime());
        dto.setEndTime(permission.getEndTime());
        dto.setGrantReason(permission.getGrantReason());
        dto.setGrantType(permission.getGrantType());
        dto.setStatus(permission.getStatus());
        dto.setCreatedTime(permission.getCreatedTime());

        try {
            SysUser user = userService.getById(permission.getUserId());
            dto.setUserName(user.getUsername());
            dto.setRealName(user.getRealName());
        } catch (Exception e) {
            dto.setUserName("未知");
        }

        try {
            SysResource resource = resourceService.getById(permission.getResourceId());
            dto.setResourceCode(resource.getResourceCode());
            dto.setResourceName(resource.getResourceName());
        } catch (Exception e) {
            dto.setResourceName("未知");
        }

        if (permission.getPermissionTemplateId() != null) {
            try {
                SysPermissionTemplate template = templateService.getById(permission.getPermissionTemplateId());
                dto.setPermissionTemplateName(template.getTemplateName());
            } catch (Exception e) {
                dto.setPermissionTemplateName("未知");
            }
        }

        if (permission.getStatus() != null) {
            dto.setStatusName(permission.getStatus() == 1 ? "生效中" : "已撤销");
        }

        return dto;
    }

    public List<SysUserPermission> getActiveByUserId(Long userId) {
        return permissionMapper.selectActiveByUserId(userId);
    }

    public List<SysUserPermission> getByUserIdAndResourceId(Long userId, Long resourceId) {
        return permissionMapper.selectByUserIdAndResourceId(userId, resourceId);
    }

    @Transactional
    public SysUserPermission create(SysUserPermission permission) {
        validatePermission(permission);

        SysUser user = userService.getById(permission.getUserId());
        permission.setOrgId(user.getOrgId());

        permissionMapper.insert(permission);
        return permission;
    }

    @Transactional
    public SysUserPermission update(Long id, SysUserPermission permission) {
        SysUserPermission existing = getById(id);
        validatePermission(permission);
        existing.setResourceId(permission.getResourceId());
        existing.setOrgScopeType(permission.getOrgScopeType());
        existing.setOrgScopeValue(permission.getOrgScopeValue());
        existing.setPermissionTemplateId(permission.getPermissionTemplateId());
        existing.setOperationTypes(permission.getOperationTypes());
        existing.setFieldAccessLevel(permission.getFieldAccessLevel());
        existing.setDesensitizationEnabled(permission.getDesensitizationEnabled());
        existing.setStartTime(permission.getStartTime());
        existing.setEndTime(permission.getEndTime());
        existing.setGrantReason(permission.getGrantReason());
        existing.setGrantType(permission.getGrantType());
        existing.setStatus(permission.getStatus());
        permissionMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void revoke(Long id) {
        SysUserPermission permission = getById(id);
        permission.setStatus(0);
        permission.setEndTime(LocalDateTime.now());
        permissionMapper.updateById(permission);
    }

    @Transactional
    public void delete(Long id) {
        permissionMapper.deleteById(id);
    }

    private void validatePermission(SysUserPermission permission) {
        if (permission.getStartTime() != null && permission.getEndTime() != null) {
            if (permission.getStartTime().isAfter(permission.getEndTime())) {
                throw new BusinessException(400, "授权开始时间不能晚于结束时间");
            }
        }

        if (permission.getResourceId() != null) {
            resourceService.getById(permission.getResourceId());
        }

        if (permission.getPermissionTemplateId() != null) {
            templateService.getById(permission.getPermissionTemplateId());
        }
    }

    public List<SysUserPermission> selectExpiringPermissions(int days) {
        LocalDateTime date = LocalDateTime.now().plusDays(days);
        return permissionMapper.selectExpiringPermissions(date);
    }

    public List<SysUserPermission> selectExpiredPermissions() {
        return permissionMapper.selectExpiredPermissions(LocalDateTime.now());
    }
}
