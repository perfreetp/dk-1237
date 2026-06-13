package com.example.datapermission.controller;

import com.example.datapermission.dto.BatchAccessCheckRequest;
import com.example.datapermission.dto.BatchAccessCheckResponse;
import com.example.datapermission.dto.EnhancedAccessCheckRequest;
import com.example.datapermission.dto.EnhancedAccessCheckResponse;
import com.example.datapermission.service.BatchAccessCheckService;
import com.example.datapermission.service.EnhancedAccessCheckService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/access")
@RequiredArgsConstructor
public class EnhancedAccessCheckController {

    private final EnhancedAccessCheckService accessCheckService;
    private final BatchAccessCheckService batchAccessCheckService;

    @PostMapping("/check-v2")
    public Result<EnhancedAccessCheckResponse> checkAccessV2(@RequestBody EnhancedAccessCheckRequest request) {
        EnhancedAccessCheckResponse response = accessCheckService.checkAccess(request);
        return Result.success(response);
    }

    @PostMapping("/check-batch-v2")
    public Result<BatchAccessCheckResponse> checkAccessBatchV2(@RequestBody BatchAccessCheckRequest request) {
        BatchAccessCheckResponse response = batchAccessCheckService.checkBatch(request);
        return Result.success(response);
    }
}
