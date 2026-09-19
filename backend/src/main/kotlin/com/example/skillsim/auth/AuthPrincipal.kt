package com.example.skillsim.auth

/** 인증된 요청의 주체. 토큰에서 꺼낸 값이며 DB를 다시 읽지 않는다. */
data class AuthPrincipal(
    val userId: Long,
    val email: String,
)

/** 토큰이 없거나, 서명이 맞지 않거나, 만료됐거나, 일괄 무효화된 경우. 401로 매핑된다. */
class TokenInvalidException(message: String) : RuntimeException(message)
