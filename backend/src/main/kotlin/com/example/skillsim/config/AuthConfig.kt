package com.example.skillsim.config

import com.example.skillsim.auth.AuthInterceptor
import com.example.skillsim.auth.BCryptPasswordHasher
import com.example.skillsim.auth.JwtTokenService
import com.example.skillsim.auth.PasswordHasher
import com.example.skillsim.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.crypto.SecretKey
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 인증 관련 빈. @Value를 여기에 모아 두어 다른 클래스가 설정을 모르게 한다.
 */
@Configuration
class AuthConfig(
    @Value("\${app.jwt.secret}") private val secret: String,
    @Value("\${app.jwt.ttl}") private val ttl: Duration,
    @Value("\${app.jwt.issuer}") private val issuer: String,
) {

    private val log = LoggerFactory.getLogger(AuthConfig::class.java)

    @Bean
    fun jwtTokenService(): JwtTokenService =
        JwtTokenService(key = resolveKey(), ttl = ttl, issuer = issuer)

    @Bean
    fun passwordHasher(): PasswordHasher = BCryptPasswordHasher()

    @Bean
    fun authInterceptor(
        jwtTokenService: JwtTokenService,
        userRepository: UserRepository,
    ): AuthInterceptor = AuthInterceptor(jwtTokenService, userRepository)

    /**
     * 서명 키를 만든다.
     *
     * 기본값을 프로퍼티에 박아 두지 않는 이유는 모든 배포가 같은 키를 쓰게 되기 때문이다.
     * 그렇다고 빈 값에서 기동을 실패시키면 `make backend`가 바로 막힌다. 그래서 비어 있으면
     * 임의 키를 만들고 경고를 남긴다 — 개발은 그대로 되고, 재시작하면 로그인이 풀리는 것이
     * 운영에서 설정을 빠뜨렸다는 신호가 된다.
     */
    private fun resolveKey(): SecretKey {
        val trimmed = secret.trim()
        if (trimmed.isEmpty()) {
            log.warn(
                "app.jwt.secret is not set. Generating a random key; issued tokens will not " +
                    "survive a restart. Set APP_JWT_SECRET in production.",
            )
            return Jwts.SIG.HS256.key().build()
        }
        val bytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        // HS256의 최소 키 길이다. 짧은 키는 첫 로그인이 아니라 기동 때 걸러 낸다.
        require(bytes.size >= MIN_SECRET_BYTES) {
            "app.jwt.secret must be at least $MIN_SECRET_BYTES bytes, but was ${bytes.size}."
        }
        return Keys.hmacShaKeyFor(bytes)
    }

    private companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
