package com.example.skillsim.repository

import com.example.skillsim.model.User

/**
 * 계정 저장소.
 *
 * 손으로 쓴 인터페이스에 구현 하나라는 [ScoreSkillRepository]의 관례를 그대로 따른다.
 * 덕분에 서비스 테스트에서 Mockito로 대체할 수 있다.
 */
interface UserRepository {

    /** 이메일은 항상 소문자로 정규화된 값이 들어온다. */
    fun findByEmail(email: String): User?

    fun findById(id: Long): User?

    /**
     * 새 계정을 만든다.
     *
     * @throws org.springframework.dao.DuplicateKeyException 이미 있는 이메일이면.
     */
    fun create(email: String, passwordHash: String): User
}
