package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RuleConflictRequest {

    private Long sourceOrgId;

    private Long targetOrgId;

    private List<String> grantTypes;

    private String previewType;

    private Map<String, Object> ruleChanges;

    @Data
    public static class RuleChange {
        private Long ruleId;
        private String field;
        private Object oldValue;
        private Object newValue;
    }
}
