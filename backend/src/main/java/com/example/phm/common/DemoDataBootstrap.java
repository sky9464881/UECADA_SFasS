package com.example.phm.common;

import java.util.List;

import com.example.phm.auth.entity.User;
import com.example.phm.auth.repository.UserRepository;
import com.example.phm.auth.service.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataBootstrap implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "secret";
    private static final String DEFAULT_SECURITY_QUESTION = "초기 보안 답변은?";
    private static final String DEFAULT_SECURITY_ANSWER = "secret";

    private final UserRepository userRepository;

    public DemoDataBootstrap(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DefaultUser> users = List.of(
                new DefaultUser("U001", null, "admin", "관리자", "admin@uecada.com", "ADMIN"),
                new DefaultUser("U101", "LINE-01", "line01_manager", "1라인 관리자", "line01.manager@uecada.com", "MANAGER"),
                new DefaultUser("U102", "LINE-01", "line01_operator", "1라인 작업자", "line01.operator@uecada.com", "OPERATOR"),
                new DefaultUser("U201", "LINE-02", "line02_manager", "2라인 관리자", "line02.manager@uecada.com", "MANAGER"),
                new DefaultUser("U202", "LINE-02", "line02_operator", "2라인 작업자", "line02.operator@uecada.com", "OPERATOR"),
                new DefaultUser("U301", "LINE-03", "line03_manager", "3라인 관리자", "line03.manager@uecada.com", "MANAGER"),
                new DefaultUser("U302", "LINE-03", "line03_operator", "3라인 작업자", "line03.operator@uecada.com", "OPERATOR")
        );
        users.forEach(this::upsert);
    }

    private void upsert(DefaultUser defaults) {
        User user = userRepository.findById(defaults.userId()).orElseGet(User::new);
        boolean isNew = user.getUserId() == null;
        user.setUserId(defaults.userId());
        user.setLineId(defaults.lineId());
        user.setLoginId(defaults.loginId());
        user.setUserName(defaults.userName());
        user.setEmail(defaults.email());
        user.setRoleName(defaults.roleName());
        user.setSecurityQuestion(DEFAULT_SECURITY_QUESTION);
        if (isNew || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(AuthService.encodePassword(DEFAULT_PASSWORD));
        }
        if (isNew || user.getSecurityAnswerHash() == null || user.getSecurityAnswerHash().isBlank()) {
            user.setSecurityAnswerHash(AuthService.encodeSecurityAnswer(DEFAULT_SECURITY_ANSWER));
        }
        userRepository.save(user);
    }

    private record DefaultUser(
            String userId,
            String lineId,
            String loginId,
            String userName,
            String email,
            String roleName
    ) {
    }
}
