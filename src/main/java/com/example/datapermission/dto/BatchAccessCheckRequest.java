package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchAccessCheckRequest {

    private List<AccessCheckItem> items;

    private Boolean continueOnError = true;

    private Boolean parallel = true;

    @Data
    public static class AccessCheckItem {
        private String itemId;
        private Long userId;
        private String resourceCode;
        private String operationType;
        private ComplexConditions complexConditions;
        private List<String> requestedFields;
    }

    @Data
    public static class ComplexConditions {
        private TimeRange timeRange;
        private CustomerLevel customerLevel;
        private ProjectScope projectScope;
    }

    @Data
    public static class TimeRange {
        private String field;
        private String startTime;
        private String endTime;
    }

    @Data
    public static class CustomerLevel {
        private String field;
        private List<Integer> levels;
    }

    @Data
    public static class ProjectScope {
        private String field;
        private List<Long> projectIds;
    }
}
