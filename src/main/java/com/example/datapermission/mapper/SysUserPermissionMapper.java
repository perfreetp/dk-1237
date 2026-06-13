package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysUserPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysUserPermissionMapper extends BaseMapper<SysUserPermission> {

    List<SysUserPermission> selectActiveByUserId(@Param("userId") Long userId);

    List<SysUserPermission> selectExpiringPermissions(@Param("date") LocalDateTime date);

    List<SysUserPermission> selectExpiredPermissions(@Param("date") LocalDateTime date);

    List<SysUserPermission> selectByUserIdAndResourceId(@Param("userId") Long userId, @Param("resourceId") Long resourceId);
}
