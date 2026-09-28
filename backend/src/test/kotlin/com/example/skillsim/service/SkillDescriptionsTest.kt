package com.example.skillsim.service

import com.example.skillsim.model.ScoreSkill
import com.opencsv.CSVReaderBuilder
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class SkillDescriptionsTest {

    @Test
    fun `맨 x는 해당 등급의 값으로 바뀐다`() {
        val skill = skill("S_001", "좌투수 상대할 때 파워, 정확 능력치가 x 증가합니다")

        assertThat(SkillDescriptions.resolve(skill, 5))
            .isEqualTo("좌투수 상대할 때 파워, 정확 능력치가 5 증가합니다")
    }

    @Test
    fun `x 더하기 1은 표현 전체가 계산된 값으로 바뀐다`() {
        // S_003 타점기계. x가 5면 x+1은 6이다. "6+1"로 남으면 안 된다.
        val skill = skill("S_003", "주자가 2루 이상일 때 파워, 정확 능력치가 x+1 증가합니다")

        assertThat(SkillDescriptions.resolve(skill, 5))
            .isEqualTo("주자가 2루 이상일 때 파워, 정확 능력치가 6 증가합니다")
    }

    @Test
    fun `y부터 시작하는 설명과 a까지 쓰는 설명도 글자마다 제 값이 들어간다`() {
        // HOF_009 타격 전문화. 예전에는 y를 둘째 효과 행으로 짐작하고 a는 아예 못 바꿨다.
        val skill = skill(
            "HOF_009",
            "상대 투수의 구위, 변화 능력치를 y 감소시킵니다. 2, 3 타순으로 출전 시 정확 능력치가 z 증가합니다. " +
                "4, 5번 타순으로 출전 시 파워, 정확, 선구 능력치가 a 증가합니다.",
        )

        assertThat(SkillDescriptions.resolve(skill, 6)).isEqualTo(
            "상대 투수의 구위, 변화 능력치를 9 감소시킵니다. 2, 3 타순으로 출전 시 정확 능력치가 5 증가합니다. " +
                "4, 5번 타순으로 출전 시 파워, 정확, 선구 능력치가 7 증가합니다.",
        )
    }

    @Test
    fun `중괄호는 값과 함께 지운다`() {
        val skill = skill("G_006", "3, 4, 5 타선에 배치시 추가로 파워 정확 능력치가 {x} 증가합니다")

        assertThat(SkillDescriptions.resolve(skill, 5))
            .isEqualTo("3, 4, 5 타선에 배치시 추가로 파워 정확 능력치가 5 증가합니다")
    }

    @Test
    fun `비율 효과는 퍼센트로 적는다`() {
        // HOF_006 숨은 위협. 효과 행은 0.02(비율)지만 설명은 "y%"다.
        val skill = skill("HOF_006", "정확 능력치의 y%만큼 파워, 정확 능력치가 추가로 증가합니다.")

        assertThat(SkillDescriptions.resolve(skill, 6))
            .isEqualTo("정확 능력치의 3%만큼 파워, 정확 능력치가 추가로 증가합니다.")
    }

    @Test
    fun `곱셈과 괄호 표현도 계산한다`() {
        assertThat(SkillDescriptions.resolve(skill("S_005", "체크스윙할 확률이 3*x% 증가합니다"), 5))
            .isEqualTo("체크스윙할 확률이 15% 증가합니다")
        assertThat(SkillDescriptions.resolve(skill("I_006", "번트 성공 시 5*(x+2)% 확률로"), 5))
            .isEqualTo("번트 성공 시 35% 확률로")
        assertThat(SkillDescriptions.resolve(skill("I_010", "실투가 발생할 확률이 0.5*x% 감소합니다"), 5))
            .isEqualTo("실투가 발생할 확률이 2.5% 감소합니다")
    }

    @Test
    fun `수치를 모르는 글자는 숫자를 지어내지 않고 자리만 지운다`() {
        // HOF_005 장타 본능의 평균 발사각 z. 효과 행도 공지 원문도 없다.
        val skill = skill("HOF_005", "평균 발사각이 z 증가합니다.")

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("평균 발사각이 증가합니다.")
    }

    @Test
    fun `레벨이 사다리보다 높으면 마지막 값을 쓴다`() {
        assertThat(SkillDescriptions.resolve(skill("S_001", "파워 능력치가 x 증가합니다"), 12))
            .isEqualTo("파워 능력치가 9 증가합니다")
    }

    @Test
    fun `한글 조사가 붙어도 치환한다`() {
        assertThat(SkillDescriptions.resolve(skill("S_001", "파워 능력치가 x증가합니다"), 5))
            .isEqualTo("파워 능력치가 5증가합니다")
    }

    @Test
    fun `단어 속의 글자와 표에 없는 스킬은 건드리지 않는다`() {
        assertThat(SkillDescriptions.resolve(skill("S_001", "MAX 수치와 xy 표기는 그대로 둔다"), 2))
            .isEqualTo("MAX 수치와 xy 표기는 그대로 둔다")
        assertThat(SkillDescriptions.resolve(skill("T_001", "파워 능력치가 x 증가합니다"), 2))
            .isEqualTo("파워 능력치가 x 증가합니다")
    }

    @Test
    fun `설명이 비어도 안전하다`() {
        assertThat(SkillDescriptions.resolve(skill("S_001", ""), 2)).isEmpty()
        assertThat(SkillDescriptions.resolve(null, 2)).isNull()
    }

    @Test
    fun `모든 스킬의 모든 등급에서 글자와 중괄호가 남지 않는다`() {
        val leftovers = realSkills().flatMap { skill ->
            (1..9).mapNotNull { level ->
                val resolved = SkillDescriptions.resolve(skill, level).orEmpty()
                val left = LEFTOVER.findAll(resolved).map { it.value }.toList()
                if (left.isEmpty()) null else "${skill.skillKey} L$level $left: $resolved"
            }
        }

        assertThat(leftovers).isEmpty()
    }

    @Test
    fun `표의 모든 행은 그 스킬 설명에 실제로 쓰이는 글자다`() {
        val letters = realSkills().associate { skill ->
            skill.skillKey to Regex("""(?<![A-Za-z])([xyzabc])(?![A-Za-z])""")
                .findAll(skill.description).map { it.groupValues[1][0] }.toSet()
        }

        val unused = SkillDescriptions.tableKeys().filter { (key, letter) -> letter !in letters[key].orEmpty() }

        assertThat(unused).isEmpty()
    }

    private fun realSkills(): List<ScoreSkill> =
        InputStreamReader(ClassPathResource("score_skills.csv").inputStream, StandardCharsets.UTF_8).use { reader ->
            CSVReaderBuilder(reader).withSkipLines(1).build().use { csv ->
                csv.readAll().map { row -> skill(row[0].trim(), row[4].trim()) }
            }
        }

    private fun skill(key: String, description: String) = ScoreSkill(
        skillKey = key,
        cardType = "NORMAL",
        position = "BATTER",
        name = key,
        description = description,
        effects = mutableListOf(),
    )

    private companion object {
        /** 치환되지 않고 남은 자리 글자나 중괄호. 라틴 단어(MAX, LF) 속 글자는 제외한다. */
        val LEFTOVER = Regex("""(?<![A-Za-z])[xyzabc](?![A-Za-z])|[{}]""")
    }
}
