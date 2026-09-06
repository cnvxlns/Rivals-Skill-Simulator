package com.example.skillsim.model

import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.RelieverRole
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 로스터는 jsonb 한 칸에 통째로 들어간다. 모양이 어긋나면 저장된 덱을 못 읽으므로
 * 왕복이 정확히 성립하는지 확인해 둔다.
 */
class DeckRosterJsonTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    private fun roster() = DeckRoster(
        starterCount = 5,
        closerCount = 2,
        players = listOf(
            DeckPlayer(
                slot = "SS",
                position = "SS",
                cardType = "NORMAL",
                skills = listOf(DeckSkillSelection("G_001", 3)),
                battingOrder = 1,
                stats = mapOf("파워" to 120.0),
                batHand = Handedness.SWITCH,
            ),
            DeckPlayer(
                slot = "RP2",
                position = "RP",
                cardType = "LIVE",
                skills = listOf(DeckSkillSelection("G_002", 1)),
                pitcherSlot = 2,
                relieverRole = RelieverRole.CHASE,
                throwHand = Handedness.LEFT,
            ),
        ),
    )

    @Test
    fun `직렬화한 뒤 다시 읽으면 같은 값이다`() {
        val json = mapper.writeValueAsString(roster())

        assertThat(mapper.readValue(json, DeckRoster::class.java)).isEqualTo(roster())
    }

    @Test
    fun `필드가 없어도 기본값으로 읽힌다`() {
        // 필드를 더할 때 기본값을 주면 기존에 저장된 덱을 계속 읽을 수 있다.
        val minimal = """
            {"starterCount":5,"closerCount":2,"players":[
              {"slot":"C","position":"C","cardType":"NORMAL",
               "skills":[{"skillId":"G_001","level":1}]}
            ]}
        """.trimIndent()

        val parsed = mapper.readValue(minimal, DeckRoster::class.java)
        val player = parsed.players.single()

        assertThat(player.battingOrder).isNull()
        assertThat(player.relieverRole).isNull()
        assertThat(player.stats).isEmpty()
    }

    @Test
    fun `중계 정원은 저장하지 않고 계산한다`() {
        // 총원이 고정이라 파생값이다. 저장하면 선발 수와 어긋날 여지가 생긴다.
        assertThat(mapper.writeValueAsString(roster())).doesNotContain("relieverCount")
        assertThat(roster().relieverCount).isEqualTo(5)
    }
}
