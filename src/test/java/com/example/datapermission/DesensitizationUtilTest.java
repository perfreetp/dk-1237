package com.example.datapermission;

import com.example.datapermission.util.DesensitizationUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DesensitizationUtilTest {

    @Autowired
    private DesensitizationUtil desensitizationUtil;

    @Test
    public void testMaskPhone() {
        String result = desensitizationUtil.desensitize("13800138000", "MASK", "前3后4");
        assertNotNull(result);
        assertTrue(result.startsWith("138"));
        assertTrue(result.endsWith("8000"));
        assertTrue(result.contains("***"));
    }

    @Test
    public void testMaskEmail() {
        String result = desensitizationUtil.desensitize("test@example.com", "MASK", "前2后@");
        assertNotNull(result);
        assertTrue(result.startsWith("te"));
        assertTrue(result.contains("@"));
    }

    @Test
    public void testHashValue() {
        String result = desensitizationUtil.desensitize("test123", "HASH", null);
        assertNotNull(result);
        assertFalse(result.equals("test123"));
    }

    @Test
    public void testHideValue() {
        String result = desensitizationUtil.desensitize("sensitive", "HIDE", null);
        assertNull(result);
    }

    @Test
    public void testNullValue() {
        String result = desensitizationUtil.desensitize(null, "MASK", null);
        assertNull(result);
    }

    @Test
    public void testEmptyValue() {
        String result = desensitizationUtil.desensitize("", "MASK", null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testMaskSalary() {
        String result = desensitizationUtil.desensitize("50000", "MASK", "只显示万");
        assertNotNull(result);
        assertTrue(result.contains("万"));
    }

    @Test
    public void testDefaultMaskPattern() {
        String result = desensitizationUtil.desensitize("12345678", "MASK", "");
        assertNotNull(result);
        assertTrue(result.contains("*"));
    }
}
