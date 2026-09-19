package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.enums.Level
import com.example.skillsim.enums.TicketType
import com.example.skillsim.repository.InMemoryScoreSkillRepository
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * 롤 엔진의 분포 검증.
 *
 * 확률 코드는 테스트가 없으면 틀려도 조용하다. 결과가 그럴듯한 숫자로 나오기 때문이다.
 * 걷어냈던 `SkillServiceDistributionTest`가 하던 일을 같은 규모로 되살린다.
 *
 * 씨드를 고정해 값이 흔들리지 않게 한다.
 */
class RollEngineTest {

    private val repository = InMemoryScoreSkillRepository().apply {
        val loader = ScoreDataLoader(this)
        val skills = reader("score_skills.csv").use { s ->
            reader("score_effects.csv").use { e -> loader.readScoreSkills(s, e) }
        }
        saveAll(skills)
    }

    private val engine = RollEngine(repository)

    private fun reader(path: String) =
        InputStreamReader(ClassPathResource(path).inputStream, StandardCharsets.UTF_8)

    private fun input(
        grade: String,
        ticket: TicketType,
        variant: String = "NONE",
        position: String = "C",
        lockSlotOne: Boolean = false,
        currentSkillKeys: List<String?> = listOf(null, null, null, null),
    ) = RollEngine.RollInput(
        grade = grade,
        variant = variant,
        position = position,
        ticket = ticket,
        currentSkillKeys = currentSkillKeys,
        currentLevels = listOf(null, null, null, null),
        lockSlotOne = lockSlotOne,
    )

    /** 뽑은 티어의 비율. 20,000회면 35%가 ±1.5%p 안에 들어온다. */
    private fun tierShare(grade: String, ticket: TicketType, variant: String = "NONE"): Map<SkillTier, Double> {
        val random = Random(42)
        val counts = mutableMapOf<SkillTier, Int>()
        var slots = 0
        repeat(TRIALS) {
            engine.roll(input(grade, ticket, variant), random).forEach { slot ->
                val tier = slot.skill?.let { SkillTier.of(it) } ?: return@forEach
                counts[tier] = (counts[tier] ?: 0) + 1
                slots++
            }
        }
        return counts.mapValues { it.value * 100.0 / slots }
    }

    @Test
    fun `시그니처에 일반권을 쓰면 표대로 티어가 나온다`() {
        val share = tierShare("SIGNATURE", TicketType.SKILL_CHANGE)

        assertThat(share[SkillTier.IRON]).isCloseTo(35.0, within(2.0))
        assertThat(share[SkillTier.BRONZE]).isCloseTo(30.0, within(2.0))
        assertThat(share[SkillTier.SILVER]).isCloseTo(20.0, within(2.0))
        assertThat(share[SkillTier.GOLD]).isCloseTo(15.0, within(2.0))
    }

    @Test
    fun `최고급은 아이언을 뽑지 않고 첫 칸이 골드로 고정된다`() {
        val random = Random(7)
        var goldFirstSlot = 0
        var ironAnywhere = 0
        repeat(TRIALS) {
            val slots = engine.roll(input("SIGNATURE", TicketType.SUPREME_SKILL_CHANGE), random)
            if (slots[0].skill?.let { SkillTier.of(it) } == SkillTier.GOLD) goldFirstSlot++
            if (slots.any { it.skill?.let { s -> SkillTier.of(s) } == SkillTier.IRON }) ironAnywhere++
        }

        assertThat(goldFirstSlot).isEqualTo(TRIALS)
        assertThat(ironAnywhere).isZero()
    }

    /** 공식 확률 페이지 기준 HOF 지분: 일반 0.1% / 고급 1.5% / 최고급 10%. */
    @Test
    fun `HOF 전용 스킬은 티켓이 좋을수록 잘 나온다`() {
        val basic = tierShare("HOF", TicketType.SKILL_CHANGE)[SkillTier.HOF] ?: 0.0
        val premium = tierShare("HOF", TicketType.PREMIUM_SKILL_CHANGE)[SkillTier.HOF] ?: 0.0
        val supreme = tierShare("HOF", TicketType.SUPREME_SKILL_CHANGE)[SkillTier.HOF] ?: 0.0

        assertThat(basic).isCloseTo(0.1, within(0.15))
        assertThat(premium).isCloseTo(1.5, within(0.5))
        assertThat(supreme).isCloseTo(10.0, within(1.5))
    }

    /**
     * 블랙은 한 장까지다. 고급은 20%, 최고급은 반드시 한 칸 나온다.
     *
     * 슬롯 하나만 보면 5%인데(20% x 네 칸 중 하나), 카드 단위로 세면 20%다.
     */
    @Test
    fun `블랙은 카드당 한 장까지이고 티켓에 따라 확률이 다르다`() {
        fun blackCardRate(ticket: TicketType): Double {
            val random = Random(11)
            var withBlack = 0
            repeat(TRIALS) {
                val slots = engine.roll(input("SIGNATURE_BLACK", ticket), random)
                val blacks = slots.count { it.skill?.let { s -> SkillTier.of(s) } == SkillTier.BLACK }
                assertThat(blacks).isLessThanOrEqualTo(1)
                if (blacks == 1) withBlack++
            }
            return withBlack * 100.0 / TRIALS
        }

        assertThat(blackCardRate(TicketType.SKILL_CHANGE)).isZero()
        assertThat(blackCardRate(TicketType.PREMIUM_SKILL_CHANGE)).isCloseTo(20.0, within(2.0))
        assertThat(blackCardRate(TicketType.SUPREME_SKILL_CHANGE)).isEqualTo(100.0)
    }

