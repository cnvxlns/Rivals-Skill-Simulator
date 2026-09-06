package com.example.skillsim.service

import com.example.skillsim.auth.AuthPrincipal
import com.example.skillsim.auth.JwtTokenService
import com.example.skillsim.auth.PasswordHasher
import com.example.skillsim.dto.AuthResponse
import com.example.skillsim.dto.SignInRequest
import com.example.skillsim.dto.SignUpRequest
import com.example.skillsim.dto.UserResponse
import com.example.skillsim.model.User
import com.example.skillsim.repository.UserRepository
import java.util.Locale
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtTokenService: JwtTokenService,
) {

    fun signUp(request: SignUpRequest): AuthResponse {
        val email = normalizeEmail(request.email)
        val password = request.password
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.")

        val user = try {
            userRepository.create(email, passwordHasher.hash(password))
        } catch (ex: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.")
        }
        return user.toAuthResponse()
    }

    fun signIn(request: SignInRequest): AuthResponse {
        val email = normalizeEmail(request.email)
        val password = request.password.orEmpty()

        val user = userRepository.findByEmail(email)
        // 계정이 없어도 대조를 수행한다. 바로 반환하면 응답 시간 차이로 가입 여부가 샌다.
        val matches = passwordHasher.matches(password, user?.passwordHash ?: passwordHasher.dummyHash)
        if (user == null || !matches) {
            // 두 경우의 메시지를 같게 둔다. 어느 쪽이 틀렸는지 알려주지 않는다.
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.")
        }
        return user.toAuthResponse()
    }

    fun me(principal: AuthPrincipal): UserResponse {
        val user = userRepository.findById(principal.userId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists.")
        return UserResponse(id = user.id, email = user.email)
    }

    private fun normalizeEmail(email: String?): String {
        val normalized = email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.")
        }
        return normalized
    }

    private fun User.toAuthResponse(): AuthResponse {
        val issued = jwtTokenService.issue(this)
        return AuthResponse(
            token = issued.token,
            expiresAt = issued.expiresAt,
            user = UserResponse(id = id, email = email),
        )
    }
}
