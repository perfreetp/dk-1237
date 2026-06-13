package com.example.datapermission.controller;

import com.example.datapermission.entity.SysResource;
import com.example.datapermission.service.SysResourceService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/resource")
@RequiredArgsConstructor
public class SysResourceController {

    private final SysResourceService resourceService;

    @GetMapping("/{id}")
    public Result<SysResource> getById(@PathVariable Long id) {
        return Result.success(resourceService.getById(id));
    }

    @GetMapping("/code/{code}")
    public Result<SysResource> getByCode(@PathVariable String code) {
        return Result.success(resourceService.getByCode(code));
    }

    @GetMapping("/list")
    public Result<List<SysResource>> getAll() {
        return Result.success(resourceService.getAll());
    }

    @GetMapping("/type/{resourceType}")
    public Result<List<SysResource>> getByType(@PathVariable String resourceType) {
        return Result.success(resourceService.getByType(resourceType));
    }

    @PostMapping
    public Result<SysResource> create(@RequestBody SysResource resource) {
        return Result.success(resourceService.create(resource));
    }

    @PutMapping("/{id}")
    public Result<SysResource> update(@PathVariable Long id, @RequestBody SysResource resource) {
        return Result.success(resourceService.update(id, resource));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resourceService.delete(id);
        return Result.success();
    }
}
