package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysPermissionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysPermissionTaskMapper extends BaseMapper<SysPermissionTask> {

    List<SysPermissionTask> selectPendingTasks(@Param("date") LocalDateTime date);

    List<SysPermissionTask> selectByUserId(@Param("userId") Long userId);

    List<SysPermissionTask> selectByTaskType(@Param("taskType") String taskType);
}
