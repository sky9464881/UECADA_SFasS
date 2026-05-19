package com.example.phm.auth.controller;

import com.example.phm.auth.dto.FindLoginIdRequest;
import com.example.phm.auth.dto.FindLoginIdResponse;
import com.example.phm.auth.dto.LoginRequest;
import com.example.phm.auth.dto.LoginResponse;
import com.example.phm.auth.dto.ResetPasswordRequest;
import com.example.phm.auth.dto.SecurityQuestionResponse;
import com.example.phm.auth.dto.UserCreateRequest;
import com.example.phm.auth.dto.UserResponse;
import com.example.phm.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/signup")
    public UserResponse signup(@Valid @RequestBody UserCreateRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/find-id")
    public FindLoginIdResponse findLoginId(@Valid @RequestBody FindLoginIdRequest request) {
        return authService.findLoginId(request);
    }

    @GetMapping("/security-question")
    public SecurityQuestionResponse securityQuestion(@RequestParam String loginId) {
        return authService.securityQuestion(loginId);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }
}
