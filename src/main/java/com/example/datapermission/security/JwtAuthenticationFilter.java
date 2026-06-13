package com.example.datapermission.security;

import com.example.datapermission.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Value("${data-permission.jwt.header:Authorization}")
    private String header;

    @Value("${data-permission.jwt.prefix:Bearer}")
    private String prefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(header);

        if (authHeader != null && authHeader.startsWith(prefix + " ")) {
            String token = authHeader.substring(prefix.length() + 1);
            try {
                if (!jwtUtil.isTokenExpired(token)) {
                    Long userId = jwtUtil.extractUserId(token);
                    String username = jwtUtil.extractUsername(token);
                    request.setAttribute("userId", userId);
                    request.setAttribute("username", username);
                }
            } catch (Exception e) {
                log.error("JWT验证失败: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
