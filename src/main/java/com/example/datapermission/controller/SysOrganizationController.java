package com.example.datapermission.controller;

import com.example.datapermission.entity.SysOrganization;
import com.example.datapermission.service.SysOrganizationService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/organization")
@RequiredArgsConstructor
public class SysOrganizationController {

    private final SysOrganizationService organizationService;

    @GetMapping("/{id}")
    public Result<SysOrganization> getById(@PathVariable Long id) {
        return Result.success(organizationService.getById(id));
    }

    @GetMapping("/list")
    public Result<List<SysOrganization>> getAll() {
        return Result.success(organizationService.getAllActive());
    }

    @GetMapping("/children/{parentId}")
    public Result<List<SysOrganization>> getChildren(@PathVariable Long parentId) {
        return Result.success(organizationService.getByParentId(parentId));
    }

    @GetMapping("/hierarchy/{orgId}")
    public Result<List<SysOrganization>> getHierarchy(@PathVariable Long orgId,
                                                      @RequestParam(required = false) Integer depth) {
        return Result.success(organizationService.getChildren(orgId, depth));
    }

    @PostMapping
    public Result<SysOrganization> create(@RequestBody SysOrganization organization) {
        return Result.success(organizationService.create(organization));
    }

    @PutMapping("/{id}")
    public Result<SysOrganization> update(@PathVariable Long id, @RequestBody SysOrganization organization) {
        return Result.success(organizationService.update(id, organization));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return Result.success();
    }
}
