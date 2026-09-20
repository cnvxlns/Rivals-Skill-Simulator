package com.example.skillsim.service

import com.example.skillsim.dto.DeckImportResponse
import com.example.skillsim.dto.DeckPlayerRequest
import com.example.skillsim.dto.DeckSaveRequest
import com.example.skillsim.dto.DeckScoreChoiceRequest
import com.example.skillsim.dto.DeckSkillRequest
import com.example.skillsim.dto.PositionTrainingRequest
import com.example.skillsim.dto.SlotTrainingRequest
import com.example.skillsim.model.DeckScoreLadder
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import com.example.skillsim.service.xlsx.XlsxException
import com.example.skillsim.service.xlsx.XlsxReader
import org.springframework.stereotype.Service

/**
 * 덱 관리 워크북을 읽어 편집기 초안으로 바꾼다.
 *
 * 워크북의 `라인업` 시트만 읽는다. 초월·강화·덱 스코어 표는 우리 CSV에 이미 있으므로,
 * 낡은 워크북을 올려도 우리 점수 기준이 조용히 바뀌는 일이 없다 — `docs/convert_xlsx.py`를
 * 퇴역시킨(b15a559) 바로 그 실패를 되풀이하지 않으려는 것이다.
 *
 * 실패보다 경고를 택한다. 못 읽은 칸은 비워 두고 무엇을 넘겼는지 돌려준다. 어차피 사용자가
 * 편집기에서 마저 채우고 저장한다.
 */
