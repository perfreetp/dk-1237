package com.example.datapermission;

import com.example.datapermission.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    public void testGenerateToken() {
        String token = jwtUtil.generateToken(1L, "testuser");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    public void testExtractUserId() {
        String token = jwtUtil.generateToken(123L, "testuser");
        Long userId = jwtUtil.extractUserId(token);
        assertEquals(123L, userId);
    }

    @Test
    public void testExtractUsername() {
        String token = jwtUtil.generateToken(1L, "testuser");
        String username = jwtUtil.extractUsername(token);
        assertEquals("testuser", username);
    }

    @Test
    public void testIsTokenExpired() {
        String token = jwtUtil.generateToken(1L, "testuser");
        boolean expired = jwtUtil.isTokenExpired(token);
        assertFalse(expired);
    }

    @Test
    public void testValidateToken() {
        String token = jwtUtil.generateToken(1L, "testuser");
        boolean valid = jwtUtil.validateToken(token, "testuser");
        assertTrue(valid);

        boolean invalid = jwtUtil.validateToken(token, "wronguser");
        assertFalse(invalid);
    }
}
