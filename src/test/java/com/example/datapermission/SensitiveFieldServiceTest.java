package com.example.datapermission;

import com.example.datapermission.entity.SysSensitiveField;
import com.example.datapermission.service.SysSensitiveFieldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SensitiveFieldServiceTest {

    @Autowired
    private SysSensitiveFieldService fieldService;

    @Test
    public void testCreateSensitiveField() {
        SysSensitiveField field = new SysSensitiveField();
        field.setResourceId(1L);
        field.setFieldName("test_field");
        field.setFieldLabel("测试字段");
        field.setSensitivityLevel(3);
        field.setDesensitizationType("MASK");
        field.setMaskPattern("前1后1");

        SysSensitiveField result = fieldService.create(field);
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("test_field", result.getFieldName());
    }

    @Test
    public void testGetByResourceId() {
        List<SysSensitiveField> fields = fieldService.getByResourceId(1L);
        assertNotNull(fields);
    }

    @Test
    public void testGetByResourceAndField() {
        SysSensitiveField field = fieldService.getByResourceAndField(1L, "phone");
        assertNotNull(field);
        assertEquals("phone", field.getFieldName());
    }

    @Test
    public void testBatchCreate() {
        List<SysSensitiveField> fields = List.of(
                createField("batch_field1", "批量字段1", 3),
                createField("batch_field2", "批量字段2", 4)
        );
        fieldService.batchCreate(1L, fields);

        List<SysSensitiveField> result = fieldService.getByResourceId(1L);
        assertTrue(result.size() > 0);
    }

    private SysSensitiveField createField(String name, String label, int level) {
        SysSensitiveField field = new SysSensitiveField();
        field.setFieldName(name);
        field.setFieldLabel(label);
        field.setSensitivityLevel(level);
        field.setDesensitizationType("MASK");
        return field;
    }
}
