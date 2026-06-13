package com.example.datapermission;

import com.example.datapermission.dto.EnhancedAccessCheckRequest;
import com.example.datapermission.dto.EnhancedAccessCheckResponse;
import com.example.datapermission.service.EnhancedAccessCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class EnhancedAccessCheckServiceTest {

    @Autowired
    private EnhancedAccessCheckService accessCheckService;

    @Test
    public void testCheckAccessV2() {
        EnhancedAccessCheckRequest request = new EnhancedAccessCheckRequest();
        request.setUserId(2L);
        request.setResourceCode("employee_table");
        request.setOperationType("READ");
        request.setRequestedFields(List.of("name", "department", "phone"));
        request.setVersion("v2");

        EnhancedAccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response);
        assertNotNull(response.getAccessDecision());
        assertNotNull(response.getAppliedRules());
        assertNotNull(response.getFieldPermissions());
    }

    @Test
    public void testCheckAccessWithComplexConditions() {
        EnhancedAccessCheckRequest request = new EnhancedAccessCheckRequest();
        request.setUserId(2L);
        request.setResourceCode("employee_table");
        request.setOperationType("READ");
        request.setRequestedFields(List.of("name", "salary"));

        EnhancedAccessCheckRequest.ComplexConditions conditions = new EnhancedAccessCheckRequest.ComplexConditions();

        EnhancedAccessCheckRequest.TimeRange timeRange = new EnhancedAccessCheckRequest.TimeRange();
        timeRange.setField("create_time");
        timeRange.setStartTime("2024-01-01");
        timeRange.setEndTime("2024-12-31");
        conditions.setTimeRange(timeRange);

        EnhancedAccessCheckRequest.CustomerLevel customerLevel = new EnhancedAccessCheckRequest.CustomerLevel();
        customerLevel.setField("customer_level");
        customerLevel.setLevels(List.of(1, 2, 3));
        conditions.setCustomerLevel(customerLevel);

        request.setComplexConditions(conditions);

        EnhancedAccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response);
        assertNotNull(response.getAccessibleScope());
        assertNotNull(response.getSqlFilters());
        assertNotNull(response.getSqlFilters().getWhereClause());
    }

    @Test
    public void testCheckAccessWithSqlFilter() {
        EnhancedAccessCheckRequest request = new EnhancedAccessCheckRequest();
        request.setUserId(2L);
        request.setResourceCode("employee_table");
        request.setOperationType("READ");
        request.setRequestedFields(List.of("name", "department"));
        request.setReturnSqlFilter(true);

        EnhancedAccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response.getSqlFilters());
        assertNotNull(response.getSqlFilters().getWhereClause());
    }

    @Test
    public void testCheckAccessReturnAppliedRules() {
        EnhancedAccessCheckRequest request = new EnhancedAccessCheckRequest();
        request.setUserId(2L);
        request.setResourceCode("employee_table");
        request.setOperationType("READ");
        request.setRequestedFields(List.of("name"));
        request.setReturnAppliedRules(true);

        EnhancedAccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response.getAppliedRules());
        assertTrue(response.getAppliedRules().size() > 0);
    }

    @Test
    public void testCheckAccessDenied() {
        EnhancedAccessCheckRequest request = new EnhancedAccessCheckRequest();
        request.setUserId(2L);
        request.setResourceCode("financial_table");
        request.setOperationType("DELETE");
        request.setRequestedFields(List.of("revenue"));

        EnhancedAccessCheckResponse response = accessCheckService.checkAccess(request);
        assertNotNull(response);
    }
}
