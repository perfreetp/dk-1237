package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysPermissionRecovery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysPermissionRecoveryMapper extends BaseMapper<SysPermissionRecovery> {

    List<SysPermissionRecovery> selectByTaskId(@Param("taskId") String taskId);

    List<SysPermissionRecovery> selectPendingRecoveries();

    List<SysPermissionRecovery> selectByStatusAndTaskId(@Param("taskId") String taskId, @Param("status") Integer status);

    Long countFailedByTaskId(@Param("taskId") String taskId);
}
