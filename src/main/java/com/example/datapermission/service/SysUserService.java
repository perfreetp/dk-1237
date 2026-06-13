package com.example.datapermission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.datapermission.entity.SysUser;
import com.example.datapermission.exception.BusinessException;
import com.example.datapermission.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    public SysUser getByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    public SysUser login(String username, String password) {
        SysUser user = getByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(401, "用户已禁用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return user;
    }

    @Transactional
    public SysUser create(SysUser user) {
        checkUsernameUnique(user.getUsername(), null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public SysUser update(Long id, SysUser user) {
        SysUser existing = getById(id);
        existing.setRealName(user.getRealName());
        existing.setEmail(user.getEmail());
        existing.setPhone(user.getPhone());
        existing.setOrgId(user.getOrgId());
        existing.setPostId(user.getPostId());
        existing.setStatus(user.getStatus());
        userMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        SysUser user = getById(id);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Transactional
    public void leave(Long id) {
        SysUser user = getById(id);
        user.setStatus(0);
        user.setLeaveDate(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(SysUser::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
    }

    public List<SysUser> getLeavedUsers() {
        return userMapper.selectLeavedUsersBefore(LocalDateTime.now());
    }

    public List<SysUser> getUsersByOrgIds(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectUsersByOrgIds(orgIds);
    }

    public List<SysUser> getUsersByOrgId(Long orgId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getOrgId, orgId)
                .eq(SysUser::getStatus, 1);
        return userMapper.selectList(wrapper);
    }
}
