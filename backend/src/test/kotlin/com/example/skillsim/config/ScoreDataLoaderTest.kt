package com.example.skillsim.config

import com.example.skillsim.dto.ScoreTableRequest
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import com.example.skillsim.service.ScoreCalculator
import com.example.skillsim.service.ScoreService
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.io.ClassPathResource

class ScoreDataLoaderTest {

    private val loader = ScoreDataLoader(mock(ScoreSkillRepository::class.java))

    @Test
    fun `swift handling uses the four out relief average at all nine levels`() {
        val skill = readBundledScoreSkills().first { it.skillKey == "G_077" }
        assertThat(skill.position).isEqualTo("RP")
        assertThat(skill.cardType).isEqualTo("NORMAL")
        assertThat(skill.effects).hasSize(8)
        val calculator = ScoreCalculator()
        val weights = resourceReader("stat_weights.csv").use(loader::readStatWeights)
        val expectedScores = listOf(10.58, 15.28, 19.98, 24.68, 29.38, 34.08, 38.78, 43.48, 48.18)
        for (level in 1..9) {
            val result = calculator.calculate(
                listOf(ScoreCalculator.Selection(skill, level)), weights,
                ScoreCalculator.conditionProbabilitiesForPosition("RP"),
            )
            assertThat(result.total).describedAs("level %s", level).isEqualTo(expectedScores[level - 1])
            val expectedMean = (6 + (level - 1) * 2) - 1.5
            for (stat in listOf("구위", "변화")) {
                assertThat(result.perSkill.single().breakdown.filter { it.stat == stat }
                    .sumOf { it.value * it.conditionProbability }).isEqualTo(expectedMean)
            }
        }
        for (position in listOf("SP", "CP", "BATTER")) {
            val result = calculator.calculate(
                listOf(ScoreCalculator.Selection(skill, 5)), weights,
                ScoreCalculator.conditionProbabilitiesForPosition(position),
            )
            assertThat(result.total).describedAs("position %s", position).isEqualTo(0.0)
        }

        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(listOf(skill))
        val service = ScoreService(repository, calculator, weights)
        assertThat(service.listSkills("SIGNATURE", null, "RP")).extracting("skillId").contains("G_077")
        for (position in listOf("SP", "CP", "BATTER")) {
            assertThat(service.listSkills("SIGNATURE", null, position)).extracting("skillId").doesNotContain("G_077")
        }
        val entry = service.buildScoreTable(ScoreTableRequest(position = "RP"), 0)
            .tiers.first { it.tier == "gold" }.entries.first { it.skillId == "G_077" }
        assertThat(entry.score).isEqualTo(29.38)
    }

    @Test
    fun `readScoreSkills links effects by skill key`() {
        val skillsCsv = """
            skill_id,card_type,position,name,description
            S_001,NORMAL,BATTER,좌투선호,"좌투수 상대할 때 파워, 정확 능력치가 x 증가합니다"
        """.trimIndent()
        val effectsCsv = """
            skill_id,stat,condition,values
            S_001,파워,좌투상대,1/2/3/4/5/6/7/8/9
            S_001,정확,좌투상대,1/2/3/4/5/6/7/8/9
        """.trimIndent()

        val skills = loader.readScoreSkills(StringReader(skillsCsv), StringReader(effectsCsv))

        assertThat(skills).hasSize(1)
        val skill = skills[0]
        assertThat(skill.skillKey).isEqualTo("S_001")
        assertThat(skill.cardType).isEqualTo("NORMAL")
        assertThat(skill.position).isEqualTo("BATTER")
        assertThat(skill.name).isEqualTo("좌투선호")
        assertThat(skill.effects).hasSize(2)
        assertThat(skill.effects)
            .allSatisfy { assertThat(it.skill).isSameAs(skill) }
            .extracting("stat", "condition", "values")
            .containsExactly(
                tuple("파워", "좌투상대", "1/2/3/4/5/6/7/8/9"),
                tuple("정확", "좌투상대", "1/2/3/4/5/6/7/8/9"),
            )
    }

