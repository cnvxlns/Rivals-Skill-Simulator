package com.example.skillsim.auth

import com.example.skillsim.model.User
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class JwtTokenServiceTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val secret = "test-secret-that-is-long-enough-for-hs256!!"
    private val user = User(
        id = 42,
        email = "a@b.co",
        passwordHash = "hash",
        tokenVersion = 3,
        createdAt = now,
    )

    private fun service(
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
        key: String = secret,
        issuer: String = "test",
    ) = JwtTokenService(
        key = Keys.hmacShaKeyFor(key.toByteArray(StandardCharsets.UTF_8)),
        ttl = Duration.ofDays(14),
        issuer = issuer,
        clock = clock,
    )

    @Test
    fun `발급한 토큰에서 주체를 되찾는다`() {
        val svc = service()
        val issued = svc.issue(user)

        val principal = svc.verify(issued.token) { user.tokenVersion }

        assertThat(principal.userId).isEqualTo(42)
        assertThat(principal.email).isEqualTo("a@b.co")
        assertThat(issued.expiresAt).isEqualTo(now.plus(Duration.ofDays(14)).toString())
    }

    @Test
    fun `서명이 다른 토큰은 거부한다`() {
        val issued = service().issue(user)
        val other = service(key = "a-completely-different-secret-key-value!!!")

        assertThatThrownBy { other.verify(issued.token) { user.tokenVersion } }
            .isInstanceOf(TokenInvalidException::class.java)
    }

    @Test
    fun `변조된 토큰은 거부한다`() {
        val svc = service()
        val issued = svc.issue(user)
        val tampered = issued.token.dropLast(1) + if (issued.token.last() == 'A') 'B' else 'A'

        assertThatThrownBy { svc.verify(tampered) { user.tokenVersion } }
            .isInstanceOf(TokenInvalidException::class.java)
    }

    @Test
    fun `만료된 토큰은 거부한다`() {
        val issued = service().issue(user)
        // 재우지 않고 시계를 옮긴다.
        val later = service(clock = Clock.fixed(now.plus(Duration.ofDays(15)), ZoneOffset.UTC))

        assertThatThrownBy { later.verify(issued.token) { user.tokenVersion } }
            .isInstanceOf(TokenInvalidException::class.java)
    }

    @Test
    fun `발급자가 다르면 거부한다`() {
        val issued = service().issue(user)

        assertThatThrownBy { service(issuer = "other").verify(issued.token) { user.tokenVersion } }
            .isInstanceOf(TokenInvalidException::class.java)
    }

    @Test
    fun `토큰 버전이 올라가면 기존 토큰이 무효가 된다`() {
        // 서버가 토큰을 보관하지 않는 대신 두는 일괄 폐기 장치다.
        val svc = service()
        val issued = svc.issue(user)

        assertThatThrownBy { svc.verify(issued.token) { user.tokenVersion + 1 } }
            .isInstanceOf(TokenInvalidException::class.java)
    }

    @Test
    fun `사라진 계정의 토큰은 거부한다`() {
        val svc = service()
        val issued = svc.issue(user)

        assertThatThrownBy { svc.verify(issued.token) { null } }
            .isInstanceOf(TokenInvalidException::class.java)
    }

    @Test
    fun `토큰 모양이 아니면 거부한다`() {
        val svc = service()
        for (garbage in listOf("", "not-a-token", "a.b.c")) {
            assertThatThrownBy { svc.verify(garbage) { user.tokenVersion } }
                .`as`(garbage)
                .isInstanceOf(TokenInvalidException::class.java)
        }
    }
}
