package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class PermissionTemplateDTO {
    private Long id;
    private String templateCode;
    private String templateName;
    private String description;
    private List<TemplateDetailDTO> details;

    @Data
    public static class TemplateDetailDTO {
        private Long id;
        private Long resourceId;
        private String resourceCode;
        private String resourceName;
        private String operationType;
        private String fieldLevelMap;
    }
}
