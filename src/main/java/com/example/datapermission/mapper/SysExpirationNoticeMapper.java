package com.example.datapermission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.datapermission.entity.SysExpirationNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysExpirationNoticeMapper extends BaseMapper<SysExpirationNotice> {

    List<SysExpirationNotice> selectPendingNotices(@Param("date") LocalDateTime date);
}
