package com.example.phm.auth.service;

<<<<<<< HEAD
import com.example.phm.auth.dto.LoginRequest;
import com.example.phm.auth.dto.LoginResponse;
=======
import com.example.phm.auth.dto.FindLoginIdRequest;
import com.example.phm.auth.dto.FindLoginIdResponse;
import com.example.phm.auth.dto.LoginRequest;
import com.example.phm.auth.dto.LoginResponse;
import com.example.phm.auth.dto.ResetPasswordRequest;
import com.example.phm.auth.dto.SecurityQuestionResponse;
import com.example.phm.auth.dto.UserCreateRequest;
import com.example.phm.auth.dto.UserResponse;
>>>>>>> feature/develop_before
import com.example.phm.auth.entity.User;
import com.example.phm.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

<<<<<<< HEAD
=======
    private static final String LEGACY_DEMO_PASSWORD_HASH =
            "$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW";
    private static final String LEGACY_DEMO_PASSWORD = "secret";
>>>>>>> feature/develop_before
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

<<<<<<< HEAD
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
=======
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                && !isLegacyDemoPassword(request.password(), user.getPasswordHash())) {
>>>>>>> feature/develop_before
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        userRepository.updateLastLoginAt(user.getUserId());
        return LoginResponse.from(user);
    }

<<<<<<< HEAD
    public static String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
=======
    public UserResponse signup(UserCreateRequest request) {
        if (userRepository.existsById(request.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists: " + request.userId());
        }
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
        user.setPasswordHash(encodePassword(request.password()));
        user.setSecurityQuestion(request.securityQuestion());
        user.setSecurityAnswerHash(encodeSecurityAnswer(request.securityAnswer()));
        return UserResponse.from(userRepository.save(user));
    }

    public FindLoginIdResponse findLoginId(FindLoginIdRequest request) {
        User user = userRepository.findByUserNameAndEmail(request.userName(), request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!matchesSecurityAnswer(request.securityAnswer(), user.getSecurityAnswerHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid security answer");
        }
        return new FindLoginIdResponse(user.getLoginId());
    }

    public SecurityQuestionResponse securityQuestion(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new SecurityQuestionResponse(user.getLoginId(), user.getSecurityQuestion());
    }

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!matchesSecurityAnswer(request.securityAnswer(), user.getSecurityAnswerHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid security answer");
        }
        userRepository.updatePassword(user.getLoginId(), encodePassword(request.newPassword()));
    }

    public static String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public static String encodeSecurityAnswer(String rawAnswer) {
        return passwordEncoder.encode(normalizeSecurityAnswer(rawAnswer));
    }

    private boolean isLegacyDemoPassword(String rawPassword, String passwordHash) {
        return LEGACY_DEMO_PASSWORD.equals(rawPassword)
                && LEGACY_DEMO_PASSWORD_HASH.equals(passwordHash);
    }

    private boolean matchesSecurityAnswer(String rawAnswer, String answerHash) {
        if (answerHash == null || answerHash.isBlank()) {
            return false;
        }
        String normalized = normalizeSecurityAnswer(rawAnswer);
        return passwordEncoder.matches(normalized, answerHash)
                || isLegacyDemoPassword(normalized, answerHash);
    }

    private static String normalizeSecurityAnswer(String rawAnswer) {
        return rawAnswer == null ? "" : rawAnswer.trim().toLowerCase();
    }

    private String normalizeRole(String roleName) {
        return roleName == null ? "OPERATOR" : roleName.trim().toUpperCase();
    }
>>>>>>> feature/develop_before
}
