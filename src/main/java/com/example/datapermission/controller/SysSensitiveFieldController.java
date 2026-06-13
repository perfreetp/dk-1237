package com.example.datapermission.controller;

import com.example.datapermission.entity.SysSensitiveField;
import com.example.datapermission.service.SysSensitiveFieldService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/sensitive-field")
@RequiredArgsConstructor
public class SysSensitiveFieldController {

    private final SysSensitiveFieldService fieldService;

    @GetMapping("/{id}")
    public Result<SysSensitiveField> getById(@PathVariable Long id) {
        return Result.success(fieldService.getById(id));
    }

    @GetMapping("/resource/{resourceId}")
    public Result<List<SysSensitiveField>> getByResourceId(@PathVariable Long resourceId) {
        return Result.success(fieldService.getByResourceId(resourceId));
    }

    @PostMapping
    public Result<SysSensitiveField> create(@RequestBody SysSensitiveField field) {
        return Result.success(fieldService.create(field));
    }

    @PostMapping("/batch")
    public Result<Void> batchCreate(@RequestBody List<SysSensitiveField> fields) {
        if (fields != null && !fields.isEmpty()) {
            fieldService.batchCreate(fields.get(0).getResourceId(), fields);
        }
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<SysSensitiveField> update(@PathVariable Long id, @RequestBody SysSensitiveField field) {
        return Result.success(fieldService.update(id, field));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fieldService.delete(id);
        return Result.success();
    }

    @DeleteMapping("/resource/{resourceId}")
    public Result<Void> deleteByResourceId(@PathVariable Long resourceId) {
        fieldService.deleteByResourceId(resourceId);
        return Result.success();
    }
}
