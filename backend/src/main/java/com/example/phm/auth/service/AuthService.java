package com.example.phm.auth.service;

import com.example.phm.auth.dto.LoginRequest;
import com.example.phm.auth.dto.LoginResponse;
import com.example.phm.auth.entity.User;
import com.example.phm.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        userRepository.updateLastLoginAt(user.getUserId());
        return LoginResponse.from(user);
    }

    public static String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
