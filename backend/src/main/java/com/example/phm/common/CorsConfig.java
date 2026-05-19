package com.example.phm.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 허용 도메인.
 * <p>
 * UECADA 프런트(localhost:5173) 외에, SMWP / KingPortal WebSCADA(222.108.180.36) 의
 * 페이지 스크립트(열기시 / 실행시)에서 fetch 로 /api/** 를 호출할 수 있도록 SMWP
 * 호스트도 허용 목록에 포함합니다. 새 환경을 추가할 때는 패턴만 늘리면 됩니다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://127.0.0.1:*",
            // SMWP / KingPortal WebSCADA — 열기시/실행시 스크립트에서 fetch 로 API 호출.
            "http://222.108.180.36:*",
            "https://222.108.180.36:*"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
        registry.addMapping("/health")
                .allowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
