package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PermissionExportRequest {

    private String type;

    private String format;

    private List<String> riskFilters;

    private List<Long> orgIds;

    private List<Long> userIds;

    private List<String> resourceTypes;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Boolean includeChangeHistory = true;

    private Boolean includeStatistics = true;
}
