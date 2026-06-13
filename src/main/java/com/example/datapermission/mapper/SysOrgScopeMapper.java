package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysOrgScope;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SysOrgScopeMapper extends BaseMapper<SysOrgScope> {

    List<SysOrgScope> selectActiveBySourceOrgId(Long sourceOrgId);

    List<SysOrgScope> selectByGrantType(String grantType);
}
