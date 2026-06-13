package com.example.datapermission.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ImpactEstimateResponse {
    private String simulationId;
    private String changeType;
    private Long userId;
    private String resourceCode;
    private ImpactSummary summary;
    private List<AffectedUser> affectedUsers;
    private List<AffectedPost> affectedPosts;
    private List<AffectedResource> affectedResources;
    private Map<String, Object> changeDetails;
    private boolean requiresApproval;
    private String approvalLevel;
    private String reason;

    @Data
    @Builder
    public static class ImpactSummary {
        private Integer totalAffectedUsers;
        private Integer totalAffectedPosts;
        private Integer totalAffectedResources;
        private String impactLevel;
        private List<String> impactDimensions;
        private List<String> riskPoints;
    }

    @Data
    @Builder
    public static class AffectedUser {
        private Long userId;
        private String userName;
        private String orgName;
        private String postName;
        private String changeDescription;
        private String beforeValue;
        private String afterValue;
    }

    @Data
    @Builder
    public static class AffectedPost {
        private Long postId;
        private String postName;
        private String orgName;
        private Integer affectedUserCount;
        private String changeDescription;
    }

    @Data
    @Builder
    public static class AffectedResource {
        private Long resourceId;
        private String resourceCode;
        private String resourceName;
        private String resourceDomain;
        private Integer sensitivityLevel;
        private Integer affectedUserCount;
        private String changeDescription;
    }
}
