package com.example.skillsim.repository

import com.example.skillsim.model.Deck
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.DeckSummary

/**
 * 덱 저장소.
 *
 * 소유권은 조회 후 검사하지 않고 모든 문장의 where에 사용자 번호를 넣는다.
 * 0행이면 없는 것으로 취급되어, 남의 덱에 접근하는 실수가 규율이 아니라 구조적으로 막힌다.
 */
interface DeckRepository {

    fun findSummaries(userId: Long): List<DeckSummary>

    fun findByIdAndUser(id: Long, userId: Long): Deck?

    fun countByUser(userId: Long): Int

    /**
     * @throws org.springframework.dao.DuplicateKeyException 같은 사용자가 같은 이름을 이미 쓰고 있으면.
     */
    fun create(userId: Long, name: String, roster: DeckRoster, totalScore: Double): Deck

    /** @return 없거나 남의 덱이면 null. */
    fun update(id: Long, userId: Long, name: String, roster: DeckRoster, totalScore: Double): Deck?

    /** @return 실제로 지웠으면 true. */
    fun delete(id: Long, userId: Long): Boolean
}
