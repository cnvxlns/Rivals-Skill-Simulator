package com.example.skillsim.config

import com.example.skillsim.auth.AuthInterceptor
import com.example.skillsim.auth.CurrentUserArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 인증 인터셉터와 @CurrentUser 리졸버를 등록한다.
 *
 * 인터셉터는 api 경로 전체에 걸지만 실제로 막는 것은 @CurrentUser 인자를 받는 핸들러뿐이다
 * ([AuthInterceptor] 참고). 경로 패턴 목록을 따로 관리하면 새 엔드포인트를 추가할 때
 * 빠뜨리기 쉬워 핸들러 시그니처를 기준으로 삼았다.
 */
@Configuration
class WebConfig(
    private val authInterceptor: AuthInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CurrentUserArgumentResolver())
    }
}
