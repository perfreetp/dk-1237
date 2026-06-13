package com.example.datapermission;

import com.example.datapermission.dto.AlertHandleRequest;
import com.example.datapermission.entity.SysAnomalyAlert;
import com.example.datapermission.enums.AlertAction;
import com.example.datapermission.mapper.SysAnomalyAlertMapper;
import com.example.datapermission.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AlertServiceTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private SysAnomalyAlertMapper alertMapper;

    @Test
    public void testGetStatistics() {
        Map<String, Object> stats = alertService.getStatistics(null, null);
        assertNotNull(stats);
        assertNotNull(stats.get("totalCount"));
        assertNotNull(stats.get("pendingCount"));
        assertNotNull(stats.get("handledCount"));
    }

    @Test
    public void testGetStatisticsWithDateRange() {
        LocalDateTime startDate = LocalDateTime.now().minusMonths(1);
        LocalDateTime endDate = LocalDateTime.now();
        Map<String, Object> stats = alertService.getStatistics(startDate, endDate);
        assertNotNull(stats);
    }

    @Test
    public void testHandleAlert() {
        SysAnomalyAlert alert = new SysAnomalyAlert();
        alert.setUserId(1L);
        alert.setAlertType("TEST");
        alert.setAlertContent("Test alert");
        alert.setAlertLevel(1);
        alert.setHandleStatus(0);
        alertMapper.insert(alert);

        AlertHandleRequest request = new AlertHandleRequest();
        request.setAction(AlertAction.CONFIRMED.name());
        request.setHandleResult("Confirmed as normal");

        alertService.handleAlert(alert.getId(), 1L, request);

        SysAnomalyAlert updated = alertMapper.selectById(alert.getId());
        assertEquals(1, updated.getHandleStatus());
        assertNotNull(updated.getHandleTime());
    }

    @Test
    public void testBlockUser() {
        SysAnomalyAlert alert = new SysAnomalyAlert();
        alert.setUserId(1L);
        alert.setAlertType("TEST");
        alert.setAlertContent("Test alert for block");
        alert.setAlertLevel(3);
        alert.setHandleStatus(0);
        alertMapper.insert(alert);

        alertService.blockUser(alert.getId(), 1L, "Security concern");

        SysAnomalyAlert updated = alertMapper.selectById(alert.getId());
        assertEquals(1, updated.getHandleStatus());
        assertNotNull(updated.getRestrictActions());
    }
}
