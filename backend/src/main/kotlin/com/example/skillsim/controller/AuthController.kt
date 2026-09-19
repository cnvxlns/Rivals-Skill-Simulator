package com.example.skillsim.controller

import com.example.skillsim.auth.AuthPrincipal
import com.example.skillsim.auth.CurrentUser
import com.example.skillsim.dto.AuthResponse
import com.example.skillsim.dto.SignInRequest
import com.example.skillsim.dto.SignUpRequest
import com.example.skillsim.dto.UserResponse
import com.example.skillsim.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest): AuthResponse =
        authService.signUp(request)

    @PostMapping("/login")
    fun signIn(@Valid @RequestBody request: SignInRequest): AuthResponse =
        authService.signIn(request)

    /** @CurrentUser가 붙어 있어 인증이 필요한 핸들러가 된다. */
    @GetMapping("/me")
    fun me(@CurrentUser principal: AuthPrincipal): UserResponse = authService.me(principal)
}
