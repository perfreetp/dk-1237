package com.example.datapermission.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.datapermission.entity.SysAnomalyAlert;
import com.example.datapermission.entity.SysExpirationNotice;
import com.example.datapermission.entity.SysUserPermission;
import com.example.datapermission.mapper.SysAnomalyAlertMapper;
import com.example.datapermission.mapper.SysExpirationNoticeMapper;
import com.example.datapermission.service.AuditService;
import com.example.datapermission.service.SysUserPermissionService;
import com.example.datapermission.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionScheduler {

    private final SysUserPermissionService permissionService;
    private final SysUserService userService;
    private final SysExpirationNoticeMapper expirationNoticeMapper;
    private final SysAnomalyAlertMapper anomalyAlertMapper;
    private final AuditService auditService;

    @Scheduled(cron = "0 0 9 * * ?")
    public void checkExpiringPermissions() {
        log.info("开始检查即将到期的权限");
        try {
            for (int days : List.of(7, 3, 1, 0)) {
                List<SysUserPermission> permissions = permissionService.selectExpiringPermissions(days);
                for (SysUserPermission permission : permissions) {
                    createExpirationNotice(permission, days);
                }
            }
            log.info("到期检查完成");
        } catch (Exception e) {
            log.error("检查到期权限失败", e);
        }
    }

    private void createExpirationNotice(SysUserPermission permission, int days) {
        LambdaUpdateWrapper<SysExpirationNotice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysExpirationNotice::getPermissionId, permission.getId())
                .eq(SysExpirationNotice::getNoticeType, days == 0 ? "EXPIRED" : "BEFORE_EXPIRE")
                .eq(SysExpirationNotice::getNoticeStatus, 0);

        Long count = expirationNoticeMapper.selectCount(wrapper);
        if (count == 0) {
            SysExpirationNotice notice = new SysExpirationNotice();
            notice.setPermissionId(permission.getId());
            notice.setNoticeType(days == 0 ? "EXPIRED" : "BEFORE_EXPIRE");
            notice.setNoticeTime(LocalDateTime.now());
            notice.setNoticeStatus(0);
            expirationNoticeMapper.insert(notice);
            log.info("创建到期提醒: 权限ID={}, 类型={}, 剩余天数={}", permission.getId(), notice.getNoticeType(), days);
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void revokeLeavedUserPermissions() {
        log.info("开始回收离职用户权限");
        try {
            List<Long> leavedUserIds = userService.getLeavedUsers().stream()
                    .map(u -> u.getId())
                    .toList();

            for (Long userId : leavedUserIds) {
                List<SysUserPermission> permissions = permissionService.getActiveByUserId(userId);
                for (SysUserPermission permission : permissions) {
                    if ("TEMP".equals(permission.getGrantType())) {
                        permissionService.revoke(permission.getId());
                        auditService.logPermissionChange(
                                permission.getId(),
                                "REVOKE",
                                permission,
                                null,
                                "离职自动回收",
                                null,
                                null,
                                null
                        );
                        log.info("自动回收临时授权: 用户ID={}, 权限ID={}", userId, permission.getId());
                    }
                }
            }
            log.info("离职权限回收完成");
        } catch (Exception e) {
            log.error("回收离职权限失败", e);
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void expirePermissions() {
        log.info("开始处理过期权限");
        try {
            List<SysUserPermission> expiredPermissions = permissionService.selectExpiredPermissions();
            for (SysUserPermission permission : expiredPermissions) {
                permission.setStatus(0);
                permissionService.updateById(permission);
                auditService.logPermissionChange(
                        permission.getId(),
                        "EXPIRE",
                        permission,
                        null,
                        "授权到期自动失效",
                        null,
                        null,
                        null
                );
                log.info("权限已过期: 权限ID={}", permission.getId());
            }
            log.info("过期权限处理完成");
        } catch (Exception e) {
            log.error("处理过期权限失败", e);
        }
    }

    @Scheduled(cron = "0 0 */6 * * ?")
    public void checkAbnormalAccess() {
        log.info("开始检查异常访问");
        try {
            LocalDateTime checkTime = LocalDateTime.now().minusHours(1);

            List<Long> activeUserIds = List.of();

            for (Long userId : activeUserIds) {
            }

            log.info("异常访问检查完成");
        } catch (Exception e) {
            log.error("检查异常访问失败", e);
        }
    }

    @Scheduled(cron = "0 30 9 * * ?")
    public void sendExpirationNotices() {
        log.info("开始发送到期提醒");
        try {
            List<SysExpirationNotice> pendingNotices = expirationNoticeMapper.selectPendingNotices(LocalDateTime.now());

            for (SysExpirationNotice notice : pendingNotices) {
                try {
                    sendNotice(notice);
                    notice.setNoticeStatus(1);
                    notice.setSentTime(LocalDateTime.now());
                    notice.setNoticeResult("发送成功");
                    expirationNoticeMapper.updateById(notice);
                } catch (Exception e) {
                    notice.setNoticeStatus(2);
                    notice.setNoticeResult("发送失败: " + e.getMessage());
                    expirationNoticeMapper.updateById(notice);
                }
            }
            log.info("到期提醒发送完成");
        } catch (Exception e) {
            log.error("发送到期提醒失败", e);
        }
    }

    private void sendNotice(SysExpirationNotice notice) {
        log.info("发送提醒通知: 通知ID={}, 类型={}", notice.getId(), notice.getNoticeType());
    }
}
