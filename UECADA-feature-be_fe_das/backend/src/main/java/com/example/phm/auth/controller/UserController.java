package com.example.phm.auth.controller;

import java.util.List;

import com.example.phm.auth.dto.UserCreateRequest;
import com.example.phm.auth.dto.UserResponse;
import com.example.phm.auth.dto.UserRoleUpdateRequest;
import com.example.phm.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> findAll(@RequestParam(required = false) String roleName) {
        return userService.findAll(roleName);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{userId}/role")
    public UserResponse updateRole(
            @PathVariable String userId,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        return userService.updateRole(userId, request);
    }
}
