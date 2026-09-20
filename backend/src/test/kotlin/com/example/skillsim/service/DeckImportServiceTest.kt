package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.repository.InMemoryScoreSkillRepository
import com.example.skillsim.service.xlsx.TestWorkbookBuilder
import com.example.skillsim.service.xlsx.XlsxException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 워크북을 편집기 초안으로 옮기는 일.
 *
 * 워크북은 게임 규칙을 검사하지 않는다 — 배포된 샘플부터 전원이 같은 스킬을 네 칸에 중복으로
 * 들고 있다. 여기서 손보지 않고 그대로 가져오는 것이 규칙이고, 그 규칙을 여기서 지킨다.
 */
class DeckImportServiceTest {

    private val repository = InMemoryScoreSkillRepository().also { ScoreDataLoader(it).run() }
    private val service = DeckImportService(repository)

    /** 라인업 시트의 뼈대. 지문 네 칸과 한 명씩만 채운다. */
    private fun workbook(block: TestWorkbookBuilder.SheetBuilder.() -> Unit = {}): ByteArray =
        TestWorkbookBuilder().sheet("라인업")
            .text("B10", "포지션")
            .text("B21", "포지션")
            .text("AT8", "팀덱코")
            .text("AX8", "스덱코")
            .apply(block)
            .and()
            .build()

    private fun TestWorkbookBuilder.SheetBuilder.batter(
        row: Int,
        slot: String,
        order: Int,
        card: String = "시그니처 블랙",
    ) = text("B$row", slot)
        .number("C$row", order)
        .text("D$row", card)
        .text("E$row", "오타니")
        .number("F$row", 2026)

    @Test
    fun `지문이 맞지 않으면 거절한다`() {
        // 다른 엑셀을 올리면 엉뚱한 칸을 능력치로 읽어 조용히 이상한 덱이 만들어진다.
        val stranger = TestWorkbookBuilder().sheet("라인업").text("A1", "안녕").and().build()

        assertThatThrownBy { service.import(stranger) }
            .isInstanceOf(XlsxException::class.java)
            .hasMessageContaining("덱 관리 워크북")
    }

    @Test
    fun `타자 한 줄을 읽는다`() {
        val result = service.import(
            workbook {
                batter(11, "C", 9)
                number("G11", 83).number("H11", 82).number("I11", 75)
                number("S11", 65).number("T11", 63).number("U11", 61)
                number("V11", 15).number("W11", 13).number("X11", 14)
                number("Y11", 3)
                number("AB11", 2).number("AF11", 18)
                text("K11", "[S0] 리드오프 (O)")
            },
        )

        val player = result.deck.players!!.single()
        assertThat(player.slot).isEqualTo("C")
        assertThat(player.battingOrder).isEqualTo(9)
        assertThat(player.cardGrade).isEqualTo("SIGNATURE_BLACK")
        assertThat(player.cardVariant).isEqualTo("NONE")
        assertThat(player.playerName).isEqualTo("오타니")
        assertThat(player.year).isEqualTo(2026)
        assertThat(player.stats).containsEntry("파워", 83.0).containsEntry("선구", 75.0)
        assertThat(player.baseStats).containsEntry("파워", 65.0)
        assertThat(player.trainingStats).containsEntry("정확", 13.0)
        assertThat(player.specialTrainingStats).containsExactlyEntriesOf(mapOf("파워" to 3.0))
        assertThat(player.transcendenceLevel).isEqualTo(2)
        assertThat(player.enhancementLevel).isEqualTo(18)
        assertThat(player.skills!!).singleElement()
            .satisfies({ assertThat(it.option).isEqualTo("O") })
    }

    @Test
    fun `투수는 변화와 구위를 읽는다`() {
        val result = service.import(
            workbook {
                text("B22", "SP1")
                text("D22", "WBC 시그니처 블랙")
                number("G22", 70).number("H22", 79)
                number("S22", 60).number("T22", 70)
                text("K22", "[S0] 타선지원 (선발)")
            },
        )

        val player = result.deck.players!!.single()
        assertThat(player.slot).isEqualTo("SP1")
        assertThat(player.cardGrade).isEqualTo("SIGNATURE_BLACK")
        assertThat(player.cardVariant).isEqualTo("WBC")
        assertThat(player.stats).containsExactlyInAnyOrderEntriesOf(
            mapOf("변화" to 70.0, "구위" to 79.0),
        )
        assertThat(player.battingOrder).isNull()
        assertThat(player.skills!!.single().option).isEqualTo("선발")
    }

    @Test
    fun `투수진 정원을 채우지 않고 돌려준다`() {
        // 워크북에는 18명뿐이다. 나머지 여덟 자리는 사용자가 편집기에서 채운다.
        val result = service.import(workbook { batter(11, "C", 1) })

        assertThat(result.deck.starterCount).isEqualTo(5)
        assertThat(result.deck.closerCount).isEqualTo(1)
        assertThat(result.deck.players).hasSize(1)
    }

    @Test
    fun `중복 스킬을 그대로 가져온다`() {
        // 배포된 샘플 워크북이 실제로 이렇다. 조용히 손보면 무엇이 달라졌는지 아무도 모른다.
        val result = service.import(
            workbook {
                batter(11, "C", 1)
                text("K11", "[S0] 리드오프 (O)")
                text("L11", "[S0] 리드오프 (O)")
                text("M11", "[S0] 리드오프 (O)")
                text("N11", "[S0] 리드오프 (O)")
            },
        )

        val skills = result.deck.players!!.single().skills!!
        assertThat(skills).hasSize(4)
        assertThat(skills.map { it.skillId }.distinct()).hasSize(1)
    }

