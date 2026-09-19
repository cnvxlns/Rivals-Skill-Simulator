package com.example.skillsim.service

import com.example.skillsim.auth.AuthPrincipal
import com.example.skillsim.auth.BCryptPasswordHasher
import com.example.skillsim.auth.JwtTokenService
import com.example.skillsim.dto.SignInRequest
import com.example.skillsim.dto.SignUpRequest
import com.example.skillsim.model.User
import com.example.skillsim.repository.UserRepository
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AuthServiceTest {

    /**
     * 손으로 만든 가짜 저장소.
     *
     * Mockito 대신 쓰는 이유는 "무엇이 저장됐는지"를 그대로 들여다볼 수 있어서다.
     * 코틀린의 널 불가 파라미터에 ArgumentCaptor를 쓰면 capture()가 null을 돌려줘
     * NPE가 나기도 한다.
     */
    private class FakeUserRepository : UserRepository {
        val saved = mutableListOf<User>()
        var failWithDuplicate = false

        override fun findByEmail(email: String): User? = saved.firstOrNull { it.email == email }

        override fun findById(id: Long): User? = saved.firstOrNull { it.id == id }

        override fun create(email: String, passwordHash: String): User {
            if (failWithDuplicate) throw DuplicateKeyException("duplicate")
            val user = User(saved.size + 1L, email, passwordHash, 0, Instant.now())
            saved += user
            return user
        }
    }

    private val userRepository = FakeUserRepository()

    // 테스트에서는 강도를 낮춘다. 기본 강도로는 눈에 띄게 느려진다.
    private val hasher = BCryptPasswordHasher(strength = 4)
    private val jwt = JwtTokenService(
        key = Keys.hmacShaKeyFor("test-secret-that-is-long-enough-for-hs256!!".toByteArray(StandardCharsets.UTF_8)),
        ttl = Duration.ofDays(1),
        issuer = "test",
    )
    private val service = AuthService(userRepository, hasher, jwt)

    @Test
    fun `가입하면 토큰을 돌려준다`() {
        val response = service.signUp(SignUpRequest("a@b.co", "password123"))

        assertThat(response.token).isNotBlank()
        assertThat(response.user.email).isEqualTo("a@b.co")
        assertThat(response.expiresAt).isNotBlank()
    }

    @Test
    fun `이메일은 공백을 떼고 소문자로 저장한다`() {
        // DB의 users_email_lowercase 제약과 짝을 이룬다. 대소문자만 다른 중복 계정을 막는다.
        service.signUp(SignUpRequest("  A@B.CO  ", "password123"))

        assertThat(userRepository.saved.single().email).isEqualTo("a@b.co")
    }

    @Test
    fun `비밀번호는 평문으로 저장하지 않는다`() {
        service.signUp(SignUpRequest("a@b.co", "password123"))

        val stored = userRepository.saved.single().passwordHash
        assertThat(stored).doesNotContain("password123").startsWith("$2")
        assertThat(hasher.matches("password123", stored)).isTrue()
    }

    @Test
    fun `이미 있는 이메일은 409로 거부한다`() {
        userRepository.failWithDuplicate = true

        assertThat(catching { service.signUp(SignUpRequest("a@b.co", "password123")) }.statusCode)
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `올바른 비밀번호로 로그인한다`() {
        service.signUp(SignUpRequest("a@b.co", "password123"))

        assertThat(service.signIn(SignInRequest("a@b.co", "password123")).token).isNotBlank()
    }

    @Test
    fun `대소문자가 달라도 같은 계정으로 로그인된다`() {
        service.signUp(SignUpRequest("a@b.co", "password123"))

        assertThat(service.signIn(SignInRequest("A@B.CO", "password123")).user.id).isEqualTo(1)
    }

    @Test
    fun `없는 계정과 틀린 비밀번호의 응답이 같다`() {
        // 메시지가 다르면 어느 이메일이 가입돼 있는지 알아낼 수 있다.
        service.signUp(SignUpRequest("known@b.co", "password123"))

        val wrongPassword = catching { service.signIn(SignInRequest("known@b.co", "wrong-password")) }
        val noAccount = catching { service.signIn(SignInRequest("unknown@b.co", "password123")) }

        assertThat(wrongPassword.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(noAccount.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(noAccount.reason).isEqualTo(wrongPassword.reason)
    }

    @Test
    fun `사라진 계정의 주체는 401로 거부한다`() {
        assertThat(catching { service.me(AuthPrincipal(99, "gone@b.co")) }.statusCode)
            .isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `me는 토큰의 값이 아니라 저장된 계정을 돌려준다`() {
        service.signUp(SignUpRequest("a@b.co", "password123"))

        // 토큰에 엉뚱한 이메일이 들어 있어도 DB의 값이 이긴다.
        assertThat(service.me(AuthPrincipal(1, "stale@b.co")).email).isEqualTo("a@b.co")
    }

    private fun catching(block: () -> Unit): ResponseStatusException =
        try {
            block()
            error("expected ResponseStatusException")
        } catch (ex: ResponseStatusException) {
            ex
        }
}
