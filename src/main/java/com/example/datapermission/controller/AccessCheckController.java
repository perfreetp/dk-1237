package com.example.datapermission.controller;

import com.example.datapermission.dto.AccessCheckRequest;
import com.example.datapermission.dto.AccessCheckResponse;
import com.example.datapermission.service.AccessCheckService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/access")
@RequiredArgsConstructor
public class AccessCheckController {

    private final AccessCheckService accessCheckService;

    @PostMapping("/check")
    public Result<AccessCheckResponse> checkAccess(@RequestBody AccessCheckRequest request) {
        AccessCheckResponse response = accessCheckService.checkAccess(request);
        return Result.success(response);
    }

    @PostMapping("/check-batch")
    public Result<List<AccessCheckResponse>> checkAccessBatch(@RequestBody List<AccessCheckRequest> requests) {
        List<AccessCheckResponse> responses = requests.stream()
                .map(accessCheckService::checkAccess)
                .toList();
        return Result.success(responses);
    }
}
