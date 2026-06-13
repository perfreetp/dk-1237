package com.example.datapermission;

import com.example.datapermission.entity.SysPermissionTemplate;
import com.example.datapermission.entity.SysPermissionTemplateDetail;
import com.example.datapermission.service.SysPermissionTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PermissionTemplateServiceTest {

    @Autowired
    private SysPermissionTemplateService templateService;

    @Test
    public void testCreateTemplate() {
        SysPermissionTemplate template = new SysPermissionTemplate();
        template.setTemplateCode("TEST_TEMPLATE");
        template.setTemplateName("测试模板");
        template.setDescription("测试用的权限模板");

        List<SysPermissionTemplateDetail> details = new ArrayList<>();
        SysPermissionTemplateDetail detail = new SysPermissionTemplateDetail();
        detail.setResourceId(1L);
        detail.setOperationType("READ");
        detail.setFieldLevelMap("{\"name\":1,\"phone\":3}");
        details.add(detail);
        template.setDetails(details);

        SysPermissionTemplate result = templateService.create(template);
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("TEST_TEMPLATE", result.getTemplateCode());
    }

    @Test
    public void testGetByCode() {
        SysPermissionTemplate template = templateService.getByCode("MANAGER_READ");
        assertNotNull(template);
        assertEquals("MANAGER_READ", template.getTemplateCode());
    }

    @Test
    public void testGetAll() {
        List<SysPermissionTemplate> templates = templateService.getAll();
        assertNotNull(templates);
        assertTrue(templates.size() > 0);
    }

    @Test
    public void testUpdateTemplate() {
        SysPermissionTemplate template = templateService.getByCode("MANAGER_READ");
        template.setDescription("更新后的描述");
        SysPermissionTemplate result = templateService.update(template.getId(), template);
        assertEquals("更新后的描述", result.getDescription());
    }
}
