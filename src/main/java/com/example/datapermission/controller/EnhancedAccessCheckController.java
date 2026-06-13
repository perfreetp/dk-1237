package com.example.datapermission.controller;

import com.example.datapermission.dto.EnhancedAccessCheckRequest;
import com.example.datapermission.dto.EnhancedAccessCheckResponse;
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

    @PostMapping("/check-v2")
    public Result<EnhancedAccessCheckResponse> checkAccessV2(@RequestBody EnhancedAccessCheckRequest request) {
        EnhancedAccessCheckResponse response = accessCheckService.checkAccess(request);
        return Result.success(response);
    }

    @PostMapping("/check-batch-v2")
    public Result<List<EnhancedAccessCheckResponse>> checkAccessBatchV2(@RequestBody List<EnhancedAccessCheckRequest> requests) {
        List<EnhancedAccessCheckResponse> responses = requests.stream()
                .map(accessCheckService::checkAccess)
                .toList();
        return Result.success(responses);
    }
}
