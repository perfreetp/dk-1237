package com.example.datapermission.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.dto.ImpactEstimateResponse;
import com.example.datapermission.dto.ImpactEstimateResponse.*;
import com.example.datapermission.dto.RuleChangeApprovalRequest;
import com.example.datapermission.dto.RuleChangeApprovalResponse;
import com.example.datapermission.dto.RuleSimulationRequest;
import com.example.datapermission.entity.*;
import com.example.datapermission.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactEstimateService {

    private final SysUserMapper userMapper;
    private final SysUserPermissionMapper permissionMapper;
    private final SysResourceMapper resourceMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysPostMapper postMapper;
    private final SysRuleChangeApprovalMapper approvalMapper;
    private final DeepRuleSimulationService simulationService;

    public ImpactEstimateResponse estimateImpact(RuleSimulationRequest request) {
        ImpactEstimateResponse response = new ImpactEstimateResponse();
        String simulationId = UUID.randomUUID().toString();
        response.setSimulationId(simulationId);
        response.setUserId(request.getUserId());
        response.setResourceCode(request.getResourceCode());
        response.setChangeType("RULE_SIMULATION");

        SysUser user = userMapper.selectById(request.getUserId());
        SysResource resource = resourceMapper.selectOne(
                new LambdaQueryWrapper<SysResource>()
                        .eq(SysResource::getResourceCode, request.getResourceCode())
        );

        List<SysUserPermission> affectedPermissions = getAffectedPermissions(request);

        List<AffectedUser> affectedUsers = buildAffectedUsers(affectedPermissions, request, user);
        response.setAffectedUsers(affectedUsers);

        List<AffectedPost> affectedPosts = buildAffectedPosts(affectedPermissions, request);
        response.setAffectedPosts(affectedPosts);

        List<AffectedResource> affectedResources = buildAffectedResources(affectedPermissions, request, resource);
        response.setAffectedResources(affectedResources);

        ImpactSummary summary = buildImpactSummary(affectedUsers, affectedPosts, affectedResources, request);
        response.setSummary(summary);

        boolean requiresApproval = determineApprovalRequired(summary, request);
        response.setRequiresApproval(requiresApproval);
        response.setApprovalLevel(determineApprovalLevel(summary));
        response.setReason(generateReason(summary, requiresApproval));

        Map<String, Object> changeDetails = new HashMap<>();
        if (request.getTempAdjustments() != null) {
            changeDetails.put("orgScope", request.getTempAdjustments().getOrgScope());
            changeDetails.put("project", request.getTempAdjustments().getProject());
            changeDetails.put("field", request.getTempAdjustments().getField());
        }
        response.setChangeDetails(changeDetails);

        return response;
    }

    @Transactional
    public RuleChangeApprovalResponse createApproval(RuleChangeApprovalRequest request, Long createdBy) {
        SysUser user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        ImpactEstimateResponse impactEstimate = null;
        if (StringUtils.hasText(request.getImpactEstimateJson())) {
            impactEstimate = JSON.parseObject(request.getImpactEstimateJson(), ImpactEstimateResponse.class);
        }

        String approvalNo = generateApprovalNo();

        SysRuleChangeApproval approval = new SysRuleChangeApproval();
        approval.setApprovalNo(approvalNo);
        approval.setChangeType(request.getChangeType() != null ? request.getChangeType() : "RULE_SIMULATION");
        approval.setUserId(request.getUserId());
        approval.setUserName(user.getUsername());
        approval.setResourceCode(request.getResourceCode());
        approval.setBusinessScenario(request.getBusinessScenario());
        approval.setChangeContent(request.getTempAdjustmentsJson());
        approval.setImpactEstimate(request.getImpactEstimateJson());
        approval.setStatus("PENDING");

        if (impactEstimate != null && impactEstimate.getRequiresApproval()) {
            List<Long> approverIds = request.getApproverIds();
            if (approverIds != null && !approverIds.isEmpty()) {
                SysUser approver = userMapper.selectById(approverIds.get(0));
                approval.setApproverId(approverIds.get(0));
                approval.setApproverName(approver != null ? approver.getUsername() : null);
            }
        }

        approval.setCreatedBy(createdBy);
        approval.setCreatedTime(LocalDateTime.now());

        approvalMapper.insert(approval);

        return RuleChangeApprovalResponse.builder()
                .approvalNo(approvalNo)
                .status(approval.getStatus())
                .changeType(approval.getChangeType())
                .userId(approval.getUserId())
                .userName(approval.getUserName())
                .resourceCode(approval.getResourceCode())
                .impactEstimate(impactEstimate)
                .approverId(approval.getApproverId())
                .approverName(approval.getApproverName())
                .createdTime(approval.getCreatedTime())
                .build();
    }

    @Transactional
    public RuleChangeApprovalResponse approve(String approvalNo, Long approverId, String comment) {
        SysRuleChangeApproval approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<SysRuleChangeApproval>()
                        .eq(SysRuleChangeApproval::getApprovalNo, approvalNo)
        );

        if (approval == null) {
            throw new RuntimeException("审批单不存在");
        }

        if (!"PENDING".equals(approval.getStatus())) {
            throw new RuntimeException("审批单状态不允许审批");
        }

        SysUser approver = userMapper.selectById(approverId);
        if (approver == null) {
            throw new RuntimeException("审批人不存在");
        }

        approval.setStatus("APPROVED");
        approval.setApproverId(approverId);
        approval.setApproverName(approver.getUsername());
        approval.setApprovalComment(comment);
        approval.setApproveTime(LocalDateTime.now());
        approval.setUpdatedTime(LocalDateTime.now());

        approvalMapper.updateById(approval);

        if (StringUtils.hasText(approval.getChangeContent())) {
            RuleSimulationRequest simRequest = JSON.parseObject(approval.getChangeContent(), RuleSimulationRequest.class);
            if (simRequest != null) {
                simulationService.saveSimulatedRules(simRequest);
            }
        }

        ImpactEstimateResponse impactEstimate = null;
        if (StringUtils.hasText(approval.getImpactEstimate())) {
            impactEstimate = JSON.parseObject(approval.getImpactEstimate(), ImpactEstimateResponse.class);
        }

        return RuleChangeApprovalResponse.builder()
                .approvalNo(approval.getApprovalNo())
                .status(approval.getStatus())
                .changeType(approval.getChangeType())
                .userId(approval.getUserId())
                .userName(approval.getUserName())
                .resourceCode(approval.getResourceCode())
                .impactEstimate(impactEstimate)
                .approverId(approval.getApproverId())
                .approverName(approval.getApproverName())
                .approvalComment(approval.getApprovalComment())
                .approveTime(approval.getApproveTime())
                .createdTime(approval.getCreatedTime())
                .build();
    }

    @Transactional
    public RuleChangeApprovalResponse reject(String approvalNo, Long approverId, String comment) {
        SysRuleChangeApproval approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<SysRuleChangeApproval>()
                        .eq(SysRuleChangeApproval::getApprovalNo, approvalNo)
        );

        if (approval == null) {
            throw new RuntimeException("审批单不存在");
        }

        SysUser approver = userMapper.selectById(approverId);

        approval.setStatus("REJECTED");
        approval.setApproverId(approverId);
        approval.setApproverName(approver != null ? approver.getUsername() : null);
        approval.setApprovalComment(comment);
        approval.setApproveTime(LocalDateTime.now());
        approval.setUpdatedTime(LocalDateTime.now());

        approvalMapper.updateById(approval);

        ImpactEstimateResponse impactEstimate = null;
        if (StringUtils.hasText(approval.getImpactEstimate())) {
            impactEstimate = JSON.parseObject(approval.getImpactEstimate(), ImpactEstimateResponse.class);
        }

        return RuleChangeApprovalResponse.builder()
                .approvalNo(approval.getApprovalNo())
                .status(approval.getStatus())
                .userId(approval.getUserId())
                .userName(approval.getUserName())
                .approvalComment(approval.getApprovalComment())
                .approveTime(approval.getApproveTime())
                .build();
    }

    public RuleChangeApprovalResponse getApproval(String approvalNo) {
        SysRuleChangeApproval approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<SysRuleChangeApproval>()
                        .eq(SysRuleChangeApproval::getApprovalNo, approvalNo)
        );

        if (approval == null) {
            throw new RuntimeException("审批单不存在");
        }

        ImpactEstimateResponse impactEstimate = null;
        if (StringUtils.hasText(approval.getImpactEstimate())) {
            impactEstimate = JSON.parseObject(approval.getImpactEstimate(), ImpactEstimateResponse.class);
        }

        return RuleChangeApprovalResponse.builder()
                .approvalNo(approval.getApprovalNo())
                .status(approval.getStatus())
                .changeType(approval.getChangeType())
                .userId(approval.getUserId())
                .userName(approval.getUserName())
                .resourceCode(approval.getResourceCode())
                .impactEstimate(impactEstimate)
                .approverId(approval.getApproverId())
                .approverName(approval.getApproverName())
                .approvalComment(approval.getApprovalComment())
                .approveTime(approval.getApproveTime())
                .createdTime(approval.getCreatedTime())
                .build();
    }

    public List<RuleChangeApprovalResponse> listApprovals(String status, Long approverId, int page, int size) {
        LambdaQueryWrapper<SysRuleChangeApproval> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(status)) {
            wrapper.eq(SysRuleChangeApproval::getStatus, status);
        }
        if (approverId != null) {
            wrapper.eq(SysRuleChangeApproval::getApproverId, approverId);
        }

        wrapper.orderByDesc(SysRuleChangeApproval::getCreatedTime);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);

        List<SysRuleChangeApproval> approvals = approvalMapper.selectList(wrapper);

        return approvals.stream().map(approval -> {
            ImpactEstimateResponse impactEstimate = null;
            if (StringUtils.hasText(approval.getImpactEstimate())) {
                impactEstimate = JSON.parseObject(approval.getImpactEstimate(), ImpactEstimateResponse.class);
            }

            return RuleChangeApprovalResponse.builder()
                    .approvalNo(approval.getApprovalNo())
                    .status(approval.getStatus())
                    .changeType(approval.getChangeType())
                    .userId(approval.getUserId())
                    .userName(approval.getUserName())
                    .resourceCode(approval.getResourceCode())
                    .impactEstimate(impactEstimate)
                    .approverId(approval.getApproverId())
                    .approverName(approval.getApproverName())
                    .approvalComment(approval.getApprovalComment())
                    .approveTime(approval.getApproveTime())
                    .createdTime(approval.getCreatedTime())
                    .build();
        }).collect(Collectors.toList());
    }

    private List<SysUserPermission> getAffectedPermissions(RuleSimulationRequest request) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();

        if (request.getUserId() != null) {
            wrapper.eq(SysUserPermission::getUserId, request.getUserId());
        }

        if (StringUtils.hasText(request.getResourceCode())) {
            SysResource resource = resourceMapper.selectOne(
                    new LambdaQueryWrapper<SysResource>()
                            .eq(SysResource::getResourceCode, request.getResourceCode())
            );
            if (resource != null) {
                wrapper.eq(SysUserPermission::getResourceId, resource.getId());
            }
        }

        wrapper.eq(SysUserPermission::getStatus, 1);
        return permissionMapper.selectList(wrapper);
    }

    private List<AffectedUser> buildAffectedUsers(List<SysUserPermission> permissions,
                                                  RuleSimulationRequest request,
                                                  SysUser requestUser) {
        List<AffectedUser> affected = new ArrayList<>();

        Set<Long> processedUsers = new HashSet<>();

        for (SysUserPermission perm : permissions) {
            if (processedUsers.contains(perm.getUserId())) continue;
            processedUsers.add(perm.getUserId());

            SysUser user = userMapper.selectById(perm.getUserId());
            SysOrganization org = user != null ? organizationMapper.selectById(user.getOrgId()) : null;

            String changeDesc = buildChangeDescription(perm, request);

            String beforeValue = String.format("权限等级: %d, 组织范围: %s",
                    perm.getFieldAccessLevel(), perm.getOrgScopeValue());
            String afterValue = changeDesc;

            affected.add(AffectedUser.builder()
                    .userId(perm.getUserId())
                    .userName(user != null ? user.getUsername() : "未知")
                    .orgName(org != null ? org.getOrgName() : "未知")
                    .postName(getPostName(user != null ? user.getPostId() : null))
                    .changeDescription(changeDesc)
                    .beforeValue(beforeValue)
                    .afterValue(afterValue)
                    .build());
        }

        if (requestUser != null && !processedUsers.contains(requestUser.getId())) {
            SysOrganization org = organizationMapper.selectById(requestUser.getOrgId());
            affected.add(AffectedUser.builder()
                    .userId(requestUser.getId())
                    .userName(requestUser.getUsername())
                    .orgName(org != null ? org.getOrgName() : "未知")
                    .postName(getPostName(requestUser.getPostId()))
                    .changeDescription("规则调整-直接受影响")
                    .beforeValue("当前权限配置")
                    .afterValue("模拟后权限配置")
                    .build());
        }

        return affected;
    }

    private List<AffectedPost> buildAffectedPosts(List<SysUserPermission> permissions, RuleSimulationRequest request) {
        Map<Long, List<SysUserPermission>> byPost = new HashMap<>();

        for (SysUserPermission perm : permissions) {
            SysUser user = userMapper.selectById(perm.getUserId());
            if (user != null && user.getPostId() != null) {
                byPost.computeIfAbsent(user.getPostId(), k -> new ArrayList<>()).add(perm);
            }
        }

        return byPost.entrySet().stream().map(entry -> {
            SysPost post = postMapper.selectById(entry.getKey());
            SysOrganization org = post != null ? organizationMapper.selectById(post.getOrgId()) : null;

            Set<Long> uniqueUsers = entry.getValue().stream()
                    .map(SysUserPermission::getUserId)
                    .collect(Collectors.toSet());

            return AffectedPost.builder()
                    .postId(entry.getKey())
                    .postName(post != null ? post.getPostName() : "未知")
                    .orgName(org != null ? org.getOrgName() : "未知")
                    .affectedUserCount(uniqueUsers.size())
                    .changeDescription("岗位模板权限调整")
                    .build();
        }).collect(Collectors.toList());
    }

    private List<AffectedResource> buildAffectedResources(List<SysUserPermission> permissions,
                                                          RuleSimulationRequest request,
                                                          SysResource resource) {
        List<AffectedResource> affected = new ArrayList<>();

        Map<Long, List<SysUserPermission>> byResource = permissions.stream()
                .collect(Collectors.groupingBy(SysUserPermission::getResourceId));

        for (Map.Entry<Long, List<SysUserPermission>> entry : byResource.entrySet()) {
            SysResource res = resourceMapper.selectById(entry.getKey());
            if (res == null) continue;

            Set<Long> uniqueUsers = entry.getValue().stream()
                    .map(SysUserPermission::getUserId)
                    .collect(Collectors.toSet());

            affected.add(AffectedResource.builder()
                    .resourceId(res.getId())
                    .resourceCode(res.getResourceCode())
                    .resourceName(res.getResourceName())
                    .resourceDomain(res.getResourceType())
                    .sensitivityLevel(res.getSensitivityLevel())
                    .affectedUserCount(uniqueUsers.size())
                    .changeDescription("资源权限规则调整")
                    .build());
        }

        return affected;
    }

    private ImpactSummary buildImpactSummary(List<AffectedUser> users,
                                             List<AffectedPost> posts,
                                             List<AffectedResource> resources,
                                             RuleSimulationRequest request) {
        List<String> impactDimensions = new ArrayList<>();
        List<String> riskPoints = new ArrayList<>();

        if (!users.isEmpty()) {
            impactDimensions.add("USER");
        }
        if (!posts.isEmpty()) {
            impactDimensions.add("POST");
        }
        if (!resources.isEmpty()) {
            impactDimensions.add("RESOURCE");
        }

        boolean hasHighSensitivity = resources.stream()
                .anyMatch(r -> r.getSensitivityLevel() != null && r.getSensitivityLevel() >= 4);
        if (hasHighSensitivity) {
            riskPoints.add("涉及高敏感级别资源");
        }

        long highLevelChanges = users.stream()
                .filter(u -> u.getAfterValue() != null && u.getAfterValue().contains("Level"))
                .count();
        if (highLevelChanges > 5) {
            riskPoints.add("大量用户权限等级变更");
        }

        String impactLevel = "LOW";
        int totalAffected = users.size() + posts.size() + resources.size();
        if (totalAffected > 50 || hasHighSensitivity) {
            impactLevel = "HIGH";
        } else if (totalAffected > 10) {
            impactLevel = "MEDIUM";
        }

        return ImpactSummary.builder()
                .totalAffectedUsers(users.size())
                .totalAffectedPosts(posts.size())
                .totalAffectedResources(resources.size())
                .impactLevel(impactLevel)
                .impactDimensions(impactDimensions)
                .riskPoints(riskPoints)
                .build();
    }

    private boolean determineApprovalRequired(ImpactSummary summary, RuleSimulationRequest request) {
        if ("HIGH".equals(summary.getImpactLevel())) {
            return true;
        }

        if (summary.getTotalAffectedUsers() > 20) {
            return true;
        }

        boolean hasHighSensitivity = summary.getTotalAffectedResources() > 0;
        if (hasHighSensitivity) {
            return true;
        }

        if (request.getTempAdjustments() != null) {
            if (request.getTempAdjustments().getField() != null &&
                    request.getTempAdjustments().getField().getTemporaryDesensitizationLevel() != null &&
                    request.getTempAdjustments().getField().getTemporaryDesensitizationLevel() >= 5) {
                return true;
            }
        }

        return false;
    }

    private String determineApprovalLevel(ImpactSummary summary) {
        if ("HIGH".equals(summary.getImpactLevel())) {
            return "HIGH";
        }
        if (summary.getTotalAffectedUsers() > 50) {
            return "HIGH";
        }
        return "NORMAL";
    }

    private String generateReason(ImpactSummary summary, boolean requiresApproval) {
        if (!requiresApproval) {
            return "影响范围小，无需审批";
        }

        StringBuilder reason = new StringBuilder();
        if (summary.getTotalAffectedUsers() > 0) {
            reason.append(String.format("涉及 %d 个用户; ", summary.getTotalAffectedUsers()));
        }
        if ("HIGH".equals(summary.getImpactLevel())) {
            reason.append("影响等级为高; ");
        }
        if (!summary.getRiskPoints().isEmpty()) {
            reason.append(String.join(", ", summary.getRiskPoints()));
        }
        return reason.toString();
    }

    private String buildChangeDescription(SysUserPermission permission, RuleSimulationRequest request) {
        List<String> changes = new ArrayList<>();

        if (request.getTempAdjustments() != null) {
            if (request.getTempAdjustments().getOrgScope() != null) {
                changes.add("组织范围调整");
            }
            if (request.getTempAdjustments().getProject() != null) {
                changes.add("项目权限调整");
            }
            if (request.getTempAdjustments().getField() != null) {
                if (request.getTempAdjustments().getField().getTemporaryDesensitizationLevel() != null) {
                    changes.add(String.format("临时脱敏等级: %d",
                            request.getTempAdjustments().getField().getTemporaryDesensitizationLevel()));
                }
            }
        }

        return changes.isEmpty() ? "权限规则模拟调整" : String.join(", ", changes);
    }

    private String getPostName(Long postId) {
        if (postId == null) return "未知";
        SysPost post = postMapper.selectById(postId);
        return post != null ? post.getPostName() : "未知";
    }

    private String generateApprovalNo() {
        return "APR-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                String.format("%04d", new Random().nextInt(10000));
    }
}
