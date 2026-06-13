package com.example.datapermission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datapermission.entity.SysSensitiveField;
import com.example.datapermission.exception.BusinessException;
import com.example.datapermission.mapper.SysSensitiveFieldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysSensitiveFieldService extends ServiceImpl<SysSensitiveFieldMapper, SysSensitiveField> {

    private final SysSensitiveFieldMapper fieldMapper;

    public SysSensitiveField getById(Long id) {
        SysSensitiveField field = fieldMapper.selectById(id);
        if (field == null) {
            throw new BusinessException(404, "敏感字段不存在");
        }
        return field;
    }

    public List<SysSensitiveField> getByResourceId(Long resourceId) {
        LambdaQueryWrapper<SysSensitiveField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysSensitiveField::getResourceId, resourceId)
                .orderByAsc(SysSensitiveField::getSensitivityLevel);
        return fieldMapper.selectList(wrapper);
    }

    public SysSensitiveField getByResourceAndField(Long resourceId, String fieldName) {
        LambdaQueryWrapper<SysSensitiveField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysSensitiveField::getResourceId, resourceId)
                .eq(SysSensitiveField::getFieldName, fieldName);
        return fieldMapper.selectOne(wrapper);
    }

    @Transactional
    public SysSensitiveField create(SysSensitiveField field) {
        checkFieldUnique(field.getResourceId(), field.getFieldName(), null);
        fieldMapper.insert(field);
        return field;
    }

    @Transactional
    public void batchCreate(Long resourceId, List<SysSensitiveField> fields) {
        for (SysSensitiveField field : fields) {
            field.setResourceId(resourceId);
            create(field);
        }
    }

    @Transactional
    public SysSensitiveField update(Long id, SysSensitiveField field) {
        SysSensitiveField existing = getById(id);
        checkFieldUnique(field.getResourceId(), field.getFieldName(), id);
        existing.setFieldName(field.getFieldName());
        existing.setFieldLabel(field.getFieldLabel());
        existing.setSensitivityLevel(field.getSensitivityLevel());
        existing.setDesensitizationType(field.getDesensitizationType());
        existing.setMaskPattern(field.getMaskPattern());
        fieldMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        fieldMapper.deleteById(id);
    }

    @Transactional
    public void deleteByResourceId(Long resourceId) {
        LambdaQueryWrapper<SysSensitiveField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysSensitiveField::getResourceId, resourceId);
        fieldMapper.delete(wrapper);
    }

    private void checkFieldUnique(Long resourceId, String fieldName, Long excludeId) {
        LambdaQueryWrapper<SysSensitiveField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysSensitiveField::getResourceId, resourceId)
                .eq(SysSensitiveField::getFieldName, fieldName);
        if (excludeId != null) {
            wrapper.ne(SysSensitiveField::getId, excludeId);
        }
        Long count = fieldMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "该字段已存在");
        }
    }
}
