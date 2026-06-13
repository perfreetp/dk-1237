package com.example.datapermission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datapermission.entity.SysPermissionTemplate;
import com.example.datapermission.entity.SysPermissionTemplateDetail;
import com.example.datapermission.exception.BusinessException;
import com.example.datapermission.mapper.SysPermissionTemplateDetailMapper;
import com.example.datapermission.mapper.SysPermissionTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysPermissionTemplateService extends ServiceImpl<SysPermissionTemplateMapper, SysPermissionTemplate> {

    private final SysPermissionTemplateMapper templateMapper;
    private final SysPermissionTemplateDetailMapper detailMapper;

    public SysPermissionTemplate getById(Long id) {
        SysPermissionTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(404, "权限模板不存在");
        }
        List<SysPermissionTemplateDetail> details = detailMapper.selectByTemplateId(id);
        template.setDetails(details);
        return template;
    }

    public SysPermissionTemplate getByCode(String code) {
        LambdaQueryWrapper<SysPermissionTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermissionTemplate::getTemplateCode, code);
        SysPermissionTemplate template = templateMapper.selectOne(wrapper);
        if (template != null) {
            List<SysPermissionTemplateDetail> details = detailMapper.selectByTemplateId(template.getId());
            template.setDetails(details);
        }
        return template;
    }

    public List<SysPermissionTemplate> getAll() {
        List<SysPermissionTemplate> templates = templateMapper.selectList(null);
        for (SysPermissionTemplate template : templates) {
            List<SysPermissionTemplateDetail> details = detailMapper.selectByTemplateId(template.getId());
            template.setDetails(details);
        }
        return templates;
    }

    @Transactional
    public SysPermissionTemplate create(SysPermissionTemplate template) {
        checkTemplateCodeUnique(template.getTemplateCode(), null);
        templateMapper.insert(template);

        if (template.getDetails() != null && !template.getDetails().isEmpty()) {
            for (SysPermissionTemplateDetail detail : template.getDetails()) {
                detail.setTemplateId(template.getId());
                detailMapper.insert(detail);
            }
        }
        return template;
    }

    @Transactional
    public SysPermissionTemplate update(Long id, SysPermissionTemplate template) {
        SysPermissionTemplate existing = getById(id);
        checkTemplateCodeUnique(template.getTemplateCode(), id);
        existing.setTemplateName(template.getTemplateName());
        existing.setDescription(template.getDescription());
        templateMapper.updateById(existing);

        detailMapper.deleteByTemplateId(id);
        if (template.getDetails() != null && !template.getDetails().isEmpty()) {
            for (SysPermissionTemplateDetail detail : template.getDetails()) {
                detail.setTemplateId(id);
                detailMapper.insert(detail);
            }
        }
        return getById(id);
    }

    @Transactional
    public void delete(Long id) {
        detailMapper.deleteByTemplateId(id);
        templateMapper.deleteById(id);
    }

    private void checkTemplateCodeUnique(String templateCode, Long excludeId) {
        LambdaQueryWrapper<SysPermissionTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermissionTemplate::getTemplateCode, templateCode);
        if (excludeId != null) {
            wrapper.ne(SysPermissionTemplate::getId, excludeId);
        }
        Long count = templateMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "模板编码已存在");
        }
    }
}
