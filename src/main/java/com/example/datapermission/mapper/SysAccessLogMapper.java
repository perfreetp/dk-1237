package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysAccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface SysAccessLogMapper extends BaseMapper<SysAccessLog> {

    Long countUserAccessToday(@Param("userId") Long userId, @Param("date") LocalDateTime date);

    Long countUserExportToday(@Param("userId") Long userId, @Param("date") LocalDateTime date);
}
