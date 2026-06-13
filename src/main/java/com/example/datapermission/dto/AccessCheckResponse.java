package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AccessCheckResponse {

    private String accessDecision;

    private Boolean allowed;

    private AccessScope accessibleScope;

    private List<String> hiddenFields;

    private List<MaskedField> maskedFields;

    private String deniedReason;

    private String applyUrl;

    private List<String> suggestions;

    private List<QueryFilter> queryFilters;

    private Long executionTime;

    @Data
    public static class AccessScope {
        private List<Long> orgIds;
        private String orgType;
    }

    @Data
    public static class MaskedField {
        private String field;
        private String maskedValue;
        private String reason;
    }

    @Data
    public static class QueryFilter {
        private String field;
        private String operator;
        private Object value;
    }
}
