package com.example.datapermission;

import com.example.datapermission.dto.LeaveRequest;
import com.example.datapermission.dto.TransferRequest;
import com.example.datapermission.service.PermissionWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PermissionWorkflowServiceTest {

    @Autowired
    private PermissionWorkflowService workflowService;

    @Test
    public void testInitiateLeaveProcess() {
        LeaveRequest request = new LeaveRequest();
        request.setLeaveDate(LocalDateTime.now().plusDays(7));
        request.setTransferToUserId(3L);
        request.setTransferPermissions(true);
        request.setNotifyReviewers(true);
        request.setReason("个人原因离职");

        LeaveRequest.LeaveProgress progress = workflowService.initiateLeaveProcess(2L, request);
        assertNotNull(progress);
        assertNotNull(progress.getTaskId());
        assertEquals("INITIATED", progress.getStatus());
        assertNotNull(progress.getSteps());
        assertTrue(progress.getSteps().size() > 0);
        assertNotNull(progress.getAffectedPermissions());
    }

    @Test
    public void testInitiateTransferProcess() {
        TransferRequest request = new TransferRequest();
        request.setTargetOrgId(3L);
        request.setTargetPostId(3L);
        request.setTransferDate(LocalDateTime.now().plusDays(3));
        request.setReason("岗位调整");
        request.setKeepPermissions(List.of(
                new TransferRequest.KeepPermission(1L, "项目延续需要")
        ));
        request.setRevokePermissions(List.of(
                new TransferRequest.RevokePermission(2L, "原部门专属权限")
        ));

        TransferRequest.TransferResult result = workflowService.initiateTransferProcess(2L, request);
        assertNotNull(result);
        assertNotNull(result.getTaskId());
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getPermissionChanges());
        assertNotNull(result.getComparison());
    }
}
