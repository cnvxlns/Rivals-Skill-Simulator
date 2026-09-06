package com.example.skillsim.service

import com.example.skillsim.dto.DeckPlayerRequest
import com.example.skillsim.dto.DeckSaveRequest
import com.example.skillsim.dto.DeckSkillRequest
import com.example.skillsim.enums.RelieverRole
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 덱 테스트가 공유하는 26명짜리 정상 로스터.
 *
 * 스킬은 전부 NORMAL·전 포지션 허용이라 자리마다 같은 것을 써도 규칙에 걸리지 않는다.
 * 개별 테스트는 [deckRequest]가 만든 정상 덱에서 한 군데만 망가뜨려 그 규칙을 검증한다.
 */
internal object DeckFixtures {

    const val STAT = "파워"

    /** 모든 포지션이 고를 수 있는 NORMAL 스킬. 값 사다리가 3단계라 레벨 1~3이 유효하다. */
    val skills = (1..4).map { index ->
        ScoreSkill(
            skillKey = "G_00$index",
            // ScoreSkill.cardType은 스킬 풀 축이다. 카드 등급이 아니다.
            cardType = "NORMAL",
            position = "BATTER, PITCHER",
            name = "스킬$index",
            description = "설명$index",
            effects = mutableListOf(
                ScoreEffect(stat = STAT, condition = "ALWAYS", values = "1/2/3"),
            ),
        )
    }

    fun repository(): ScoreSkillRepository {
        val repository = mock(ScoreSkillRepository::class.java)
        skills.forEach { `when`(repository.findBySkillKey(it.skillKey)).thenReturn(it) }
        `when`(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(skills)
        return repository
    }

    fun validator(repository: ScoreSkillRepository = repository()) = DeckValidator(
        scoreSkillRepository = repository,
        skillPoolsFor = { _, _ -> listOf("NORMAL") },
        allowedStatNames = { setOf(STAT) },
    )

    private fun threeSkills() = (1..3).map { DeckSkillRequest(skillId = "G_00$it", level = 1) }

    /**
     * 정상 덱 요청. [mutate]로 특정 자리의 값을 바꿔 실패 케이스를 만든다.
     *
     * @param starterCount 선발 인원. 중계 정원은 12에서 선발·마무리를 뺀 값으로 자동 계산된다.
     */
    fun deckRequest(
        starterCount: Int = 5,
        closerCount: Int = 2,
        mutate: (DeckPlayerRequest) -> DeckPlayerRequest = { it },
    ): DeckSaveRequest {
        val lineup = DeckRules.LINEUP_SLOTS.mapIndexed { index, slot ->
            DeckPlayerRequest(
                slot = slot,
                cardGrade = "SIGNATURE",
                skills = threeSkills(),
                battingOrder = index + 1,
            )
        }
        val bench = DeckRules.BENCH_SLOTS.map { slot ->
            DeckPlayerRequest(
                slot = slot,
                cardGrade = "SIGNATURE",
                position = "C",
                skills = threeSkills(),
            )
        }
        val pitchers = DeckRules.pitcherSlots(starterCount, closerCount).map { slot ->
            DeckPlayerRequest(
                slot = slot,
                cardGrade = "SIGNATURE",
                skills = threeSkills(),
                relieverRole = if (DeckRules.isReliever(slot)) RelieverRole.LONG else null,
            )
        }
        return DeckSaveRequest(
            name = "테스트 덱",
            starterCount = starterCount,
            closerCount = closerCount,
            players = (lineup + bench + pitchers).map(mutate),
        )
    }
}
