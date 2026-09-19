package com.example.skillsim.auth

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 인증된 주체를 컨트롤러 인자로 받는다.
 *
 * 이 애노테이션이 붙어 있다는 것 자체가 "인증이 필요한 핸들러"라는 표시다.
 * 별도의 @RequiresAuth를 두면 인자 목록과 어긋날 수 있어 하나로 합쳤다
 * ([AuthInterceptor] 참고).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUser

/** 요청 속성에 담아 둔 주체를 꺼내 컨트롤러에 넘긴다. */
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            AuthPrincipal::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthPrincipal {
        val principal = webRequest.getAttribute(
            AuthInterceptor.PRINCIPAL_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST,
        ) as? AuthPrincipal
        // null을 돌려주지 않고 던진다. 인터셉터 등록이 잘못돼도 인증되지 않은 요청이
        // 컨트롤러까지 흘러들지 않는다.
        return principal ?: throw TokenInvalidException("Authentication is required.")
    }
}
