package com.example.skillsim.service

import com.example.skillsim.dto.ScoreTableRequest
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ScoreTableTest {

    @Test
    fun `티어별로 묶고 점수 내림차순으로 정렬한다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                skill("G_001", "약한골드", "1/1/1/1/1/1/1/1/1"),
                skill("G_002", "강한골드", "9/9/9/9/9/9/9/9/9"),
                skill("I_001", "아이언", "2/2/2/2/2/2/2/2/2"),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val table = service.buildScoreTable(request(), 0)

        // 아이언이 골드보다 앞선다 (DISPLAY_ORDER)
        assertThat(table.tiers).extracting("tier").containsExactly("iron", "gold")

        val gold = table.tiers[1]
        assertThat(gold.totalCount).isEqualTo(2)
        assertThat(gold.entries).extracting("name").containsExactly("강한골드", "약한골드")
        assertThat(gold.entries[0].score).isGreaterThan(gold.entries[1].score)
    }

    @Test
    fun `topN으로 티어별 상위만 남기되 전체 개수는 보존한다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                skill("G_001", "a", "1/1/1/1/1/1/1/1/1"),
                skill("G_002", "b", "2/2/2/2/2/2/2/2/2"),
                skill("G_003", "c", "3/3/3/3/3/3/3/3/3"),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val gold = service.buildScoreTable(request(), 2).tiers[0]

        assertThat(gold.entries).hasSize(2)
        assertThat(gold.totalCount).isEqualTo(3)
    }

    @Test
    fun `S가 없는 스킬은 자기 최대등급으로 채점한다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        // 수치가 3단계뿐이면 S(5번째)에 도달하지 못하므로 B로 내려간다.
        `when`(repository.findAll()).thenReturn(listOf(skill("G_001", "짧은사다리", "1/2/3")))
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val entry = service.buildScoreTable(request(), 0).tiers[0].entries[0]

        assertThat(entry.appliedGrade).isEqualTo("B")
        assertThat(entry.score).isEqualTo(3.0)
    }

    private fun request() = ScoreTableRequest(position = "BATTER", battingOrder = 3)

    private fun skill(key: String, name: String, values: String) = ScoreSkill(
        skillKey = key,
        cardType = "NORMAL",
        position = "BATTER",
        name = name,
        description = name,
        effects = mutableListOf(
            ScoreEffect(stat = "파워", condition = "ALWAYS", values = values),
        ),
    )
}
