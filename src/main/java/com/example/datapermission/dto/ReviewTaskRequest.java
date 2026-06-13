package com.example.datapermission.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewTaskRequest {

    private String taskName;

    private ReviewScope scope;

    private List<String> riskFilters;

    private List<Long> reviewers;

    private LocalDateTime dueDate;

    private Boolean autoRemind = true;

    private Integer remindInterval = 3;

    @Data
    public static class ReviewScope {
        private List<Long> orgIds;
        private List<Long> userIds;
        private List<String> resourceTypes;
    }
}
