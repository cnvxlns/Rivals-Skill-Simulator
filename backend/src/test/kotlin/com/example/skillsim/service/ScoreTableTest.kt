package com.example.skillsim.service

import com.example.skillsim.dto.ScoreTableRequest
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.web.server.ResponseStatusException

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

    @Test
    fun `조건을 못박으면 발동 여부에 따라 순위가 갈리고 기댓값을 함께 싣는다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                // 주자있음 0.4 x 20 = 8 (기댓값), 발동 시 20, 미발동 시 0
                skill("G_001", "득점권", "20/20/20/20/20/20/20/20/20", condition = "주자있음"),
                // 주자없음 0.6 x 10 = 6 (기댓값), 주자있음 발동 시 0, 미발동 시 10
                skill("G_002", "솔로", "10/10/10/10/10/10/10/10/10", condition = "주자없음"),
                skill("G_003", "상시", "7/7/7/7/7/7/7/7/7"),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val expected = service.buildScoreTable(request(), 0).tiers[0]
        assertThat(expected.entries).extracting("name").containsExactly("득점권", "상시", "솔로")
        assertThat(expected.entries).allSatisfy { assertThat(it.expectedScore).isNull() }

        val on = service.buildScoreTable(request(condition = "주자있음", active = true), 0).tiers[0]
        assertThat(on.entries).extracting("name").containsExactly("득점권", "상시", "솔로")
        assertThat(on.entries).extracting("score").containsExactly(20.0, 7.0, 0.0)
        assertThat(on.entries).extracting("expectedScore").containsExactly(8.0, 7.0, 6.0)

        val off = service.buildScoreTable(request(condition = "주자있음", active = false), 0).tiers[0]
        assertThat(off.entries).extracting("name").containsExactly("솔로", "상시", "득점권")
        assertThat(off.entries).extracting("score").containsExactly(10.0, 7.0, 0.0)
    }

    @Test
    fun `나눠 볼 조건은 확률이 0과 1 사이인 것만 쓰는 스킬 수 순으로 준다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                skill("G_001", "a", "1/1/1/1/1/1/1/1/1", condition = "주자있음"),
                skill("G_002", "b", "1/1/1/1/1/1/1/1/1", condition = "주자있음+7회이후"),
                // 원정은 랭킹대전에서 늘 참이라 나눠 볼 게 없다.
                skill("G_003", "c", "1/1/1/1/1/1/1/1/1", condition = "원정"),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val conditions = service.buildScoreTable(request(), 0).conditions

        assertThat(conditions).extracting("token").containsExactly("주자있음", "7회이후")
        assertThat(conditions).extracting("skillCount").containsExactly(2, 1)
        assertThat(conditions[0].probability).isEqualTo(0.4)
    }

    @Test
    fun `카드 고유 능력치 조건은 단계마다 한 줄로 나누고 단계는 누적이다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                ScoreSkill(
                    skillKey = "G_001",
                    cardType = "NORMAL",
                    position = "BATTER",
                    name = "엘 그란데",
                    description = "엘 그란데",
                    effects = mutableListOf(
                        ScoreEffect(stat = "파워", condition = "기본주루수비합155이상", values = "10/10/10/10/10/10/10/10/10"),
                        ScoreEffect(stat = "파워", condition = "기본주루수비합165이상", values = "5/5/5/5/5/5/5/5/5"),
                    ),
                ),
                skill("G_002", "상시", "12/12/12/12/12/12/12/12/12"),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val entries = service.buildScoreTable(request(), 0).tiers[0].entries

        assertThat(entries).extracting("name").containsExactly("엘 그란데", "상시", "엘 그란데")
        assertThat(entries).extracting("variant").containsExactly("주+수합 165 이상", null, "주+수합 155 이상 165 미만")
        assertThat(entries).extracting("score").containsExactly(15.0, 12.0, 10.0)
    }

    @Test
    fun `기본 능력치가 정해져 있으면 단계를 나누지 않는다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(skill("G_001", "엘 그란데", "10/10/10/10/10/10/10/10/10", condition = "기본주루수비합155이상")),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val entries = service.buildScoreTable(
            request().copy(baseStats = mapOf("주루" to 80.0, "수비" to 80.0)),
            0,
        ).tiers[0].entries

        assertThat(entries).extracting("variant").containsExactly(null)
        assertThat(entries).extracting("score").containsExactly(10.0)
    }

    @Test
    fun `타순을 고르지 않으면 타순 조건 스킬을 발동하는 타순마다 나눈다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                ScoreSkill(
                    skillKey = "G_001",
                    cardType = "NORMAL",
                    position = "BATTER",
                    name = "클린업",
                    description = "클린업",
                    effects = mutableListOf(
                        ScoreEffect(stat = "파워", condition = "타순3_4_5", values = "4/4/4/4/4/4/4/4/4"),
                        ScoreEffect(stat = "파워", condition = "타순4_5", values = "6/6/6/6/6/6/6/6/6"),
                    ),
                ),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val split = service.buildScoreTable(ScoreTableRequest(position = "BATTER"), 0).tiers[0].entries
        assertThat(split).extracting("variant").containsExactly("타순 4~5", "타순 3")
        assertThat(split).extracting("score").containsExactly(10.0, 4.0)

        // 타순을 고르면 이미 정해진 것이라 나누지 않는다.
        val fixed = service.buildScoreTable(request(), 0).tiers[0].entries
        assertThat(fixed).extracting("variant").containsExactly(null)
        assertThat(fixed).extracting("score").containsExactly(4.0)
    }

    @Test
    fun `선발 순번을 고르지 않으면 순번 조건 스킬을 순번마다 나눈다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                ScoreSkill(
                    skillKey = "G_001",
                    cardType = "NORMAL",
                    position = "PITCHER",
                    name = "에이스",
                    description = "에이스",
                    effects = mutableListOf(
                        ScoreEffect(stat = "구위", condition = "선발1_2", values = "5/5/5/5/5/5/5/5/5"),
                        ScoreEffect(stat = "구위", condition = "선발1", values = "3/3/3/3/3/3/3/3/3"),
                    ),
                ),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("구위" to 1.0))

        val entries = service.buildScoreTable(ScoreTableRequest(position = "SP"), 0).tiers[0].entries

        assertThat(entries).extracting("variant").containsExactly("선발 1", "선발 2")
        assertThat(entries).extracting("score").containsExactly(8.0, 5.0)
    }

    @Test
    fun `상대 능력치 비교는 나누지 않고 발생 확률로 매기며 이름에 적지 않는다`() {
        // G_028 집중력. "상대 투수의 구위가 파워보다 높은 경우"는 상대에 따라 갈리므로 확률(0.35)이다.
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(skill("G_001", "집중력", "10/10/10/10/10/10/10/10/10", condition = "구위>파워")),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val entries = service.buildScoreTable(request(), 0).tiers[0].entries

        assertThat(entries).extracting("variant").containsExactly(null)
        assertThat(entries).extracting("score").containsExactly(3.5)
    }

    @Test
    fun `타순과 스위치 히터 조건은 곱으로 나눈다`() {
        // HOF_045 치퍼. 3번 타순일 때만 쓰므로 타순은 3 한 줄이고, 스위치 히터는 여부 둘 다 남긴다.
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                skillOf(
                    "G_001", "치퍼",
                    ScoreEffect(stat = "파워", condition = "ALWAYS", values = "4/4/4/4/4/4/4/4/4"),
                    ScoreEffect(stat = "파워", condition = "스위치타", values = "2/2/2/2/2/2/2/2/2"),
                    ScoreEffect(stat = "파워", condition = "타순3", values = "1/1/1/1/1/1/1/1/1"),
                ),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val entries = service.buildScoreTable(ScoreTableRequest(position = "BATTER"), 0).tiers[0].entries

        assertThat(entries).extracting("variant").containsExactly("타순 3, 스위치 히터", "타순 3, 스위치 히터 아님")
        assertThat(entries).extracting("score").containsExactly(7.0, 5.0)
    }

    @Test
    fun `내 타석 방향은 좌타 우타 스위치로 나누고 상대 투수 방향은 확률로 둔다`() {
        // HOF_015 적응형 타자. 우투수 상대 좌타석 / 좌투수 상대 우타석. 스위치 히터는 둘 다 받는다.
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                skillOf(
                    "G_001", "적응형 타자",
                    ScoreEffect(stat = "파워", condition = "우투상대+좌타", values = "10/10/10/10/10/10/10/10/10"),
                    ScoreEffect(stat = "파워", condition = "좌투상대+우타", values = "10/10/10/10/10/10/10/10/10"),
                ),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))
        val probabilities = ScoreCalculator.conditionProbabilitiesForPosition("BATTER")

        val entries = service.buildScoreTable(request(), 0).tiers[0].entries
            .associate { it.variant to it.score }

        assertThat(entries.keys).containsExactlyInAnyOrder("좌타", "우타", "스위치 히터")
        assertThat(entries["스위치 히터"]).isEqualTo(10.0)
        assertThat(entries["좌타"]).isCloseTo(10.0 * probabilities.getValue("우투상대"), within(0.01))
        assertThat(entries["우타"]).isCloseTo(10.0 * probabilities.getValue("좌투상대"), within(0.01))
    }

    @Test
    fun `능력치 비례 효과는 내림값이 같은 구간마다 한 줄로 나눈다`() {
        // 정확의 2%. 100~149는 2, 150~199는 3.
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                skillOf(
                    "G_001", "숨은 위협",
                    ScoreEffect(stat = "파워", condition = "ALWAYS", values = "0.02", baseStat = "정확"),
                ),
                skillOf(
                    "G_002", "5툴 플레이어",
                    ScoreEffect(stat = "파워", condition = "ALWAYS", values = "0.01", baseStat = "주루+수비"),
                ),
                skillOf(
                    "G_003", "결속력",
                    ScoreEffect(stat = "파워", condition = "ALWAYS", values = "0.01", baseStat = "스페셜덱"),
                ),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val entries = service.buildScoreTable(request(), 0).tiers[0].entries
            .associate { "${it.name} (${it.variant})" to it.score }

        assertThat(entries).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "숨은 위협 (정확 100~149)" to 2.0,
                "숨은 위협 (정확 150~199)" to 3.0,
                "5툴 플레이어 (주+수합 200~299)" to 2.0,
                "5툴 플레이어 (주+수합 300~399)" to 3.0,
                // 스페셜 덱 스코어는 700을 조금 넘는 경우도 있어 마지막 구간은 열어 둔다.
                "결속력 (스페셜 덱 스코어 500~599)" to 5.0,
                "결속력 (스페셜 덱 스코어 600~699)" to 6.0,
                "결속력 (스페셜 덱 스코어 700 이상)" to 7.0,
            ),
        )
    }

    @Test
    fun `티어별로 고른 등급으로 채점하고 사다리가 짧은 스킬은 자기 최대 등급으로 내려간다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(
            listOf(
                skill("G_001", "긴 사다리", "1/2/3/4/5/6/7/8/9"),
                // 수치가 6단계뿐이라 S1이 끝이다.
                skill("G_002", "짧은 사다리", "10/20/30/40/50/60"),
            ),
        )
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val s3 = service.buildScoreTable(request().copy(grades = mapOf("gold" to "S3")), 0).tiers[0]
        assertThat(s3.grade).isEqualTo("S3")
        assertThat(s3.grades).containsExactly("S", "S1", "S2", "S3", "S4")
        assertThat(s3.entries.associate { it.name to (it.score to it.appliedGrade) }).containsExactlyInAnyOrderEntriesOf(
            mapOf("긴 사다리" to (8.0 to "S3"), "짧은 사다리" to (60.0 to "S1")),
        )

        // 고르지 않았거나 모르는 값이면 S다.
        val fallback = service.buildScoreTable(request().copy(grades = mapOf("gold" to "S9")), 0).tiers[0]
        assertThat(fallback.grade).isEqualTo("S")
        assertThat(fallback.entries.first { it.name == "긴 사다리" }.score).isEqualTo(5.0)
    }

    @Test
    fun `모르는 조건을 못박으려 하면 400이다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(emptyList())
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        assertThatThrownBy { service.buildScoreTable(request(condition = "없는조건", active = true), 0) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Unknown condition")
    }

    private fun request(condition: String? = null, active: Boolean = true) =
        ScoreTableRequest(position = "BATTER", battingOrder = 3, condition = condition, conditionActive = active)

    private fun skillOf(key: String, name: String, vararg effects: ScoreEffect) = ScoreSkill(
        skillKey = key,
        cardType = "NORMAL",
        position = "BATTER",
        name = name,
        description = name,
        effects = effects.toMutableList(),
    )

    private fun skill(key: String, name: String, values: String, condition: String = "ALWAYS") = ScoreSkill(
        skillKey = key,
        cardType = "NORMAL",
        position = "BATTER",
        name = name,
        description = name,
        effects = mutableListOf(
            ScoreEffect(stat = "파워", condition = condition, values = values),
        ),
    )
}
