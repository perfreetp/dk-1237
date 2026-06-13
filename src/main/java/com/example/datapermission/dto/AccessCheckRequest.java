package com.example.datapermission.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AccessCheckRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "资源编码不能为空")
    private String resourceCode;

    @NotNull(message = "操作类型不能为空")
    private String operationType;

    private Object queryConditions;

    private List<String> requestedFields;
}