    @Test
    fun `readStatWeights returns stat to weight map`() {
        val weightsCsv = """
            stat,weight
            파워,1.10
            정확,0.90
            제구,0.00
        """.trimIndent()

        val weights = loader.readStatWeights(StringReader(weightsCsv))

        assertThat(weights).containsEntry("파워", 1.10)
        assertThat(weights).containsEntry("정확", 0.90)
        assertThat(weights).containsEntry("제구", 0.00)
    }

    @Test
    fun `standout pitcher data does not encode batter count as stat increase`() {
        val skills = readBundledScoreSkills()
        if (skills.none { it.skillKey == "M_029" }) {
            return
        }

        for (stat in listOf("구위", "변화", "제구")) {
            assertThat(valuesFor(skills, "M_029", stat, "ALWAYS")).containsExactly("7")
            assertThat(valuesFor(skills, "M_029", stat, "등판후9타자")).containsExactly("5")
        }
    }

    @Test
    fun `standout batter duration bonus uses second plate appearance condition`() {
        val skills = readBundledScoreSkills()
        if (skills.none { it.skillKey == "M_032" }) {
            return
        }

        for (stat in listOf("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "M_032", stat, "ALWAYS")).containsExactly("7")
            assertThat(valuesFor(skills, "M_032", stat, "두번째타석까지")).containsExactly("5")
        }
    }

    @Test
    fun `power pitcher additional batter debuffs use stat comparison condition`() {
        val skills = readBundledScoreSkills()
        if (skills.none { it.skillKey == "M_041" }) {
            return
        }

        assertThat(valuesFor(skills, "M_041", "구위", "ALWAYS")).containsExactly("10")
        assertThat(valuesFor(skills, "M_041", "변화", "ALWAYS")).containsExactly("10")
        for (stat in listOf("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "M_041", stat, "인내<구속")).containsExactly("10")
        }
    }

    @Test
    fun `maestro data uses cumulative out count condition for pitcher buff`() {
        val skills = readBundledScoreSkills()
        if (skills.none { it.skillKey == "M_042" }) {
            return
        }

        for (stat in listOf("파워", "정확", "선구", "인내", "주루", "수비")) {
            assertThat(valuesFor(skills, "M_042", stat, "ALWAYS")).containsExactly("11")
        }
        assertThat(valuesFor(skills, "M_042", "구위", "마에스트로누적")).containsExactly("12")
        assertThat(valuesFor(skills, "M_042", "변화", "마에스트로누적")).containsExactly("12")
        assertThat(alwaysValues(skills, "M_042", "구위")).isEmpty()
        assertThat(alwaysValues(skills, "M_042", "변화")).isEmpty()
    }

    @Test
    fun `moment skills and position exclusives match updated workbook`() {
        val skills = readBundledScoreSkills()

        assertThat(positionFor(skills, "M_007")).isEqualTo("C, 2B, SS, CF")
        assertThat(positionFor(skills, "M_010")).isEqualTo("C, IF, OF")
        assertThat(positionFor(skills, "M_014")).isEqualTo("C")
        assertThat(positionFor(skills, "M_017")).isEqualTo("RP")
        assertThat(positionFor(skills, "M_019")).isEqualTo("SP")
        assertThat(positionFor(skills, "M_027")).isEqualTo("CP")
        assertThat(positionFor(skills, "M_043")).isEqualTo("CP")
        assertThat(positionFor(skills, "M_044")).isEqualTo("BATTER")

        for (stat in listOf("구속", "변화", "구위", "제구")) {
            assertThat(valuesFor(skills, "M_043", stat, "ALWAYS")).containsExactly("9")
        }
        for (stat in listOf("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "M_043", stat, "9회까지")).containsExactly("9")
            assertThat(valuesFor(skills, "M_044", stat, "파워정확합>주루수비합")).containsExactly("12")
            assertThat(valuesFor(skills, "M_044", stat, "포지션_DH")).containsExactly("6")
        }
    }

    @Test
    fun `playoff hero OVR comparison effects are marked as OVR underdog conditions`() {
        val skills = readBundledScoreSkills()
        if (skills.none { it.skillKey == "M_004" }) {
            return
        }

        assertThat(valuesFor(skills, "M_004", "구위", "OVR열세")).containsExactly("4")
        assertThat(valuesFor(skills, "M_004", "변화", "OVR열세")).containsExactly("4")
        assertThat(valuesFor(skills, "M_020", "파워", "OVR열세")).containsExactly("4")
        assertThat(valuesFor(skills, "M_020", "정확", "OVR열세")).containsExactly("4")
        assertThat(valuesFor(skills, "M_004", "구위", "ALWAYS")).isEmpty()
        assertThat(valuesFor(skills, "M_020", "파워", "ALWAYS")).isEmpty()
    }

    @Test
    fun `conditional normal skill bonuses are not marked as always active`() {
        val skills = readBundledScoreSkills()

        assertThat(valuesFor(skills, "G_003", "파워", "높은공")).containsExactly("3/4/5/6/8/10/12/14/15")
        assertThat(valuesFor(skills, "G_003", "파워", "ALWAYS")).isEmpty()

        assertThat(valuesFor(skills, "G_011", "변화", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9")
        assertThat(valuesFor(skills, "G_011", "제구", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9")
        for (stat in listOf("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "G_011", stat, "대타첫타석"))
                .containsExactly("5/6/7/8/9/10/11/12/13")
            assertThat(valuesFor(skills, "G_011", stat, "ALWAYS")).isEmpty()
        }

        assertThat(valuesFor(skills, "G_049", "정확", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9")
        assertThat(valuesFor(skills, "G_049", "선구", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9")
        for (stat in listOf("구위", "변화", "제구")) {
            assertThat(valuesFor(skills, "G_049", stat, "교체후첫타자"))
                .containsExactly("5/6/7/8/9/10/11/12/13")
            assertThat(valuesFor(skills, "G_049", stat, "ALWAYS")).isEmpty()
        }

        for (stat in listOf("구위", "변화", "제구", "구속")) {
            assertThat(valuesFor(skills, "G_043", stat, "등판후3타자"))
                .containsExactly("2/3/4/5/6/7/8/9/10")
            assertThat(valuesFor(skills, "G_043", stat, "ALWAYS")).isEmpty()
        }

        for (stat in listOf("정확", "선구", "인내")) {
            assertThat(valuesFor(skills, "G_048", stat, "포지션_SP+1_2회"))
                .containsExactly("2/3/4/5/6/7/8/9/10")
            assertThat(valuesFor(skills, "G_048", stat, "포지션_SP")).isEmpty()
        }

        for (stat in listOf("파워", "인내")) {
            assertThat(valuesFor(skills, "G_052", stat, "선발3_4_5+중계3_4_5+등판후4타자"))
                .containsExactly("1/2/3/4/5/6/7/8/9")
            assertThat(valuesFor(skills, "G_052", stat, "선발3_4_5+중계3_4_5")).isEmpty()
        }

        for (stat in listOf("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "G_056", stat, "비김또는리드+포지션_RP_CP+등판후3타자"))
                .containsExactly("2/3/4/5/6/7/8/9/10")
            assertThat(valuesFor(skills, "G_056", stat, "비김또는리드+포지션_RP_CP")).isEmpty()
        }
    }

    /**
     * 설명문에 조건이 붙은 절이 조건 없이(ALWAYS) 들어가 항상 발동하던 스킬들.
     * 전 티어를 설명문과 대조해 찾았다. 같은 값의 ALWAYS 행이 되살아나지 않게 못박는다.
     */
    @Test
    fun `conditioned clauses are not scored as always`() {
        val skills = readBundledScoreSkills()
        data class Clause(val skillKey: String, val stats: List<String>, val condition: String, val values: String)

        val clauses = listOf(
            Clause("HOF_007", listOf("파워", "정확", "인내"), "주자3루", "1/2/2/3/3/5"),
            Clause("HOF_010", listOf("파워", "정확", "선구", "인내", "주루", "수비"), "직전타석출루실패", "2/2/3/4/4/5"),
            Clause("HOF_011", listOf("구위", "변화", "제구"), "출루중", "1/1/1/1/1/2"),
            Clause("HOF_013", listOf("파워"), "당겨치기", "3/4/6/8/10/13"),
            Clause("HOF_013", listOf("정확"), "밀어치기", "3/4/6/8/10/13"),
            Clause("HOF_017", listOf("파워"), "직전타자출루", "1/3/4/4/5/6"),
            Clause("HOF_017", listOf("파워", "정확", "선구"), "직전타자홈런", "2/3/3/4/4/5"),
            Clause("HOF_024", listOf("파워", "정확"), "등판후6타자", "6"),
            Clause("HOF_027", listOf("구위", "변화", "구속"), "주자3루", "1/2/2/3/3/5"),
            Clause("HOF_029", listOf("구속", "구위", "변화", "제구", "지구력", "수비"), "직전타자출루", "2/2/3/4/4/5"),
            Clause("HOF_034", listOf("파워", "정확"), "직전타자삼진", "2/3/4/5/7/8"),
            Clause("HOF_037", listOf("정확"), "너클볼선택", "3"),
            Clause("HOF_041", listOf("구위", "변화"), "직전타석홈런실패", "2/3/4/5/6/7"),
            Clause("HOF_042", listOf("파워", "정확"), "직전타석출루실패", "1/2/3/4/5/6"),
            Clause("S_004", listOf("파워", "정확", "선구", "인내", "주루", "수비"), "직전타자출루", "1/2/3/4/5/6/7/8/9"),
            Clause("B_008", listOf("파워", "정확", "선구", "인내"), "등판후1타자", "1/2/3/4/5/6/7/8/9"),
            Clause("B_010", listOf("변화", "구속"), "직전타자삼진", "2/3/4/5/6/7/8/9/10"),
            Clause("I_001", listOf("파워"), "밀어치기", "2/3/4/5/6/7/8/9/10"),
            Clause("I_002", listOf("파워"), "당겨치기", "2/3/4/5/6/7/8/9/10"),
            Clause("G_002", listOf("파워"), "낮은공", "1/2/3/4/5/6/7/8/9"),
            // "주자당"은 주자 수만큼 곱해진다.
            Clause("G_042", listOf("변화", "제구"), "주자당", "1/2/3/4/5/6/7/8/9"),
            Clause("G_076", listOf("구위", "변화"), "직전타자아웃", "1/1/1/1/2/3/4/5/6"),
            Clause("WBC_016", listOf("구위", "변화"), "출루중", "6/7/8/9/10/11/12/13/14"),
            Clause("BLACK_006", listOf("구위", "변화"), "출루중", "2/2/3/3/4/4/5"),
        )

        for (clause in clauses) {
            for (stat in clause.stats) {
                assertThat(valuesFor(skills, clause.skillKey, stat, clause.condition))
                    .describedAs("%s %s %s", clause.skillKey, stat, clause.condition)
                    .contains(clause.values)
                assertThat(alwaysValues(skills, clause.skillKey, stat))
                    .describedAs("%s %s ALWAYS", clause.skillKey, stat)
                    .doesNotContain(clause.values)
            }
        }
    }

    /**
     * 조건을 잘못 읽었거나 설명문이 낡아 틀어졌던 효과 행.
     *
     * - "평균 발사각이 N도 감소하고 추가로 …"는 발사각을 바꾸는 효과다. 조건(×0.2)으로 읽어 깎고 있었다.
     * - "주자 1명당", "주자가 더해질수록"은 주자 수만큼 곱해진다(`주자당`).
     * - 블랙 스킬의 능력치 목록은 인게임 화면을 따른다. 용기의 파워는 설명문만 보고 지웠다가 되살렸다.
     */
    @Test
    fun `conditions and stats follow the in game text`() {
        val skills = readBundledScoreSkills()

        for (stat in listOf("정확", "선구", "주루")) {
            assertThat(valuesFor(skills, "G_025", stat, "타순1_2")).containsExactly("2/3/3/4/4/5/5/6/6")
        }
        for (stat in listOf("파워", "정확")) {
            assertThat(valuesFor(skills, "BLACK_004", stat, "주자있음")).containsExactly("1/2/3/4/5/8/10")
        }
        for (stat in listOf("정확", "선구", "인내")) {
            assertThat(valuesFor(skills, "BLACK_004", stat, "주자없음")).containsExactly("4/5/6/7/8/11/14")
        }
        for (stat in listOf("파워", "인내")) {
            assertThat(valuesFor(skills, "BLACK_001", stat, "포지션_DH+발사각10이상")).containsExactly("1/2/3/4/5/8/11")
        }
        for (stat in listOf("정확", "선구")) {
            assertThat(valuesFor(skills, "BLACK_001", stat, "포지션_DH+발사각14이하")).containsExactly("1/2/3/4/5/8/11")
        }
        assertThat(skills.flatMap { it.effects }.map { it.condition }).noneMatch { it.contains("발사각조건") }

        val perRunner = mapOf(
            "G_004" to listOf("파워"),
            "G_013" to listOf("정확", "선구"),
            "G_051" to listOf("변화", "제구"),
            "M_035" to listOf("파워", "정확", "인내"),
            "M_038" to listOf("구속", "변화", "구위"),
        )
        for ((skillKey, stats) in perRunner) {
            for (stat in stats) {
                assertThat(valuesFor(skills, skillKey, stat, "주자당")).describedAs("%s %s", skillKey, stat).hasSize(1)
                assertThat(valuesFor(skills, skillKey, stat, "주자있음")).describedAs("%s %s", skillKey, stat).isEmpty()
                assertThat(valuesFor(skills, skillKey, stat, "주자2명이상")).describedAs("%s %s", skillKey, stat).isEmpty()
            }
        }

        assertThat(valuesFor(skills, "BLACK_007", "파워", "타순6_9")).containsExactly("2/2/3/3/4/6/8")
        assertThat(valuesFor(skills, "BLACK_002", "파워", "ALWAYS")).containsExactly("2/3/3/4/4/7/9")
        assertThat(valuesFor(skills, "BLACK_012", "파워", "ALWAYS")).containsExactly("3/4/5/6/7/10/13")
        // 도전 본능(타자) 인게임 설명은 "구위, 변화, 구속"이다.
        assertThat(valuesFor(skills, "BLACK_009", "제구", "ALWAYS")).isEmpty()
        for (stat in listOf("파워", "정확", "선구", "인내")) {
            assertThat(valuesFor(skills, "BLACK_009", stat, "상대팀홈런3")).containsExactly("1/1/1/1/2/4/6")
        }
        for (stat in listOf("구위", "변화", "제구", "구속")) {
            assertThat(valuesFor(skills, "BLACK_022", stat, "이닝출루2인이상")).containsExactly("1/1/2/3/4/6/8")
        }
    }

    @Test
    fun `duration counts are not encoded as standalone stat increases`() {
        val skills = readBundledScoreSkills()
        if (skills.none { it.skillKey == "M_029" }) {
            return
        }

        assertThat(alwaysValues(skills, "M_029", "구위")).doesNotContain("9")
        assertThat(alwaysValues(skills, "M_029", "변화")).doesNotContain("9")
        assertThat(alwaysValues(skills, "M_029", "제구")).doesNotContain("9")
        assertThat(alwaysValues(skills, "M_032", "파워")).doesNotContain("2")
        assertThat(alwaysValues(skills, "M_032", "정확")).doesNotContain("2")
        assertThat(alwaysValues(skills, "M_032", "선구")).doesNotContain("2")
        assertThat(alwaysValues(skills, "M_023", "구위")).doesNotContain("7")
        assertThat(alwaysValues(skills, "M_023", "변화")).doesNotContain("7")
        assertThat(alwaysValues(skills, "M_023", "구속")).doesNotContain("7")
        assertThat(alwaysValues(skills, "M_023", "제구")).doesNotContain("7")
        assertThat(alwaysValues(skills, "M_031", "구위")).doesNotContain("3")
        assertThat(alwaysValues(skills, "M_031", "변화")).doesNotContain("3")
    }

    /**
     * CSV의 조건 토큰이 전부 채점기에서 풀리는지 확인한다.
     *
     * [ScoreCalculator.conditionProbability]는 모르는 토큰에서 예외를 던지고, 그 예외는
     * 검증이 아니라 **채점 도중**에 터진다. 덱이면 26명 중 1명 때문에 전체 점수가 죽는다.
     * 오타 하나가 배포 뒤에 500으로 드러나지 않도록 여기서 먼저 깨뜨린다.
     */
    @Test
    fun `every condition token in bundled data resolves`() {
        val calculator = ScoreCalculator()
        val conditions = readBundledScoreSkills()
            .flatMap { skill -> skill.effects.map { skill.skillKey to it.condition } }
            .filter { !it.second.isNullOrBlank() }
            .distinct()

        assertThat(conditions).isNotEmpty()
        for ((skillKey, condition) in conditions) {
            assertThatCode { calculator.conditionProbability(condition, null) }
                .describedAs("%s의 조건 토큰 '%s'", skillKey, condition)
                .doesNotThrowAnyException()
        }
    }

    @Test
    fun `board sourced skills carry their announced values`() {
        val skills = readBundledScoreSkills()

        // 로드 워리어(G_021/G_062)의 수치는 v2.03.00 공지 표(D~SS+)에 "저니맨"이라는 이름으로 실렸다.
        // 인게임에는 저니맨이 없다(2026-09 확인). 예전에 공지 이름대로 G_077/G_078을 따로 넣어
        // 같은 스킬이 두 번 있었으므로 다시 들어오지 않게 막는다.
        for (stat in listOf("파워", "정확", "인내")) {
            assertThat(valuesFor(skills, "G_021", stat, "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9")
        }
        for (stat in listOf("구위", "변화", "구속")) {
            assertThat(valuesFor(skills, "G_021", stat, "원정")).containsExactly("2/2/2/2/2/3/4/5/6")
        }
        for (stat in listOf("파워", "정확", "주루")) {
            assertThat(valuesFor(skills, "G_062", stat, "원정")).containsExactly("2/2/2/2/2/3/4/5/6")
        }
        assertThat(skills.map { it.name }).doesNotContain("저니맨")

        // HOF 전용 스킬은 사다리가 D~S1 6단계라 공지 수치를 그대로 옮겼다.
        for (stat in listOf("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "HOF_045", stat, "ALWAYS")).containsExactly("4/5/6/7/8/9")
        }
        assertThat(valuesFor(skills, "HOF_045", "구위", "스위치타")).containsExactly("2/3/4/5/6/7")
        assertThat(valuesFor(skills, "HOF_045", "구위", "타순3")).containsExactly("1/2/3/4/5/6")

        assertThat(valuesFor(skills, "HOF_046", "구위", "ALWAYS")).containsExactly("3/4/5/6/7/8")
        assertThat(valuesFor(skills, "HOF_046", "파워", "홈")).containsExactly("4/6/8/10/12/14")

        assertThat(positionFor(skills, "HOF_047")).isEqualTo("2B")
        for (stat in listOf("파워", "정확", "선구", "주루")) {
            assertThat(valuesFor(skills, "HOF_047", stat, "포지션_2B")).containsExactly("4/6/6/8/8/10")
        }
        assertThat(valuesFor(skills, "HOF_047", "변화", "좌타")).containsExactly("1/2/3/4/5/6")
        assertThat(valuesFor(skills, "HOF_047", "변화", "타순2")).containsExactly("2/3/4/5/6/7")

        assertThat(valuesFor(skills, "HOF_048", "구위", "ALWAYS")).containsExactly("1/3/5/7/9/11")
        for (stat in listOf("구위", "변화", "구속", "제구", "지구력", "수비")) {
            assertThat(valuesFor(skills, "HOF_048", stat, "모드_랭킹대전+모드_랭킹토너먼트"))
                .containsExactly("4/6/8/10/12/14")
        }
    }

    /**
     * 같은 풀·포지션 안에서 스킬 이름이 겹치지 않는지 확인한다.
     *
     * 목록에 같은 이름이 둘 뜨면 사용자가 둘 다 골라 실제로는 한 번뿐인 효과를 이중으로
     * 계산하게 된다. 워크북을 다시 들여올 때 같은 스킬이 새 행으로 붙는 경우를 잡는다.
     * 이름이 같아도 타자용/투수용은 별개라 포지션까지 묶어 본다.
     */
    @Test
    fun `skill names are unique within a pool and position`() {
        val duplicates = readBundledScoreSkills()
            .groupBy { Triple(it.cardType, it.position, it.name) }
            .filterValues { it.size > 1 }
            .mapValues { (_, skills) -> skills.map { it.skillKey } }

        assertThat(duplicates).isEmpty()
    }

    /**
     * 게시판 공지와 어긋나 있던 값들. 되돌아가지 않게 못을 박는다.
     */
    @Test
    fun `renamed and mis-transcribed skills match the official announcements`() {
        val skills = readBundledScoreSkills()

        // 파워 히터는 2025 시즌 6차 Live 업데이트에서 슬러거로 이름이 바뀌었다.
        // 옛 이름 행이 남아 있으면 모먼트 타자 카드에 슬러거 계열이 둘 뜬다.
        assertThat(skills).noneMatch { it.name == "파워 히터" }
        assertThat(valuesFor(skills, "M_003", "파워", "ALWAYS")).containsExactly("5")
        assertThat(valuesFor(skills, "M_003", "파워", "스윗스팟")).containsExactly("2")

        // 홈 히어로 -> 프랜차이즈. 타자/투수 표기가 갈려 있었다.
        assertThat(skills.filter { it.skillKey in setOf("M_001", "M_015") })
            .allMatch { it.name == "프랜차이즈" }

        // 빅 유닛의 좌타자 상대 절은 5차 HOF 공지 기준 D레벨부터 3이다.
        for (stat in listOf("파워", "정확", "선구", "인내")) {
            assertThat(valuesFor(skills, "HOF_040", stat, "좌타상대")).containsExactly("3/3/3/3/3/4")
        }

        // 빅 허트(프랭크 토머스, DH 전용). 5차 HOF 공지 표기는 '빅 허트'인데
        // '빅 히트'로 옮겨 적혀 있었다. hit가 아니라 별명 The Big Hurt다.
        assertThat(skills.first { it.skillKey == "HOF_044" }.name).isEqualTo("빅 허트")
        for (stat in listOf("구위", "변화", "제구", "구속")) {
            assertThat(valuesFor(skills, "HOF_044", stat, "ALWAYS")).containsExactly("4/5/6/7/8/9")
            assertThat(valuesFor(skills, "HOF_044", stat, "포지션_DH")).containsExactly("3/5/7/9/11/13")
        }

        // 타점 사냥꾼. 워크북에 '터점'으로 오타가 나 있었다.
        assertThat(skills.first { it.skillKey == "BLACK_008" }.name).isEqualTo("타점 사냥꾼")

        // 오펜시브 리더(13차 Live 업데이트 모먼트 카드). 첫 타석 12에서 2씩 줄고
        // 공지의 괄호 주석대로 6~7번째 타석은 2로 멈춘다. G_034 오버페이스와 같은 꼴이다.
        // 엘 그란데(13차 Live 업데이트 HOF 티어 스킬). 두 임계 절은 배타가 아니라
        // 누적이다. 165를 넘는 카드는 155 절도 함께 받는다.
        val elGrande = skills.first { it.skillKey == "HOF_049" }
        assertThat(elGrande.name).isEqualTo("엘 그란데")
        assertThat(elGrande.cardType).isEqualTo("HOF")
        for (stat in listOf("구위", "변화")) {
            assertThat(valuesFor(skills, "HOF_049", stat, "ALWAYS")).containsExactly("5/6/7/8/9/10")
        }
        for (stat in listOf("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "HOF_049", stat, "기본주루수비합155이상"))
                .containsExactly("4/5/6/7/8/9")
            assertThat(valuesFor(skills, "HOF_049", stat, "기본주루수비합165이상"))
                .containsExactly("1/2/3/4/5/6")
        }

        val offensiveLeader = skills.first { it.skillKey == "M_045" }
        assertThat(offensiveLeader.name).isEqualTo("오펜시브 리더")
        assertThat(offensiveLeader.cardType).isEqualTo("MOMENT")
        for (stat in listOf("파워", "정확", "선구", "인내")) {
            assertThat(valuesFor(skills, "M_045", stat, "타석1")).containsExactly("12")
            assertThat(valuesFor(skills, "M_045", stat, "타석5")).containsExactly("4")
            assertThat(valuesFor(skills, "M_045", stat, "타석6_7")).containsExactly("2")
        }
    }

    @Test
    fun `October update moment skills appear for each role at their fixed grade`() {
        val skillIds = setOf("M_046", "M_047", "M_048", "M_049")
        val skills = readBundledScoreSkills().filter { it.skillKey in skillIds }
        assertThat(skills).hasSize(4)
        val repository = mock(ScoreSkillRepository::class.java)
        `when`(repository.findAll()).thenReturn(skills)
        for (pool in listOf("NORMAL", "MOMENT")) {
            `when`(repository.findByCardTypeIgnoreCase(pool)).thenReturn(skills.filter { it.cardType == pool })
        }
        val weights = skills.flatMap { it.effects }.associate { it.stat to 1.0 }
        val service = ScoreService(repository, ScoreCalculator(), weights)

        for ((position, performerId, warId) in listOf(
            Triple("BATTER", "M_046", "M_048"),
            Triple("SP", "M_047", "M_049"),
        )) {
            val options = service.listSkills("MOMENT", null, position)
            assertThat(options).extracting("skillId").containsExactly(performerId, warId)
            assertThat(options).extracting("name").containsExactly("2026 퍼포머트리오", "WAR 리더")
            assertThat(options).allSatisfy {
                assertThat(it.cardType).isEqualTo("MOMENT")
                assertThat(it.maxLevel).isEqualTo(1)
                assertThat(it.levelLabels).containsExactly("S")
            }

            val moment = service.buildScoreTable(ScoreTableRequest(position = position), 0)
                .tiers.first { it.tier == "moment" }
            val entries = moment.entries.filter { it.skillId in setOf(performerId, warId) }
            assertThat(entries).extracting("skillId").containsExactly(performerId, warId)
            assertThat(entries).extracting("score").containsExactly(60.0, if (position == "BATTER") 39.0 else 36.0)
            assertThat(entries).allSatisfy { assertThat(it.appliedGrade).isEqualTo("S") }
        }
    }

    @Test
    fun `2026 performer trio grants all six stats and adds the bonus in either announced mode`() {
        val skills = readBundledScoreSkills()
        val calculator = ScoreCalculator()
        for ((skillId, stats) in listOf(
            "M_046" to listOf("파워", "정확", "선구", "인내", "주루", "수비"),
            "M_047" to listOf("구속", "구위", "변화", "제구", "지구력", "수비"),
        )) {
            val skill = skills.first { it.skillKey == skillId }
            assertThat(skill.effects).hasSize(12)
            for (stat in stats) {
                assertThat(valuesFor(skills, skillId, stat, "ALWAYS")).containsExactly("10")
                assertThat(valuesFor(skills, skillId, stat, "모드_리그+모드_라이브매치")).containsExactly("10")
            }
            for ((league, live) in listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0)) {
                val result = calculator.calculate(
                    listOf(ScoreCalculator.Selection(skill, 1)),
                    stats.associateWith { 1.0 },
                    mapOf("모드_리그" to league, "모드_라이브매치" to live),
                )
                assertThat(result.total).isEqualTo(if (league == 0.0 && live == 0.0) 60.0 else 120.0)
            }
        }
    }

    @Test
    fun `WAR leader grants role stats and opponent reductions at announced values`() {
        val skills = readBundledScoreSkills()
        for ((skillId, buffs, debuffs) in listOf(
            Triple("M_048", listOf("파워", "정확", "선구"), listOf("변화", "구위")),
            Triple("M_049", listOf("변화", "구위"), listOf("파워", "정확", "선구")),
        )) {
            assertThat(skills.first { it.skillKey == skillId }.effects).hasSize(5)
            for (stat in buffs) {
                assertThat(alwaysValues(skills, skillId, stat)).containsExactly("9")
            }
            for (stat in debuffs) {
                assertThat(alwaysValues(skills, skillId, stat)).containsExactly("6")
            }
        }
    }

    private fun readBundledScoreSkills(): List<ScoreSkill> =
        resourceReader("score_skills.csv").use { skillsReader ->
            resourceReader("score_effects.csv").use { effectsReader ->
                loader.readScoreSkills(skillsReader, effectsReader)
            }
        }

    private fun alwaysValues(skills: List<ScoreSkill>, skillKey: String, stat: String): List<String> =
        valuesFor(skills, skillKey, stat, "ALWAYS")

    private fun positionFor(skills: List<ScoreSkill>, skillKey: String): String =
        skills.first { it.skillKey == skillKey }.position

    private fun valuesFor(
        skills: List<ScoreSkill>,
        skillKey: String,
        stat: String,
        condition: String,
    ): List<String> =
        skills.first { it.skillKey == skillKey }.effects
            .filter { it.stat == stat && it.condition == condition }
            .map { it.values }

    private fun resourceReader(path: String): Reader =
        InputStreamReader(ClassPathResource(path).inputStream, StandardCharsets.UTF_8)
}
