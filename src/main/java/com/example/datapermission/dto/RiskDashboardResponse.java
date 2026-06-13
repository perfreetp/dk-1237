package com.example.datapermission.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RiskDashboardResponse {
    private RiskSummary summary;
    private List<RiskCategoryStats> categoryStats;
    private Map<String, Map<String, Long>> crossDimensionMatrix;
    private List<TrendData> trendData;

    @Data
    @Builder
    public static class RiskSummary {
        private Long expiringCount;
        private Long unusedCount;
        private Long overGrantedCount;
        private Long abnormalDownloadCount;
        private Long totalRisks;
        private Double riskScore;
    }

    @Data
    @Builder
    public static class RiskCategoryStats {
        private String category;
        private String categoryName;
        private Long count;
        private Double percentage;
        private List<RiskDetail> details;
    }

    @Data
    @Builder
    public static class RiskDetail {
        private Long id;
        private Long userId;
        private String userName;
        private String orgName;
        private String postName;
        private String resourceName;
        private String riskType;
        private Integer riskLevel;
        private String riskDescription;
        private String suggestAction;
        private Long permissionId;
    }

    @Data
    @Builder
    public static class TrendData {
        private String date;
        private String metric;
        private Long value;
    }
}
