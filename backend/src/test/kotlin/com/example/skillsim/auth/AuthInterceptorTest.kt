package com.example.skillsim.auth

import com.example.skillsim.controller.AuthController
import com.example.skillsim.controller.HealthController
import com.example.skillsim.model.User
import com.example.skillsim.repository.UserRepository
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod

/**
 * 컨텍스트를 띄우지 않고 인터셉터만 검증한다.
 *
 * 이게 가능한 것이 spring-boot-starter-security 대신 HandlerInterceptor를 고른 이유 중
 * 하나다. SecurityFilterChain이었다면 이 파일은 존재할 수 없다.
 */
class AuthInterceptorTest {

    private val user = User(1, "a@b.co", "hash", 0, Instant.now())
    private val userRepository = mock(UserRepository::class.java)
    private val jwt = JwtTokenService(
        key = Keys.hmacShaKeyFor("test-secret-that-is-long-enough-for-hs256!!".toByteArray(StandardCharsets.UTF_8)),
        ttl = Duration.ofDays(1),
        issuer = "test",
    )
    private val interceptor = AuthInterceptor(jwt, userRepository)

    /** @CurrentUser를 받는 핸들러 = 인증이 필요한 핸들러. */
    private val protectedHandler = HandlerMethod(
        mock(AuthController::class.java),
        AuthController::class.java.getMethod("me", AuthPrincipal::class.java),
    )

    /** @CurrentUser가 없는 핸들러. */
    private val publicHandler = HandlerMethod(
        HealthController(),
        HealthController::class.java.getMethod("checkHealth"),
    )

    private fun request(method: String = "GET", token: String? = null) =
        MockHttpServletRequest(method, "/api/auth/me").apply {
            token?.let { addHeader("Authorization", it) }
        }

    @Test
    fun `유효한 토큰이면 주체를 요청에 담는다`() {
        `when`(userRepository.findById(1)).thenReturn(user)
        val req = request(token = "Bearer ${jwt.issue(user).token}")

        assertThat(interceptor.preHandle(req, MockHttpServletResponse(), protectedHandler)).isTrue()

        val principal = req.getAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) as AuthPrincipal
        assertThat(principal.userId).isEqualTo(1)
        assertThat(principal.email).isEqualTo("a@b.co")
    }

    @Test
    fun `토큰이 없으면 거부한다`() {
        assertThatThrownBy {
            interceptor.preHandle(request(), MockHttpServletResponse(), protectedHandler)
        }.isInstanceOf(TokenInvalidException::class.java)
    }

    @Test
    fun `헤더 모양이 어긋나면 거부한다`() {
        for (header in listOf("token", "Basic abc", "Bearer", "Bearer   ")) {
            assertThatThrownBy {
                interceptor.preHandle(request(token = header), MockHttpServletResponse(), protectedHandler)
            }.`as`(header).isInstanceOf(TokenInvalidException::class.java)
        }
    }

    @Test
    fun `Bearer 접두사는 대소문자를 가리지 않는다`() {
        `when`(userRepository.findById(1)).thenReturn(user)
        val req = request(token = "bearer ${jwt.issue(user).token}")

        assertThat(interceptor.preHandle(req, MockHttpServletResponse(), protectedHandler)).isTrue()
    }

    @Test
    fun `CORS 프리플라이트는 그냥 통과시킨다`() {
        // 여기서 401을 내면 브라우저에는 원인 불명의 CORS 오류로만 보인다.
        val preflight = MockHttpServletRequest("OPTIONS", "/api/decks").apply {
            addHeader("Origin", "https://example.com")
            addHeader("Access-Control-Request-Method", "POST")
        }

        assertThat(interceptor.preHandle(preflight, MockHttpServletResponse(), protectedHandler)).isTrue()
        assertThat(preflight.getAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE)).isNull()
    }

    @Test
    fun `인증이 필요 없는 핸들러는 토큰 없이 통과한다`() {
        assertThat(interceptor.preHandle(request(), MockHttpServletResponse(), publicHandler)).isTrue()
    }

    @Test
    fun `핸들러 메서드가 아니면 통과한다`() {
        // 정적 리소스 핸들러 등.
        assertThat(interceptor.preHandle(request(), MockHttpServletResponse(), Any())).isTrue()
    }
}
