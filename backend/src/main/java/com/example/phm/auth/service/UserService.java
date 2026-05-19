package com.example.phm.auth.service;

import java.util.List;

import com.example.phm.auth.dto.UserCreateRequest;
import com.example.phm.auth.dto.UserResponse;
import com.example.phm.auth.dto.UserRoleUpdateRequest;
import com.example.phm.auth.entity.User;
import com.example.phm.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> findAll(String roleName) {
        List<User> users = roleName != null && !roleName.isBlank()
                ? userRepository.findByRoleName(roleName)
                : userRepository.findAll();
        return users.stream().map(UserResponse::from).toList();
    }

    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsById(request.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists: " + request.userId());
        }
<<<<<<< HEAD
        User user = new User();
        user.setUserId(request.userId());
        user.setLoginId(request.loginId());
        user.setUserName(request.userName());
        user.setEmail(request.email());
        user.setRoleName(request.roleName());
        user.setPasswordHash(AuthService.encodePassword(request.password()));
=======
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login ID already exists: " + request.loginId());
        }
        User user = new User();
        user.setUserId(request.userId());
        user.setLoginId(request.loginId());
        user.setLineId(request.lineId());
        user.setUserName(request.userName());
        user.setEmail(request.email());
        user.setRoleName(normalizeRole(request.roleName()));
        user.setPasswordHash(AuthService.encodePassword(request.password()));
        user.setSecurityQuestion(request.securityQuestion());
        user.setSecurityAnswerHash(AuthService.encodeSecurityAnswer(request.securityAnswer()));
>>>>>>> feature/develop_before
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse updateRole(String userId, UserRoleUpdateRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        }
<<<<<<< HEAD
        userRepository.updateRole(userId, request.roleName());
        return UserResponse.from(userRepository.findById(userId).orElseThrow());
    }
=======
        userRepository.updateRole(userId, normalizeRole(request.roleName()));
        return UserResponse.from(userRepository.findById(userId).orElseThrow());
    }

    private String normalizeRole(String roleName) {
        return roleName == null ? "OPERATOR" : roleName.trim().toUpperCase();
    }
>>>>>>> feature/develop_before
}