    @Test
    fun `모르는 스킬은 건너뛰고 알려 준다`() {
        // `파워 히터`는 워크북에 있지만 우리 CSV에는 없다. 슬러거와 다른 스킬이다.
        val result = service.import(
            workbook {
                batter(11, "C", 1)
                text("K11", "[S0] 파워 히터 (-)")
            },
        )

        assertThat(result.deck.players!!.single().skills).isEmpty()
        assertThat(result.warnings).anySatisfy {
            assertThat(it.code).isEqualTo("UNKNOWN_SKILL")
            assertThat(it.cell).isEqualTo("K11")
        }
    }

    @Test
    fun `모르는 카드는 비워 두고 알려 준다`() {
        val result = service.import(
            workbook { batter(11, "C", 1, card = "듣보 카드") },
        )

        assertThat(result.deck.players!!.single().cardGrade).isNull()
        assertThat(result.warnings).anySatisfy { assertThat(it.code).isEqualTo("UNKNOWN_CARD") }
    }

    @Test
    fun `FA 카드의 띄어쓰기 차이를 흡수한다`() {
        // 워크북의 드롭다운은 `FA 시그니처`, 표 키는 `FA시그니처`다. 엑셀 안에서는 #N/A가 난다.
        val spaced = service.import(workbook { batter(11, "C", 1, card = "FA 시그니처") })
        val tight = service.import(workbook { batter(11, "C", 1, card = "FA시그니처") })

        assertThat(spaced.deck.players!!.single().cardVariant).isEqualTo("FA")
        assertThat(tight.deck.players!!.single().cardVariant).isEqualTo("FA")
    }

    @Test
    fun `덱 스코어에서 고른 칸을 읽는다`() {
        val result = service.import(
            workbook {
                number("AT10", 200).text("AU10", "O")
                number("AT11", 240).text("AV11", "O")
                number("AX10", 100).text("AY10", "O")
            },
        )

        val choices = result.deck.deckScoreChoices!!
        assertThat(choices).hasSize(3)
        assertThat(choices).anySatisfy {
            assertThat(it.ladder).isEqualTo("TEAM")
            assertThat(it.threshold).isEqualTo(200)
            assertThat(it.side).isEqualTo("LEFT")
        }
        assertThat(choices).anySatisfy {
            assertThat(it.threshold).isEqualTo(240)
            assertThat(it.side).isEqualTo("RIGHT")
        }
        assertThat(choices).anySatisfy { assertThat(it.ladder).isEqualTo("SPECIAL") }
    }

    @Test
    fun `연대 칸은 연도를 읽는다`() {
        val result = service.import(
            workbook { number("AX33", 615).number("AZ33", 2010) },
        )

        assertThat(result.deck.deckScoreChoices!!.single().decadeYear).isEqualTo(2010)
    }

    @Test
    fun `연대 칸에 연도가 아닌 표시가 있으면 건너뛴다`() {
        // 워크북 자체의 결함이다. 680 칸은 검증이 "O"인데 수식은 연도를 본다.
        val result = service.import(
            workbook { number("AX37", 680).text("AY37", "O") },
        )

        assertThat(result.deck.deckScoreChoices).isEmpty()
        assertThat(result.warnings).anySatisfy { assertThat(it.code).isEqualTo("MISSING_DECADE") }
    }

    @Test
    fun `좌우가 둘 다 표시돼 있으면 좌를 쓰고 알려 준다`() {
        val result = service.import(
            workbook { number("AT10", 200).text("AU10", "O").text("AV10", "O") },
        )

        assertThat(result.deck.deckScoreChoices!!.single().side).isEqualTo("LEFT")
        assertThat(result.warnings).anySatisfy { assertThat(it.code).isEqualTo("BOTH_SIDES") }
    }

    @Test
    fun `포지션 훈련은 조회값과 손으로 적은 값을 더한다`() {
        val result = service.import(
            workbook {
                batter(11, "C", 1)
                number("AK11", 4).number("AL11", 4).number("AM11", 4)
                number("AO11", 2)
            },
        )

        val slot = result.positionTraining!!.slots!!.getValue("C")
        assertThat(slot.stats).containsExactlyInAnyOrderEntriesOf(
            mapOf("파워" to 4.0, "정확" to 6.0, "선구" to 4.0),
        )
        assertThat(result.warnings).anySatisfy {
            assertThat(it.code).isEqualTo("POSITION_TRAINING_FOUND")
        }
    }

    @Test
    fun `모르는 자리는 건너뛰고 알려 준다`() {
        val result = service.import(workbook { text("B11", "XX") })

        assertThat(result.deck.players).isEmpty()
        assertThat(result.warnings).anySatisfy { assertThat(it.code).isEqualTo("UNKNOWN_SLOT") }
    }

    /**
     * 진짜 워크북으로 한 번 훑어 본다.
     *
     * 워크북은 추적하지 않으므로 CI에서는 돌지 않는다. 손에 있을 때만 켠다.
     *
     *     ./gradlew test -Dworkbook=/경로/MLB라이벌_덱관리프로그램_260910.xlsx
     */
    @Test
    fun `손에 있는 진짜 워크북을 읽어 본다`() {
        val path = System.getProperty("workbook") ?: return
        val result = service.import(java.io.File(path).readBytes())

        val players = result.deck.players!!
        assertThat(players).hasSize(18)
        assertThat(players.map { it.slot }).containsAll(DeckRules.LINEUP_SLOTS)
        assertThat(players.map { it.slot }).contains("SP1", "SP5", "RP1", "RP3", "CP1")
        assertThat(players).allSatisfy { assertThat(it.cardGrade).isNotNull() }
        assertThat(result.warnings.map { it.code })
            .doesNotContain("UNKNOWN_SLOT", "UNKNOWN_CARD", "UNREADABLE_SKILL")
    }
}
