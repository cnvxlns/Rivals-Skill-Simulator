package com.example.skillsim.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 허용 출처.
 *
 * <p>배포처가 바뀔 때 코드를 고치지 않도록 프로퍼티로 주입한다.
 * 개인 서버(Cloudflare Tunnel) 도메인은 APP_CORS_ALLOWED_ORIGINS 환경변수로 추가한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
