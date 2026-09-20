package com.example.skillsim.config

import com.example.skillsim.model.DeckScoreCondition
import com.example.skillsim.model.DeckScoreLadder
import com.example.skillsim.model.DeckScoreReward
import com.example.skillsim.model.DeckScoreSide
import com.example.skillsim.model.DeckScoreTarget
import com.example.skillsim.model.ExcelSkillScore
import com.example.skillsim.model.StatGrowth
import com.example.skillsim.model.StatGrowthKey
import com.example.skillsim.model.TeamBuffSkill
import com.opencsv.CSVReaderBuilder
import com.opencsv.exceptions.CsvValidationException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(DeckDataLoader::class.java)

/**
 * 덱 계산 CSV 5종을 부팅 때 한 번 읽는다.
 *
 * [ScoreDataLoader]와 같은 방침이다 — 깨진 행은 경고만 남기고 넘어간다. 데이터 한 줄 때문에
 * 부팅이 죽는 것보다 낫다. 그 관대함 때문에 잘못된 데이터가 "조용히 틀린 점수"로만 드러나므로,
 * 판정은 `tools/validate_deck_data.py`가 CI에서 앞당긴다.
 *
 * 스킬 로더와 합치지 않고 따로 둔 이유는 원천이 달라서다. 저쪽은 손으로 다듬는 스킬 CSV,
 * 이쪽은 워크북에서 뽑은 파생 CSV다.
 */
@Component
class DeckDataLoader : CommandLineRunner {

    // Spring이 @Component를 open으로 열기 때문에 private setter를 쓸 수 없다. 읽기 전용
    // 프로퍼티로 내보내는 방식은 ScoreDataLoader.statWeights와 같다.
    @Volatile
    private var loadedTranscendence: Map<StatGrowthKey, StatGrowth> = emptyMap()

    @Volatile
    private var loadedEnhancement: Map<StatGrowthKey, StatGrowth> = emptyMap()

    @Volatile
    private var loadedRewards: List<DeckScoreReward> = emptyList()

    @Volatile
    private var loadedSkillScores: Map<Pair<String, Int>, List<ExcelSkillScore>> = emptyMap()

    @Volatile
    private var loadedTeamBuffs: Map<String, List<TeamBuffSkill>> = emptyMap()

    val transcendence: Map<StatGrowthKey, StatGrowth> get() = loadedTranscendence

    val enhancement: Map<StatGrowthKey, StatGrowth> get() = loadedEnhancement

    val deckScoreRewards: List<DeckScoreReward> get() = loadedRewards

    /** (스킬, 레벨) -> 변형들. 순서가 곧 우선순위이고 기본값(`ALWAYS`)이 맨 뒤에 온다. */
    val excelSkillScores: Map<Pair<String, Int>, List<ExcelSkillScore>> get() = loadedSkillScores

    val teamBuffSkills: Map<String, List<TeamBuffSkill>> get() = loadedTeamBuffs

    override fun run(vararg args: String) {
        loadedTranscendence = readGrowth("stat_growth_transcendence.csv", baseLevel = 0)
        loadedEnhancement = readGrowth("stat_growth_enhancement.csv", baseLevel = 1)
        loadedRewards = readRewards()
        loadedSkillScores = readSkillScores()
        loadedTeamBuffs = readTeamBuffs()
        log.info(
            "Loaded deck data: {} transcendence, {} enhancement, {} deck-score rewards, " +
                "{} workbook skill scores, {} team buff skills.",
            transcendence.size,
            enhancement.size,
            deckScoreRewards.size,
            excelSkillScores.values.sumOf { it.size },
            teamBuffSkills.values.sumOf { it.size },
        )
    }

    internal fun readGrowth(name: String, baseLevel: Int): Map<StatGrowthKey, StatGrowth> {
        val out = LinkedHashMap<StatGrowthKey, StatGrowth>()
        eachRow(name, columns = 4) { row, line ->
            val values = row[3].split("/").mapNotNull { it.trim().toIntOrNull() }
            if (values.isEmpty()) {
                log.warn("Skipping line {} in {}: no numeric values in '{}'", line, name, row[3])
                return@eachRow
            }
            val key = StatGrowthKey(row[0].uppercase(), row[1].uppercase(), row[2])
            out[key] = StatGrowth(key, baseLevel, values)
        }
        return out
    }

