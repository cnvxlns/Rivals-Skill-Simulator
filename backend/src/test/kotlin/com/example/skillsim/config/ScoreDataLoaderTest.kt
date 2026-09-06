package com.example.skillsim.config

import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import com.example.skillsim.service.ScoreCalculator
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.core.io.ClassPathResource

class ScoreDataLoaderTest {

    private val loader = ScoreDataLoader(mock(ScoreSkillRepository::class.java))

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

        // 저니맨(v2.03.00). 게시글 표는 D~SS+ 7단계뿐이라 S3·S4는 마지막 증가폭을 이어 붙였다.
        for (stat in listOf("파워", "정확", "인내")) {
            assertThat(valuesFor(skills, "G_077", stat, "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9")
        }
        for (stat in listOf("구위", "변화", "구속")) {
            assertThat(valuesFor(skills, "G_077", stat, "원정")).containsExactly("2/2/2/2/2/3/4/5/6")
        }
        for (stat in listOf("파워", "정확", "주루")) {
            assertThat(valuesFor(skills, "G_078", stat, "원정")).containsExactly("2/2/2/2/2/3/4/5/6")
        }

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
