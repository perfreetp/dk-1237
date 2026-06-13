package com.example.datapermission.dto;

import lombok.Data;
import java.util.List;

@Data
public class RiskSubscriptionRequest {
    private String subscriptionName;
    private Long subscriberId;
    private String subscriptionType;
    private List<Long> targetOrgIds;
    private List<Long> targetPostIds;
    private List<String> riskTypes;
    private String frequency;
    private String deliveryMethod;
    private String deliveryAddress;
}
