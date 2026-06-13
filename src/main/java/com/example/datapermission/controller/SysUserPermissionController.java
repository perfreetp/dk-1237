package com.example.datapermission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.UserPermissionDTO;
import com.example.datapermission.entity.SysUserPermission;
import com.example.datapermission.service.SysUserPermissionService;
import com.example.datapermission.vo.PageResult;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/user-permission")
@RequiredArgsConstructor
public class SysUserPermissionController {

    private final SysUserPermissionService permissionService;

    @GetMapping("/{id}")
    public Result<UserPermissionDTO> getById(@PathVariable Long id) {
        return Result.success(permissionService.getDTOById(id));
    }

    @GetMapping("/page")
    public Result<PageResult<UserPermissionDTO>> pageQuery(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<UserPermissionDTO> page = permissionService.pageQuery(userId, resourceId, pageNum, pageSize);
        PageResult<UserPermissionDTO> result = new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
        return Result.success(result);
    }

    @GetMapping("/user/{userId}")
    public Result<PageResult<UserPermissionDTO>> getByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<UserPermissionDTO> page = permissionService.pageQuery(userId, null, pageNum, pageSize);
        PageResult<UserPermissionDTO> result = new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
        return Result.success(result);
    }

    @PostMapping
    public Result<SysUserPermission> create(@RequestBody SysUserPermission permission,
                                            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId != null) {
            permission.setCreatedBy(operatorId);
        }
        return Result.success(permissionService.create(permission));
    }

    @PutMapping("/{id}")
    public Result<SysUserPermission> update(@PathVariable Long id, @RequestBody SysUserPermission permission) {
        return Result.success(permissionService.update(id, permission));
    }

    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        permissionService.revoke(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.success();
    }
}
