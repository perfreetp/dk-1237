package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysPermissionTemplateDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysPermissionTemplateDetailMapper extends BaseMapper<SysPermissionTemplateDetail> {

    List<SysPermissionTemplateDetail> selectByTemplateId(@Param("templateId") Long templateId);

    void deleteByTemplateId(@Param("templateId") Long templateId);
}
