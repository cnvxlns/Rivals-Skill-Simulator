package com.example.skillsim.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS 허용 출처.
 *
 * 배포처가 바뀔 때 코드를 고치지 않도록 프로퍼티로 주입한다.
 * 개인 서버(Cloudflare Tunnel) 도메인은 APP_CORS_ALLOWED_ORIGINS 환경변수로 추가한다.
 */
@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins}")
    private val allowedOriginPatterns: List<String>,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(*allowedOriginPatterns.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
    }
}
