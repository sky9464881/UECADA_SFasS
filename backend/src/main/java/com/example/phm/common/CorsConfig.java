package com.example.phm.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 허용 도메인.
 * <p>
 * UECADA 프런트(localhost:5173) 외에, SMWP / KingPortal WebSCADA 페이지 스크립트
 * (열기시 / 실행시)에서 fetch 로 /api/** 를 호출할 수 있도록 허용합니다.
 * SMWP가 외부 프로그램/WebView로 뜨는 경우 Origin 이 "null" 로 들어올 수 있습니다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.0.*:*",
            "https://192.168.0.*:*",
            "http://222.108.180.36:*",
            "https://222.108.180.36:*",
            "http://192.168.0.100:*",
            "https://192.168.0.100:*",
            "null"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .allowPrivateNetwork(true)
                .maxAge(3600);
        registry.addMapping("/health")
                .allowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowPrivateNetwork(true)
                .maxAge(3600);
    }
}
