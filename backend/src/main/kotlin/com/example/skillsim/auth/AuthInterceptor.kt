package com.example.skillsim.auth

import com.example.skillsim.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.cors.CorsUtils
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Bearer 토큰을 검증해 주체를 요청 속성에 담는다.
 *
 * 서블릿 필터가 아니라 인터셉터를 쓴다. 인터셉터는 DispatcherServlet 안에서 돌기 때문에
 * 여기서 던진 예외가 다른 오류와 같은 경로로 JSON이 된다. 필터에서 던지면 컨테이너
 * 오류 페이지가 나온다. 또한 핸들러가 정해진 뒤라 URL 패턴을 맞추는 대신 핸들러의
 * 인자를 보고 인증 필요 여부를 정할 수 있다.
 */
class AuthInterceptor(
    private val jwtTokenService: JwtTokenService,
    private val userRepository: UserRepository,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        // 프리플라이트는 PreFlightHandler로 매핑되지만 인터셉터 체인은 그대로 유지된다.
        // 여기서 걸러 내지 않으면 OPTIONS가 401이 되고, 브라우저에는 원인을 알 수 없는
        // CORS 오류로만 보여 진단이 매우 어려워진다.
        if (CorsUtils.isPreFlightRequest(request) || handler !is HandlerMethod) {
            return true
        }
        if (!handler.requiresAuthentication()) {
            return true
        }

        val token = request.bearerToken()
            ?: throw TokenInvalidException("Authentication is required.")
        val principal = jwtTokenService.verify(token) { userId ->
            userRepository.findById(userId)?.tokenVersion
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
        return true
    }

    /** @CurrentUser 인자를 받는 핸들러가 곧 인증이 필요한 핸들러다. */
    private fun HandlerMethod.requiresAuthentication(): Boolean =
        methodParameters.any { it.hasParameterAnnotation(CurrentUser::class.java) }

    private fun HttpServletRequest.bearerToken(): String? {
        val header = getHeader("Authorization")?.trim().orEmpty()
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            return null
        }
        return header.substring(BEARER_PREFIX.length).trim().ifEmpty { null }
    }

    companion object {
        const val PRINCIPAL_ATTRIBUTE = "com.example.skillsim.auth.principal"
        private const val BEARER_PREFIX = "Bearer "
    }
}
