package com.example.datapermission.controller;

import com.example.datapermission.entity.SysPermissionTemplate;
import com.example.datapermission.service.SysPermissionTemplateService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/permission-template")
@RequiredArgsConstructor
public class SysPermissionTemplateController {

    private final SysPermissionTemplateService templateService;

    @GetMapping("/{id}")
    public Result<SysPermissionTemplate> getById(@PathVariable Long id) {
        return Result.success(templateService.getById(id));
    }

    @GetMapping("/code/{code}")
    public Result<SysPermissionTemplate> getByCode(@PathVariable String code) {
        return Result.success(templateService.getByCode(code));
    }

    @GetMapping("/list")
    public Result<List<SysPermissionTemplate>> getAll() {
        return Result.success(templateService.getAll());
    }

    @PostMapping
    public Result<SysPermissionTemplate> create(@RequestBody SysPermissionTemplate template) {
        return Result.success(templateService.create(template));
    }

    @PutMapping("/{id}")
    public Result<SysPermissionTemplate> update(@PathVariable Long id, @RequestBody SysPermissionTemplate template) {
        return Result.success(templateService.update(id, template));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }
}
