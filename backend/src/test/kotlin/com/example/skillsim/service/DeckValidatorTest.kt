package com.example.skillsim.service

import com.example.skillsim.dto.DeckSkillRequest
import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.RelieverRole
import com.example.skillsim.model.DeckRoster
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DeckValidatorTest {

    private val validator = DeckFixtures.validator()

    @Test
    fun `정상 덱은 26명으로 통과한다`() {
        val roster = validator.validate(DeckFixtures.deckRequest())

        assertThat(roster.players).hasSize(DeckRoster.ROSTER_SIZE)
        assertThat(roster.starterCount).isEqualTo(5)
        assertThat(roster.closerCount).isEqualTo(2)
        assertThat(roster.relieverCount).isEqualTo(5)
    }

    @Test
    fun `중계 정원은 총원 12에서 선발과 마무리를 뺀 값이다`() {
        // 게임 화면의 "중간 계투(5/5)"가 이 계산 결과다.
        val cases = listOf(
            Triple(4, 1, 7), Triple(4, 2, 6), Triple(5, 1, 6),
            Triple(5, 2, 5), Triple(6, 1, 5), Triple(6, 2, 4),
        )
        for ((starters, closers, expected) in cases) {
            val roster = validator.validate(DeckFixtures.deckRequest(starters, closers))
            assertThat(roster.relieverCount).`as`("SP%d CP%d", starters, closers).isEqualTo(expected)
            assertThat(roster.players.count { DeckRules.isPitcher(it.slot) })
                .isEqualTo(DeckRoster.PITCHER_COUNT)
        }
    }

    @Test
    fun `선발이나 마무리가 허용 범위를 벗어나면 거부한다`() {
        assertThatThrownBy { validator.validate(DeckFixtures.deckRequest(starterCount = 3)) }
            .hasMessageContaining("Starter count must be between 4 and 6")
        assertThatThrownBy { validator.validate(DeckFixtures.deckRequest(starterCount = 7)) }
            .hasMessageContaining("Starter count must be between 4 and 6")
        assertThatThrownBy { validator.validate(DeckFixtures.deckRequest(closerCount = 3)) }
            .hasMessageContaining("Closer count must be between 1 and 2")
    }

    @Test
    fun `인원이 26명이 아니면 거부한다`() {
        val request = DeckFixtures.deckRequest()
        val short = request.copy(players = request.players!!.drop(1))

        assertThatThrownBy { validator.validate(short) }
            .hasMessageContaining("exactly 26 players, but was 25")
    }

    @Test
    fun `자리가 중복되면 거부한다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.toMutableList()
        players[1] = players[1].copy(slot = players[0].slot)

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("Duplicate deck slots")
    }

    @Test
    fun `자리 이름이 구성과 맞지 않으면 무엇이 빠지고 남는지 알려준다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "SP1") it.copy(slot = "SP9") else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("Missing: SP1")
            .hasMessageContaining("Unexpected: SP9")
    }

    @Test
    fun `주전 타순은 1부터 9까지 중복 없이 채워야 한다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "1B") it.copy(battingOrder = 1) else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("each batting order from 1 to 9 exactly once")
    }

    @Test
    fun `후보와 투수에는 타순을 붙일 수 없다`() {
        // 조용히 무시하면 클라이언트 버그가 숨는다.
        for (slot in listOf("BENCH1", "SP1")) {
            val request = DeckFixtures.deckRequest()
            val players = request.players!!.map {
                if (it.slot == slot) it.copy(battingOrder = 5) else it
            }
            assertThatThrownBy { validator.validate(request.copy(players = players)) }
                .`as`(slot)
                .hasMessageContaining("Batting order is only for starting batters")
        }
    }

    @Test
    fun `후보는 타자 포지션을 지정해야 한다`() {
        val request = DeckFixtures.deckRequest()
        val missing = request.players!!.map {
            if (it.slot == "BENCH1") it.copy(position = null) else it
        }
        assertThatThrownBy { validator.validate(request.copy(players = missing)) }
            .hasMessageContaining("Bench position is required")

        val pitcherPosition = request.players!!.map {
            if (it.slot == "BENCH1") it.copy(position = "SP") else it
        }
        assertThatThrownBy { validator.validate(request.copy(players = pitcherPosition)) }
            .hasMessageContaining("must be a batter position")
    }

    @Test
    fun `주전과 투수의 포지션은 자리에서 유도한다`() {
        // 클라이언트가 엉뚱한 포지션을 보내도 자리가 이긴다.
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "SP1") it.copy(position = "CP") else it
        }

        val roster = validator.validate(request.copy(players = players))

        assertThat(roster.players.single { it.slot == "SP1" }.position).isEqualTo("SP")
        assertThat(roster.players.single { it.slot == "SS" }.position).isEqualTo("SS")
    }

    @Test
    fun `중계 하위 역할은 중계에만 붙는다`() {
        val request = DeckFixtures.deckRequest()

        val missing = request.players!!.map {
            if (it.slot == "RP1") it.copy(relieverRole = null) else it
        }
        assertThatThrownBy { validator.validate(request.copy(players = missing)) }
            .hasMessageContaining("Reliever role is required")

        val misplaced = request.players!!.map {
            if (it.slot == "SP1") it.copy(relieverRole = RelieverRole.WIN) else it
        }
        assertThatThrownBy { validator.validate(request.copy(players = misplaced)) }
            .hasMessageContaining("Reliever role is only for relievers")
    }

    @Test
    fun `투수 슬롯 번호는 조건 게이트가 쓰도록 자리에서 뽑는다`() {
        val roster = validator.validate(DeckFixtures.deckRequest())

        assertThat(roster.players.single { it.slot == "SP3" }.pitcherSlot).isEqualTo(3)
        assertThat(roster.players.single { it.slot == "RP5" }.pitcherSlot).isEqualTo(5)
        assertThat(roster.players.single { it.slot == "CP2" }.pitcherSlot).isEqualTo(2)
        assertThat(roster.players.single { it.slot == "SS" }.pitcherSlot).isNull()
    }

    @Test
    fun `스킬 개수는 카드 타입의 슬롯 수와 정확히 같아야 한다`() {
        // ScoreService.calculate는 미만도 허용하지만 저장되는 덱은 완성품이어야 한다.
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "C") it.copy(skills = it.skills!!.take(2)) else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("must have exactly 3 skills, but had 2")
    }

    @Test
    fun `한 선수가 같은 스킬을 두 번 가질 수 없다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "C") {
                it.copy(skills = listOf("G_001", "G_001", "G_002").map { id -> DeckSkillRequest(id, 1) })
            } else {
                it
            }
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("Duplicate skill G_001")
    }

    @Test
    fun `없는 스킬은 404가 아니라 400으로 거부한다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "C") it.copy(skills = it.skills!!.drop(1) + DeckSkillRequest("NOPE", 1)) else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unknown skill NOPE")
    }

    @Test
    fun `레벨이 스킬의 최대치를 넘으면 거부한다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "C") it.copy(skills = it.skills!!.map { s -> s.copy(level = 4) }) else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("level must be between 1 and 3")
    }

    @Test
    fun `모르는 능력치 이름은 거부한다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "C") it.copy(stats = mapOf("없는스탯" to 100.0)) else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("Unknown stat 없는스탯")
    }

    @Test
    fun `능력치가 유한한 수가 아니면 거부한다`() {
        // 1e999는 파싱 단계에서 조용히 Infinity가 되어 점수를 오염시킨다.
        val request = DeckFixtures.deckRequest()
        for (bad in listOf(Double.POSITIVE_INFINITY, Double.NaN)) {
            val players = request.players!!.map {
                if (it.slot == "C") it.copy(stats = mapOf(DeckFixtures.STAT to bad)) else it
            }
            assertThatThrownBy { validator.validate(request.copy(players = players)) }
                .`as`(bad.toString())
                .hasMessageContaining("must be a finite number")
        }
    }

    @Test
    fun `투구 방향은 스위치가 될 수 없다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "SP1") it.copy(throwHand = Handedness.SWITCH) else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("Throwing hand cannot be SWITCH")

        // 타격 방향은 스위치가 정상이다.
        val switchBatter = request.players!!.map {
            if (it.slot == "SS") it.copy(batHand = Handedness.SWITCH) else it
        }
        validator.validate(request.copy(players = switchBatter))
    }

    @Test
    fun `지원하지 않는 카드 타입은 어느 자리인지 알려주며 거부한다`() {
        val request = DeckFixtures.deckRequest()
        val players = request.players!!.map {
            if (it.slot == "CF") it.copy(cardType = "PRIME") else it
        }

        assertThatThrownBy { validator.validate(request.copy(players = players)) }
            .hasMessageContaining("CF: Unsupported card type: PRIME")
    }
}
