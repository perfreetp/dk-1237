package com.example.datapermission.controller;

import com.example.datapermission.dto.LoginRequest;
import com.example.datapermission.dto.LoginResponse;
import com.example.datapermission.entity.SysUser;
import com.example.datapermission.service.SysUserService;
import com.example.datapermission.util.JwtUtil;
import com.example.datapermission.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = userService.login(request.getUsername(), request.getPassword());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setExpiresIn(jwtUtil.getExpiration());

        return Result.success(response);
    }

    @GetMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    @GetMapping("/current")
    public Result<SysUser> getCurrentUser(@RequestAttribute("userId") Long userId) {
        SysUser user = userService.getById(userId);
        user.setPassword(null);
        return Result.success(user);
    }
}