    internal fun readRewards(): List<DeckScoreReward> {
        val out = ArrayList<DeckScoreReward>()
        eachRow("deck_score_rewards.csv", columns = 7) { row, line ->
            val ladder = DeckScoreLadder.entries.firstOrNull { it.name == row[0] }
            val side = DeckScoreSide.entries.firstOrNull { it.name == row[2] }
            val condition = DeckScoreCondition.entries.firstOrNull { it.name == row[6] }
            val threshold = row[1].toIntOrNull()
            val amount = row[5].toIntOrNull()
            if (ladder == null || side == null || condition == null || threshold == null || amount == null) {
                log.warn("Skipping line {} in deck_score_rewards.csv: unreadable row {}", line, row)
                return@eachRow
            }
            out += DeckScoreReward(
                ladder = ladder,
                threshold = threshold,
                side = side,
                target = DeckScoreTarget.parse(row[3]),
                stat = row[4],
                amount = amount,
                condition = condition,
            )
        }
        return out
    }

    internal fun readSkillScores(): Map<Pair<String, Int>, List<ExcelSkillScore>> {
        val out = LinkedHashMap<Pair<String, Int>, MutableList<ExcelSkillScore>>()
        eachRow("excel_skill_scores.csv", columns = 6) { row, line ->
            val level = row[1].toIntOrNull()
            val score = row[3].toDoubleOrNull()
            if (level == null || score == null) {
                log.warn("Skipping line {} in excel_skill_scores.csv: unreadable row {}", line, row)
                return@eachRow
            }
            out.getOrPut(row[0] to level) { ArrayList() } +=
                ExcelSkillScore(row[0], level, row[2], score, row[4], row[5])
        }
        return out
    }

    internal fun readTeamBuffs(): Map<String, List<TeamBuffSkill>> {
        val out = LinkedHashMap<String, MutableList<TeamBuffSkill>>()
        eachRow("team_buff_skills.csv", columns = 6) { row, line ->
            val scope = TeamBuffSkill.Scope.entries.firstOrNull { it.name == row[1] }
            val values = row[5].split("/").mapNotNull { it.trim().toDoubleOrNull() }
            if (scope == null || values.isEmpty()) {
                log.warn("Skipping line {} in team_buff_skills.csv: unreadable row {}", line, row)
                return@eachRow
            }
            out.getOrPut(row[0]) { ArrayList() } += TeamBuffSkill(
                skillId = row[0],
                scope = scope,
                requiresSlot = row[2].ifEmpty { null },
                stat = row[3],
                condition = row[4],
                values = values,
                rawValues = row[5],
            )
        }
        return out
    }

    /**
     * 클래스패스 CSV를 한 줄씩 넘긴다. 파일이 없으면 조용히 비운다.
     *
     * [ScoreDataLoader.eachRow]와 같은 모양이되 그쪽은 private이라 여기서 다시 쓴다. 로더를
     * 하나로 합치자고 스킬 CSV의 관심사를 여기로 끌어오는 것보다 낫다고 봤다.
     */
    private inline fun eachRow(name: String, columns: Int, action: (List<String>, Int) -> Unit) {
        val resource = ClassPathResource(name)
        if (!resource.exists()) {
            log.warn("{} not found on classpath; deck data from that file stays empty.", name)
            return
        }
        BufferedReader(InputStreamReader(resource.inputStream, StandardCharsets.UTF_8)).use { reader ->
            CSVReaderBuilder(reader).withSkipLines(1).build().use { csv ->
                var line = 1
                while (true) {
                    line++
                    val row = try {
                        csv.readNext()
                    } catch (ex: CsvValidationException) {
                        log.warn("Skipping malformed line {} in {}: {}", line, name, ex.message)
                        return
                    } catch (ex: IOException) {
                        log.warn("Stopped reading {} at line {}: {}", name, line, ex.message)
                        return
                    } ?: return
                    val cells = row.map { it?.trim() ?: "" }
                    if (cells.size < columns) {
                        log.warn(
                            "Skipping line {} in {}: expected {} columns but found {}",
                            line, name, columns, cells.size,
                        )
                        continue
                    }
                    action(cells, line)
                }
            }
        }
    }
}
