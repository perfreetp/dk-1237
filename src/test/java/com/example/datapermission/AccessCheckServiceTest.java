package com.example.datapermission;

import com.example.datapermission.dto.AccessCheckRequest;
import com.example.datapermission.dto.AccessCheckResponse;
import com.example.datapermission.service.AccessCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AccessCheckServiceTest {

    @Autowired
    private AccessCheckService accessCheckService;

    @Test
    public void testCheckAccessWithPermission() {
        AccessCheckRequest request = new AccessCheckRequest();
        request.setUserId(2L);
        request.setResourceCode("employee_table");
        request.setOperationType("READ");
        request.setRequestedFields(List.of("name", "department", "email"));

        AccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response);
        assertNotNull(response.getAccessDecision());
    }

    @Test
    public void testCheckAccessWithSensitiveField() {
        AccessCheckRequest request = new AccessCheckRequest();
        request.setUserId(3L);
        request.setResourceCode("employee_table");
        request.setOperationType("READ");
        request.setRequestedFields(List.of("name", "phone", "salary"));

        AccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response);
        assertNotNull(response.getHiddenFields());
        assertNotNull(response.getMaskedFields());
    }

    @Test
    public void testCheckAccessWithoutPermission() {
        AccessCheckRequest request = new AccessCheckRequest();
        request.setUserId(999L);
        request.setResourceCode("financial_table");
        request.setOperationType("READ");
        request.setRequestedFields(List.of("revenue", "profit"));

        AccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response);
    }

    @Test
    public void testCheckAccessExport() {
        AccessCheckRequest request = new AccessCheckRequest();
        request.setUserId(2L);
        request.setResourceCode("employee_table");
        request.setOperationType("EXPORT");
        request.setRequestedFields(List.of("name", "department"));

        AccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response);
    }

    @Test
    public void testBatchCheck() {
        List<AccessCheckRequest> requests = List.of(
                createRequest(2L, "employee_table", "READ"),
                createRequest(3L, "project_table", "READ")
        );

        for (AccessCheckRequest request : requests) {
            AccessCheckResponse response = accessCheckService.checkAccess(request);
            assertNotNull(response);
        }
    }

    private AccessCheckRequest createRequest(Long userId, String resourceCode, String operationType) {
        AccessCheckRequest request = new AccessCheckRequest();
        request.setUserId(userId);
        request.setResourceCode(resourceCode);
        request.setOperationType(operationType);
        request.setRequestedFields(List.of("name"));
        return request;
    }
}
