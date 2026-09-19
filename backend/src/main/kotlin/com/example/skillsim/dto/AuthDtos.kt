package com.example.skillsim.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignUpRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val email: String? = null,
    // 상한을 두는 이유는 BCrypt가 72바이트까지만 보기 때문이다. 그보다 길면 뒷부분이
    // 조용히 무시돼 사용자가 오해할 수 있다.
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String? = null,
)

data class SignInRequest(
    @field:NotBlank
    val email: String? = null,
    @field:NotBlank
    val password: String? = null,
)

data class AuthResponse(
    val token: String,
    val expiresAt: String,
    val user: UserResponse,
)

data class UserResponse(
    val id: Long,
    val email: String,
)
