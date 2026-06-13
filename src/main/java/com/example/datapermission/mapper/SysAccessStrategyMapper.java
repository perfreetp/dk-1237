package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysAccessStrategy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysAccessStrategyMapper extends BaseMapper<SysAccessStrategy> {

    SysAccessStrategy selectByCallerAndDomain(@Param("callerCode") String callerCode,
                                              @Param("tenantId") String tenantId,
                                              @Param("resourceDomain") String resourceDomain);
}
