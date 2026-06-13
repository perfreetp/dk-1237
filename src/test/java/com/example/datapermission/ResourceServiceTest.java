package com.example.datapermission;

import com.example.datapermission.entity.SysResource;
import com.example.datapermission.service.SysResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ResourceServiceTest {

    @Autowired
    private SysResourceService resourceService;

    @Test
    public void testCreateResource() {
        SysResource resource = new SysResource();
        resource.setResourceCode("TEST_RESOURCE");
        resource.setResourceName("测试资源");
        resource.setResourceType("TABLE");
        resource.setSensitivityLevel(3);

        SysResource result = resourceService.create(resource);
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("TEST_RESOURCE", result.getResourceCode());
    }

    @Test
    public void testGetByCode() {
        SysResource resource = resourceService.getByCode("employee_table");
        assertNotNull(resource);
        assertEquals("employee_table", resource.getResourceCode());
    }

    @Test
    public void testGetAll() {
        List<SysResource> resources = resourceService.getAll();
        assertNotNull(resources);
        assertTrue(resources.size() > 0);
    }

    @Test
    public void testGetByType() {
        List<SysResource> resources = resourceService.getByType("TABLE");
        assertNotNull(resources);
    }

    @Test
    public void testUpdateResource() {
        SysResource resource = resourceService.getByCode("employee_table");
        resource.setDescription("更新后的描述");
        SysResource result = resourceService.update(resource.getId(), resource);
        assertEquals("更新后的描述", result.getDescription());
    }
}
