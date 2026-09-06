package com.example.skillsim.model

import java.time.Instant

/**
 * 계정 하나.
 *
 * @param tokenVersion 토큰 일괄 무효화 스위치. 발급한 토큰의 클레임에 실리고 검증 때 대조한다.
 *   이 값을 올리면 그 사용자에게 이미 나간 토큰이 전부 죽는다. 서버가 토큰을 보관하지 않는
 *   대신 개별 폐기를 못 하기 때문에 두는 장치다.
 */
data class User(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val tokenVersion: Int,
    val createdAt: Instant,
)
