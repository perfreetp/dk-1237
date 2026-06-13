package com.example.datapermission.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.datapermission.dto.RecoveryWorkOrderRequest;
import com.example.datapermission.dto.RecoveryWorkOrderResponse;
import com.example.datapermission.service.RecoveryWorkOrderService;
import com.example.datapermission.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/recovery")
@RequiredArgsConstructor
public class RecoveryWorkOrderController {

    private final RecoveryWorkOrderService workOrderService;

    @PostMapping("/work-order")
    public Result<RecoveryWorkOrderResponse> createWorkOrder(
            @RequestBody RecoveryWorkOrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        RecoveryWorkOrderResponse response = workOrderService.createWorkOrder(request, userId);
        return Result.success(response);
    }

    @GetMapping("/work-order/{orderNo}")
    public Result<RecoveryWorkOrderResponse> getWorkOrder(@PathVariable String orderNo) {
        RecoveryWorkOrderResponse response = workOrderService.getWorkOrder(orderNo);
        return Result.success(response);
    }

    @GetMapping("/work-order")
    public Result<Page<RecoveryWorkOrderResponse>> listWorkOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long responsibleId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<RecoveryWorkOrderResponse> page = workOrderService.listWorkOrders(status, responsibleId, pageNum, pageSize);
        return Result.success(page);
    }

    @GetMapping("/work-order/status/pending")
    public Result<List<RecoveryWorkOrderResponse>> listPendingWorkOrders(
            @RequestParam(required = false) Long responsibleId) {
        Page<RecoveryWorkOrderResponse> page = workOrderService.listWorkOrders("PENDING,PROCESSING,PARTIAL_FAILED",
                responsibleId, 1, 100);
        return Result.success(page.getRecords());
    }

    @GetMapping("/work-order/status/failed")
    public Result<List<RecoveryWorkOrderResponse>> listFailedWorkOrders(
            @RequestParam(required = false) Long responsibleId) {
        Page<RecoveryWorkOrderResponse> page = workOrderService.listWorkOrders("PARTIAL_FAILED",
                responsibleId, 1, 100);
        return Result.success(page.getRecords());
    }

    @GetMapping("/work-order/status/completed")
    public Result<List<RecoveryWorkOrderResponse>> listCompletedWorkOrders(
            @RequestParam(required = false) Long responsibleId) {
        Page<RecoveryWorkOrderResponse> page = workOrderService.listWorkOrders("COMPLETED",
                responsibleId, 1, 100);
        return Result.success(page.getRecords());
    }

    @PostMapping("/work-order/{orderNo}/retry")
    public Result<RecoveryWorkOrderResponse> retryWorkOrder(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        RecoveryWorkOrderResponse response = workOrderService.retryWorkOrder(orderNo, userId);
        return Result.success(response);
    }

    @PostMapping("/work-order/item/{recoveryId}/retry")
    public Result<RecoveryWorkOrderResponse> retrySingleItem(
            @PathVariable Long recoveryId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        RecoveryWorkOrderResponse response = workOrderService.retrySingleItem(recoveryId, userId);
        return Result.success(response);
    }
}