@Service
class DeckImportService(
    private val scoreSkillRepository: ScoreSkillRepository,
) {

    fun import(bytes: ByteArray): DeckImportResponse {
        val sheet = XlsxReader().readSheet(bytes, LINEUP_SHEET)
        verifyLayout(sheet)

        val warnings = ArrayList<DeckImportResponse.ImportWarning>()
        val index = SkillNameIndex(scoreSkillRepository.findAll())

        val batters = BATTER_ROWS.mapNotNull { readPlayer(sheet, it, BATTER_LAYOUT, index, warnings) }
        val pitchers = PITCHER_ROWS.mapNotNull { readPlayer(sheet, it, PITCHER_LAYOUT, index, warnings) }
        val training = readPositionTraining(sheet, warnings)

        return DeckImportResponse(
            deck = DeckSaveRequest(
                // 워크북은 선발 5·중계 3·마무리 1이다. 우리 정원은 12라 중계가 6이 되고,
                // RP4~6과 후보 다섯 자리는 비어 있다.
                starterCount = STARTER_COUNT,
                closerCount = CLOSER_COUNT,
                players = batters + pitchers,
                deckScoreChoices = readDeckScoreChoices(sheet, warnings),
            ),
            positionTraining = training,
            warnings = warnings,
        )
    }

    /**
     * 우리가 아는 워크북인지 본다.
     *
     * 다른 엑셀을 올리면 엉뚱한 칸을 능력치로 읽어 조용히 이상한 덱이 만들어진다. 머리글
     * 네 칸만 보면 충분히 갈린다.
     */
    private fun verifyLayout(sheet: XlsxReader.Sheet) {
        val checks = listOf(
            Triple("B", 10, "포지션"),
            Triple("B", 21, "포지션"),
            Triple("AT", 8, "팀덱코"),
            Triple("AX", 8, "스덱코"),
        )
        val mismatched = checks.filterNot { (column, row, expected) ->
            sheet.text(column, row) == expected
        }
        if (mismatched.isNotEmpty()) {
            throw XlsxException(
                "덱 관리 워크북의 모양이 아닙니다. " +
                    mismatched.joinToString(", ") { (c, r, expected) -> "$c${r}에 '$expected'가 없습니다" },
            )
        }
    }

    /** 타자와 투수는 같은 자리에 다른 스탯을 적는다. 열 배치만 갈아 끼운다. */
    private class Layout(
        val stats: List<String>,
        val finalColumns: List<String>,
        val baseColumns: List<String>,
        val trainingColumns: List<String>,
        val specialColumns: List<String>,
        val positionTrainingColumns: List<String>,
        val positionBonusColumns: List<String>,
        val hasBattingOrder: Boolean,
    )

    private fun readPlayer(
        sheet: XlsxReader.Sheet,
        row: Int,
        layout: Layout,
        index: SkillNameIndex,
        warnings: MutableList<DeckImportResponse.ImportWarning>,
    ): DeckPlayerRequest? {
        val slot = sheet.text("B", row).uppercase()
        if (slot.isEmpty()) return null
        if (slot !in DeckRules.ALL_SLOTS) {
            warnings += warn("B$row", null, "UNKNOWN_SLOT", "자리 이름 '$slot'을 알 수 없어 건너뜁니다.")
            return null
        }

        val cardText = sheet.text("D", row)
        val card = WorkbookCards.parse(cardText)
        if (card == null && cardText.isNotEmpty()) {
            warnings += warn("D$row", slot, "UNKNOWN_CARD", "카드 '$cardText'를 알 수 없어 비워 둡니다.")
        }

        return DeckPlayerRequest(
            slot = slot,
            playerName = sheet.text("E", row).ifEmpty { null },
            cardGrade = card?.first,
            cardVariant = card?.second,
            skills = readSkills(sheet, row, slot, index, warnings),
            battingOrder = if (layout.hasBattingOrder) sheet.integer("C", row) else null,
            stats = readStats(sheet, row, layout.stats, layout.finalColumns),
            baseStats = readStats(sheet, row, layout.stats, layout.baseColumns),
            trainingStats = readStats(sheet, row, layout.stats, layout.trainingColumns),
            specialTrainingStats = readStats(sheet, row, layout.stats, layout.specialColumns),
            transcendenceLevel = sheet.integer("AB", row),
            enhancementLevel = sheet.integer("AF", row),
            year = sheet.integer("F", row),
        )
    }

    private fun readStats(
        sheet: XlsxReader.Sheet,
        row: Int,
        stats: List<String>,
        columns: List<String>,
    ): Map<String, Double>? {
        val out = LinkedHashMap<String, Double>()
        stats.forEachIndexed { i, stat ->
            sheet.number(columns[i], row)?.let { out[stat] = it }
        }
        return out.ifEmpty { null }
    }

    /**
     * 스킬 네 칸.
     *
     * 워크북은 게임 규칙을 검사하지 않는다 — 배포된 샘플부터 전원이 같은 스킬을 네 칸에
     * 중복으로 들고 있다. 여기서 고치지 않고 **그대로 가져온다.** 편집기가 빨간 테두리로
     * 보여 주고 사용자가 지우면 된다. 조용히 손보면 무엇이 달라졌는지 아무도 모른다.
     */
    private fun readSkills(
        sheet: XlsxReader.Sheet,
        row: Int,
        slot: String,
        index: SkillNameIndex,
        warnings: MutableList<DeckImportResponse.ImportWarning>,
    ): List<DeckSkillRequest> {
        val out = ArrayList<DeckSkillRequest>()
        for (column in SKILL_COLUMNS) {
            val text = sheet.text(column, row)
            if (text.isEmpty()) continue
            val parsed = WorkbookSkills.parse(text)
            if (parsed == null) {
                warnings += warn("$column$row", slot, "UNREADABLE_SKILL", "스킬 '$text'를 읽지 못했습니다.")
                continue
            }
            val skill = index.find(parsed.name, DeckRules.isPitcher(slot))
            if (skill == null) {
                warnings += warn("$column$row", slot, "UNKNOWN_SKILL", "스킬 '${parsed.name}'이 목록에 없습니다.")
                continue
            }
            val level = SkillRules.levelIndex(parsed.level, skill.cardType)
                .coerceIn(1, SkillRules.maxLevel(skill))
            out += DeckSkillRequest(skillId = skill.skillKey, level = level, option = parsed.option)
        }
        return out
    }

    /**
     * 자리별 포지션 훈련.
     *
     * 워크북은 레벨(AJ)로 표를 찾아 AK~AM에 값을 적고, 그 옆 AN~AP에 손으로 보너스를 더 적는다.
     * 우리는 레벨 표를 들이지 않았으므로(공개된 적이 없다) **계산된 증가치만** 가져온다.
     */
    private fun readPositionTraining(
        sheet: XlsxReader.Sheet,
        warnings: MutableList<DeckImportResponse.ImportWarning>,
    ): PositionTrainingRequest? {
        val slots = LinkedHashMap<String, SlotTrainingRequest>()
        for ((rows, layout) in listOf(BATTER_ROWS to BATTER_LAYOUT, PITCHER_ROWS to PITCHER_LAYOUT)) {
            for (row in rows) {
                val slot = sheet.text("B", row).uppercase()
                if (slot !in DeckRules.ALL_SLOTS) continue
                val stats = LinkedHashMap<String, Double>()
                layout.stats.forEachIndexed { i, stat ->
                    val looked = sheet.number(layout.positionTrainingColumns[i], row) ?: 0.0
                    val manual = sheet.number(layout.positionBonusColumns[i], row) ?: 0.0
                    if (looked + manual != 0.0) stats[stat] = looked + manual
                }
                if (stats.isNotEmpty()) {
                    slots[slot] = SlotTrainingRequest(stats = stats)
                }
            }
        }
        if (slots.isEmpty()) return null
        warnings += warn(
            null, null, "POSITION_TRAINING_FOUND",
            "포지션 훈련 ${slots.size}자리를 읽었습니다. 구단 전체에 걸리는 설정이라 따로 확인한 뒤 저장합니다.",
        )
        return PositionTrainingRequest(slots = slots)
    }

    /**
     * 덱 스코어에서 고른 칸들.
     *
     * 임계값은 우리가 아는 값을 가정하지 않고 워크북의 AT·AX열에서 읽는다. 게임이 사다리를
     * 바꾸면 워크북이 먼저 따라가기 때문이다.
     */
    private fun readDeckScoreChoices(
        sheet: XlsxReader.Sheet,
        warnings: MutableList<DeckImportResponse.ImportWarning>,
    ): List<DeckScoreChoiceRequest> {
        val out = ArrayList<DeckScoreChoiceRequest>()
        for ((ladder, columns) in LADDER_COLUMNS) {
            val (thresholdColumn, leftColumn, rightColumn) = columns
            for (row in DECK_SCORE_ROWS) {
                val threshold = sheet.integer(thresholdColumn, row) ?: continue
                if (threshold !in DeckScoreRules.thresholdsOf(ladder)) {
                    warnings += warn(
                        "$thresholdColumn$row", null, "UNKNOWN_TIER",
                        "$ladder 덱 스코어 $threshold 칸을 알 수 없어 건너뜁니다.",
                    )
                    continue
                }
                val left = sheet.text(leftColumn, row)
                val right = sheet.text(rightColumn, row)
                if (left.isEmpty() && right.isEmpty()) continue
                if (left.isNotEmpty() && right.isNotEmpty()) {
                    warnings += warn(
                        "$leftColumn$row", null, "BOTH_SIDES",
                        "$ladder $threshold 칸에 좌·우가 둘 다 표시돼 있어 좌를 씁니다.",
                    )
                }
                val side = if (left.isNotEmpty()) "LEFT" else "RIGHT"
                val marked = if (left.isNotEmpty()) left else right
                val needsDecade = DeckScoreRules.isDecadeTier(ladder, threshold)
                val decade = marked.toIntOrNull()?.takeIf { it in DeckScoreRules.DECADE_YEARS }
                if (needsDecade && decade == null) {
                    // 연대 칸에는 "O"가 아니라 연도를 적어야 한다. 엑셀 안에서도 "O"를 넣으면
                    // `연도 - "O"`가 되어 #VALUE!가 난다.
                    warnings += warn(
                        "$leftColumn$row", null, "MISSING_DECADE",
                        "$ladder $threshold 칸은 연대를 적어야 하는데 '$marked'가 있어 건너뜁니다.",
                    )
                    continue
                }
                out += DeckScoreChoiceRequest(
                    ladder.name,
                    threshold,
                    side,
                    if (needsDecade) decade else null,
                )
            }
        }
        return out
    }

    private fun warn(cell: String?, slot: String?, code: String, message: String) =
        DeckImportResponse.ImportWarning(cell, slot, code, message)

    /**
     * 스킬 이름으로 우리 스킬을 찾는다.
     *
     * 워크북 표기와 우리 CSV 표기의 차이는 셋뿐이다 — 공백(`배팅 머신`/`배팅머신`), WBC 약칭,
     * `피쳐`/`피처`. 여기 규칙은 `tools/deck_workbook.py`의 것과 같아야 한다.
     */
    private class SkillNameIndex(skills: List<ScoreSkill>) {
        private val byName: Map<Pair<Boolean, String>, List<ScoreSkill>> =
            skills.groupBy { isPitcher(it.position) to normalize(it.name) }

        fun find(name: String, pitcher: Boolean): ScoreSkill? {
            val key = ALIASES[normalize(name)] ?: normalize(name)
            val exact = byName[pitcher to key]
            if (exact?.size == 1) return exact.first()
            // 포지션이 한쪽으로만 있는 스킬도 있다. 쪽을 가리지 않고 하나면 그것을 쓴다.
            val any = byName.filterKeys { it.second == key }.values.flatten()
            return any.singleOrNull()
        }

        private fun isPitcher(position: String): Boolean =
            SkillRules.roleForPosition(position) != "BATTER"

        companion object {
            private val ALIASES = mapOf(normalize("리그 주도자") to normalize("리그의 주도자"))

            fun normalize(name: String): String = name
                .replace("WORLD BASEBALL CLASSIC", "WBC")
                .replace("피쳐", "피처")
                .replace(Regex("\\s+"), "")
                .uppercase()
        }
    }

    private companion object {
        const val LINEUP_SHEET = "라인업"
        const val STARTER_COUNT = 5
        const val CLOSER_COUNT = 1

        val BATTER_ROWS = 11..19
        val PITCHER_ROWS = 22..30
        val DECK_SCORE_ROWS = 10..38
        val SKILL_COLUMNS = listOf("K", "L", "M", "N")

        val BATTER_LAYOUT = Layout(
            stats = listOf("파워", "정확", "선구"),
            finalColumns = listOf("G", "H", "I"),
            baseColumns = listOf("S", "T", "U"),
            trainingColumns = listOf("V", "W", "X"),
            specialColumns = listOf("Y", "Z", "AA"),
            positionTrainingColumns = listOf("AK", "AL", "AM"),
            positionBonusColumns = listOf("AN", "AO", "AP"),
            hasBattingOrder = true,
        )

        val PITCHER_LAYOUT = Layout(
            stats = listOf("변화", "구위"),
            finalColumns = listOf("G", "H"),
            baseColumns = listOf("S", "T"),
            trainingColumns = listOf("V", "W"),
            specialColumns = listOf("Y", "Z"),
            positionTrainingColumns = listOf("AK", "AL"),
            positionBonusColumns = listOf("AN", "AO"),
            hasBattingOrder = false,
        )

        /** 사다리 -> (임계값 열, 좌 열, 우 열). */
        val LADDER_COLUMNS = listOf(
            DeckScoreLadder.TEAM to Triple("AT", "AU", "AV"),
            DeckScoreLadder.SPECIAL to Triple("AX", "AY", "AZ"),
        )
    }
}
