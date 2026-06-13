package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysPermissionReviewItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysPermissionReviewItemMapper extends BaseMapper<SysPermissionReviewItem> {

    void batchInsert(@Param("items") java.util.List<SysPermissionReviewItem> items);
}
