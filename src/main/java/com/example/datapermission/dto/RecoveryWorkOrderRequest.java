package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class RecoveryWorkOrderRequest {
    private String triggerType;
    private Long targetUserId;
    private Long responsibleId;
    private String transferToUserId;
    private String remark;
    private LocalDateTime dueTime;
}
