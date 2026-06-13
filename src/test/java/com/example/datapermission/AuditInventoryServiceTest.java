package com.example.datapermission;

import com.example.datapermission.dto.PermissionExportRequest;
import com.example.datapermission.dto.ReviewTaskRequest;
import com.example.datapermission.service.AuditInventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuditInventoryServiceTest {

    @Autowired
    private AuditInventoryService auditInventoryService;

    @Test
    public void testExportPermissions() {
        PermissionExportRequest request = new PermissionExportRequest();
        request.setType("USER");
        request.setFormat("JSON");
        request.setIncludeChangeHistory(true);
        request.setIncludeStatistics(true);

        Map<String, Object> result = auditInventoryService.exportPermissions(request);
        assertNotNull(result);
        assertNotNull(result.get("permissions"));
        assertNotNull(result.get("totalCount"));
        assertNotNull(result.get("statistics"));
    }

    @Test
    public void testExportPermissionsWithRiskFilters() {
        PermissionExportRequest request = new PermissionExportRequest();
        request.setType("USER");
        request.setFormat("JSON");
        request.setRiskFilters(List.of("EXPIRING", "UNUSED"));
        request.setIncludeChangeHistory(false);
        request.setIncludeStatistics(false);

        Map<String, Object> result = auditInventoryService.exportPermissions(request);
        assertNotNull(result);
        assertNotNull(result.get("riskPermissions"));
    }

    @Test
    public void testExportPermissionsByOrg() {
        PermissionExportRequest request = new PermissionExportRequest();
        request.setType("ORG");
        request.setFormat("JSON");
        request.setOrgIds(List.of(1L, 2L));
        request.setIncludeStatistics(true);

        Map<String, Object> result = auditInventoryService.exportPermissions(request);
        assertNotNull(result);
    }

    @Test
    public void testCreateReviewTask() {
        ReviewTaskRequest request = new ReviewTaskRequest();
        request.setTaskName("2024年Q1权限复核");
        request.setRiskFilters(List.of("EXPIRING", "UNUSED", "OVER_GRANTED"));
        request.setDueDate(LocalDateTime.now().plusMonths(1));
        request.setAutoRemind(true);
        request.setRemindInterval(3);

        ReviewTaskRequest.ReviewScope scope = new ReviewTaskRequest.ReviewScope();
        scope.setOrgIds(List.of(1L, 2L));
        scope.setResourceTypes(List.of("TABLE"));
        request.setScope(scope);

        Map<String, Object> result = auditInventoryService.createReviewTask(request);
        assertNotNull(result);
        assertNotNull(result.get("taskId"));
        assertEquals("CREATED", result.get("status"));
        assertNotNull(result.get("statistics"));
    }

    @Test
    public void testExportPermissionsWithDateRange() {
        PermissionExportRequest request = new PermissionExportRequest();
        request.setType("USER");
        request.setFormat("JSON");
        request.setStartDate(LocalDateTime.now().minusMonths(3));
        request.setEndDate(LocalDateTime.now());
        request.setIncludeChangeHistory(true);

        Map<String, Object> result = auditInventoryService.exportPermissions(request);
        assertNotNull(result);
        assertNotNull(result.get("changeHistory"));
    }
}
