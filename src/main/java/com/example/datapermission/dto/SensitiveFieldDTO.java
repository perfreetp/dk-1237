package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class SensitiveFieldDTO {
    private Long id;
    private Long resourceId;
    private String resourceCode;
    private String resourceName;
    private String fieldName;
    private String fieldLabel;
    private Integer sensitivityLevel;
    private String sensitivityLevelName;
    private String desensitizationType;
    private String desensitizationTypeName;
    private String maskPattern;

    @Data
    public static class BatchImportRequest {
        private Long resourceId;
        private List<FieldItem> fields;

        @Data
        public static class FieldItem {
            private String fieldName;
            private String fieldLabel;
            private Integer sensitivityLevel;
            private String desensitizationType;
            private String maskPattern;
        }
    }
}
