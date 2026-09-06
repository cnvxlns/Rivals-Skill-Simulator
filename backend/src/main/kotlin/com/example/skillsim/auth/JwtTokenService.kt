package com.example.skillsim.auth

import com.example.skillsim.model.User
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.JwtException
import java.time.Clock
import java.time.Duration
import java.util.Date
import javax.crypto.SecretKey

/**
 * HS256 서명 액세스 토큰.
 *
 * 리프레시 토큰을 두지 않는다. 서버가 토큰을 보관하지 않는 대신 개별 폐기를 못 하는데,
 * [User.tokenVersion]을 올리면 그 사용자에게 나간 토큰이 한 번에 무효가 된다.
 *
 * @param clock 만료를 재우지 않고 테스트하기 위해 주입받는다.
 */
class JwtTokenService(
    private val key: SecretKey,
    private val ttl: Duration,
    private val issuer: String,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun issue(user: User): IssuedToken {
        val now = clock.instant()
        val expiresAt = now.plus(ttl)
        val token = Jwts.builder()
            .subject(user.id.toString())
            .issuer(issuer)
            .claim(EMAIL_CLAIM, user.email)
            .claim(TOKEN_VERSION_CLAIM, user.tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return IssuedToken(token = token, expiresAt = expiresAt.toString())
    }

    /**
     * 토큰을 검증하고 주체를 꺼낸다.
     *
     * @param tokenVersionOf 사용자의 현재 토큰 버전을 돌려주는 함수. 클레임의 값과 다르면
     *   일괄 무효화된 토큰이므로 거부한다. 이 조회 때문에 DB를 한 번 읽는다.
     */
    fun verify(token: String, tokenVersionOf: (Long) -> Int?): AuthPrincipal {
        val claims = try {
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock { Date.from(clock.instant()) }
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (ex: JwtException) {
            throw TokenInvalidException("Invalid or expired token.")
        } catch (ex: IllegalArgumentException) {
            throw TokenInvalidException("Invalid or expired token.")
        }

        val userId = claims.subject?.toLongOrNull()
            ?: throw TokenInvalidException("Invalid or expired token.")
        val tokenVersion = (claims[TOKEN_VERSION_CLAIM] as? Number)?.toInt()
            ?: throw TokenInvalidException("Invalid or expired token.")
        val current = tokenVersionOf(userId)
            ?: throw TokenInvalidException("Invalid or expired token.")
        if (current != tokenVersion) {
            throw TokenInvalidException("Invalid or expired token.")
        }

        return AuthPrincipal(
            userId = userId,
            email = claims[EMAIL_CLAIM] as? String ?: "",
        )
    }

    data class IssuedToken(val token: String, val expiresAt: String)

    private companion object {
        const val EMAIL_CLAIM = "email"
        const val TOKEN_VERSION_CLAIM = "tv"
    }
}
