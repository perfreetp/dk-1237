package com.example.datapermission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.OrgScopeDTO;
import com.example.datapermission.entity.SysOrgScope;
import com.example.datapermission.service.SysOrgScopeService;
import com.example.datapermission.vo.PageResult;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/org-scope")
@RequiredArgsConstructor
public class SysOrgScopeController {

    private final SysOrgScopeService orgScopeService;

    @GetMapping("/{id}")
    public Result<SysOrgScope> getById(@PathVariable Long id) {
        return Result.success(orgScopeService.getById(id));
    }

    @GetMapping("/page")
    public Result<PageResult<OrgScopeDTO>> pageQuery(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long sourceOrgId,
            @RequestParam(required = false) String grantType) {
        Page<OrgScopeDTO> page = orgScopeService.pageQuery(pageNum, pageSize, sourceOrgId, grantType);
        PageResult<OrgScopeDTO> result = new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
        return Result.success(result);
    }

    @PostMapping
    public Result<SysOrgScope> create(@RequestBody SysOrgScope scope) {
        return Result.success(orgScopeService.create(scope));
    }

    @PutMapping("/{id}")
    public Result<SysOrgScope> update(@PathVariable Long id, @RequestBody SysOrgScope scope) {
        return Result.success(orgScopeService.update(id, scope));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orgScopeService.delete(id);
        return Result.success();
    }
}
