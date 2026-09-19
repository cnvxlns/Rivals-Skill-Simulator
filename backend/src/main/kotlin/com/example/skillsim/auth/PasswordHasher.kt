package com.example.skillsim.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * 비밀번호 해싱.
 *
 * 인터페이스로 감싼 이유는 테스트가 낮은 강도를 주입해 빠르게 돌 수 있게 하기 위해서다.
 * BCrypt는 일부러 느리므로 기본 강도로 수십 개를 돌리면 테스트가 눈에 띄게 느려진다.
 */
interface PasswordHasher {

    /**
     * 계정이 없을 때도 대조에 쓸 더미 해시.
     *
     * 이메일이 없다고 바로 반환하면 응답 시간 차이로 가입 여부가 새어 나간다.
     * 없는 계정에도 이 값으로 [matches]를 돌려 같은 시간을 쓴다.
     */
    val dummyHash: String

    fun hash(rawPassword: String): String

    fun matches(rawPassword: String, hash: String): Boolean
}

/**
 * @param strength 기본 10. 12는 약 4배 느린데, 터널 뒤 개인 서버에서는 로그인 지연과
 *   CPU 고갈 표적이 되는 쪽이 더 현실적인 위험이다.
 */
class BCryptPasswordHasher(strength: Int = 10) : PasswordHasher {

    private val encoder = BCryptPasswordEncoder(strength)

    override val dummyHash: String = encoder.encode("not-a-real-password")

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    override fun matches(rawPassword: String, hash: String): Boolean =
        encoder.matches(rawPassword, hash)
}
