package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AlertDetailResponse {

    private Long alertId;

    private Long userId;

    private String userName;

    private String userOrg;

    private String userPost;

    private String alertType;

    private String alertContent;

    private Integer alertLevel;

    private String riskLevelName;

    private Integer riskScore;

    private List<TriggeredDimension> triggeredDimensions;

    private List<AccessLogDetail> relatedAccessLogs;

    private List<String> suggestions;

    private String handleStatus;

    private String handleStatusName;

    private LocalDateTime handleTime;

    private String handleResult;

    private LocalDateTime createdTime;

    private UserBehaviorStats userBehaviorStats;

    @Data
    public static class TriggeredDimension {
        private String dimension;
        private String dimensionName;
        private Integer threshold;
        private Integer actual;
        private Integer score;
        private String description;
    }

    @Data
    public static class AccessLogDetail {
        private Long logId;
        private LocalDateTime accessTime;
        private String resourceCode;
        private String resourceName;
        private String operationType;
        private String accessDecision;
        private Long recordCount;
        private Long dataVolume;
        private List<String> sensitiveFieldsAccessed;
        private String clientIp;
        private Long executionTimeMs;
        private String viewUrl;
    }

    @Data
    public static class UserBehaviorStats {
        private Integer avgDailyAccess;
        private Integer avgDailyDownload;
        private Integer totalAccessCount;
        private Integer totalDownloadCount;
        private LocalDateTime lastAccessTime;
        private Double anomalyScore;
    }
}
