package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysSensitiveField;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SysSensitiveFieldMapper extends BaseMapper<SysSensitiveField> {

    List<SysSensitiveField> selectByResourceId(Long resourceId);
}
