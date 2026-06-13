package com.example.datapermission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.datapermission.entity.SysOrganization;
import com.example.datapermission.mapper.SysOrganizationMapper;
import com.example.datapermission.service.SysOrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OrganizationTest {

    @Autowired
    private SysOrganizationMapper organizationMapper;

    @Autowired
    private SysOrganizationService organizationService;

    @Test
    public void testCreateOrganization() {
        SysOrganization org = new SysOrganization();
        org.setOrgCode("TEST_ORG");
        org.setOrgName("测试组织");
        org.setOrgType("DEPT");
        org.setParentId(1L);
        org.setHierarchyLevel(2);
        org.setStatus(1);

        SysOrganization result = organizationService.create(org);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("TEST_ORG", result.getOrgCode());
        assertEquals("测试组织", result.getOrgName());
    }

    @Test
    public void testGetById() {
        SysOrganization org = organizationService.getById(1L);
        assertNotNull(org);
        assertNotNull(org.getOrgCode());
    }

    @Test
    public void testGetChildren() {
        List<SysOrganization> children = organizationService.getByParentId(1L);
        assertNotNull(children);
    }

    @Test
    public void testGetOrgIdsInHierarchy() {
        List<Long> orgIds = organizationService.getOrgIdsInHierarchy(1L);
        assertNotNull(orgIds);
        assertTrue(orgIds.contains(1L));
    }

    @Test
    public void testGetAllActive() {
        List<SysOrganization> allOrg = organizationService.getAllActive();
        assertNotNull(allOrg);
        assertTrue(allOrg.size() > 0);
    }
}