    /** 시그니처 블랙은 슬롯이 네 개다. */
    @Test
    fun `시그니처 블랙은 네 칸을 뽑는다`() {
        assertThat(engine.roll(input("SIGNATURE_BLACK", TicketType.SKILL_CHANGE), Random(1))).hasSize(4)
        assertThat(engine.roll(input("SIGNATURE", TicketType.SKILL_CHANGE), Random(1))).hasSize(3)
    }

    /** WBC 변형은 티어를 가리지 않고 항상 S로 나온다. */
    @Test
    fun `WBC 변형은 항상 S레벨이다`() {
        val random = Random(3)
        repeat(500) {
            engine.roll(input("SIGNATURE", TicketType.SUPREME_SKILL_CHANGE, variant = "WBC"), random)
                .forEach { assertThat(it.level).isEqualTo(Level.S) }
        }
    }

    @Test
    fun `슈프림 모먼트는 첫 칸에서만 모먼트 전용이 나온다`() {
        val random = Random(5)
        var momentFirst = 0
        var momentElsewhere = 0
        repeat(TRIALS) {
            val slots = engine.roll(input("SUPREME_MOMENT", TicketType.SUPREME_SKILL_CHANGE), random)
            if (slots[0].skill?.let { SkillTier.of(it) } == SkillTier.MOMENT) momentFirst++
            if (slots.drop(1).any { it.skill?.let { s -> SkillTier.of(s) } == SkillTier.MOMENT }) momentElsewhere++
        }

        assertThat(momentFirst * 100.0 / TRIALS).isCloseTo(30.0, within(2.0))
        assertThat(momentElsewhere).isZero()
    }

    @Test
    fun `잠근 첫 칸은 그대로 남는다`() {
        val kept = repository.findByCardTypeIgnoreCase("NORMAL")
            .first { SkillRules.matchesPosition(it.position, "C") }
        val random = Random(9)

        repeat(200) {
            val slots = engine.roll(
                input("SIGNATURE", TicketType.SKILL_CHANGE, lockSlotOne = true,
                    currentSkillKeys = listOf(kept.skillKey, null, null, null)),
                random,
            )
            assertThat(slots[0].skill?.skillKey).isEqualTo(kept.skillKey)
            // 잠근 스킬이 다른 칸에 또 나오면 안 된다.
            assertThat(slots.drop(1).mapNotNull { it.skill?.skillKey }).doesNotContain(kept.skillKey)
        }
    }

    /**
     * 첫 칸 잠금은 등급을 가린다.
     *
     * 시그니처·프라임·임팩트는 되고, 시그니처 블랙과 HOF는 안 된다. 모먼트 계열은 첫 칸이
     * 이미 모먼트 전용일 때만 된다.
     */
    @Test
    fun `첫 칸 잠금은 등급 규칙을 따른다`() {
        val gold = repository.findByCardTypeIgnoreCase("NORMAL").first { it.skillKey.startsWith("G_") }
        val moment = repository.findByCardTypeIgnoreCase("MOMENT").first()

        assertThat(engine.canLockSlotOne("SIGNATURE", gold.skillKey)).isTrue()
        assertThat(engine.canLockSlotOne("PRIME", gold.skillKey)).isTrue()
        assertThat(engine.canLockSlotOne("IMPACT", gold.skillKey)).isTrue()

        assertThat(engine.canLockSlotOne("SIGNATURE_BLACK", gold.skillKey)).isFalse()
        assertThat(engine.canLockSlotOne("HOF", gold.skillKey)).isFalse()

        // 모먼트는 첫 칸이 모먼트 전용일 때만 잠글 수 있다.
        assertThat(engine.canLockSlotOne("SUPREME_MOMENT", gold.skillKey)).isFalse()
        assertThat(engine.canLockSlotOne("SUPREME_MOMENT", moment.skillKey)).isTrue()
        assertThat(engine.canLockSlotOne("SUPREME_MOMENT", null)).isFalse()
    }

    @Test
    fun `한 카드 안에서 같은 스킬이 두 번 나오지 않는다`() {
        val random = Random(13)
        repeat(1_000) {
            val keys = engine.roll(input("SIGNATURE_BLACK", TicketType.SUPREME_SKILL_CHANGE), random)
                .mapNotNull { it.skill?.skillKey }
            assertThat(keys).doesNotHaveDuplicates()
        }
    }

    private companion object {
        const val TRIALS = 20_000
    }
}
