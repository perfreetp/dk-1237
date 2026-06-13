package com.example.datapermission.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RiskTrendResponse {
    private String riskType;
    private String riskTypeName;
    private List<TrendPoint> trendPoints;
    private TrendSummary summary;
    private List<RiskItemDetail> recentItems;

    @Data
    @Builder
    public static class TrendPoint {
        private String date;
        private Long count;
        private Double changeRate;
        private String changeDirection;
    }

    @Data
    @Builder
    public static class TrendSummary {
        private Long currentCount;
        private Long previousCount;
        private Double changeRate;
        private String trend;
        private Boolean isImproving;
    }

    @Data
    @Builder
    public static class RiskItemDetail {
        private Long id;
        private String userName;
        private String orgName;
        private String resourceName;
        private String riskDescription;
        private String status;
        private String handleStatus;
        private LocalDateTime createdTime;
    }
}
