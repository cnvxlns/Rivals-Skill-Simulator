package com.example.skillsim.config

import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.exceptions.CsvValidationException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(ScoreDataLoader::class.java)

@Component
class ScoreDataLoader(
    private val scoreSkillRepository: ScoreSkillRepository,
) : CommandLineRunner {

    private val mutableStatWeights = ConcurrentHashMap<String, Double>()

    val statWeights: Map<String, Double> get() = mutableStatWeights.toMap()

    override fun run(vararg args: String) {
        val skillsResource = ClassPathResource("score_skills.csv")
        val effectsResource = ClassPathResource("score_effects.csv")
        val weightsResource = ClassPathResource("stat_weights.csv")

        if (!skillsResource.exists() || !effectsResource.exists()) {
            log.warn("score_skills.csv or score_effects.csv not found on classpath; skipping score seed load.")
            return
        }

        val skills = skillsResource.utf8Reader().use { skillsReader ->
            effectsResource.utf8Reader().use { effectsReader ->
                readScoreSkills(skillsReader, effectsReader)
            }
        }

        if (skills.isEmpty()) {
            log.warn("Score seed CSV files are empty; no score data loaded.")
        } else {
            scoreSkillRepository.deleteAll()
            scoreSkillRepository.saveAll(skills)
            val effectCount = skills.sumOf { it.effects.size }
            log.info("Loaded {} score skills and {} score effects from CSV.", skills.size, effectCount)
        }

        if (!weightsResource.exists()) {
            log.warn("stat_weights.csv not found on classpath; score stat weights remain empty.")
            return
        }

        weightsResource.utf8Reader().use { weightsReader ->
            mutableStatWeights.clear()
            mutableStatWeights.putAll(readStatWeights(weightsReader))
            log.info("Loaded {} score stat weights from CSV.", mutableStatWeights.size)
        }
    }

    internal fun readScoreSkills(skillsReader: Reader, effectsReader: Reader): List<ScoreSkill> {
        val skillMap = readSkillMap(skillsReader)
        attachEffects(skillMap, effectsReader)
        return skillMap.values.toList()
    }

    internal fun readStatWeights(weightsReader: Reader): Map<String, Double> {
        val weights = LinkedHashMap<String, Double>()
        csvReader(weightsReader).use { reader ->
            reader.eachRow("stat_weights.csv") { row, lineNumber ->
                if (row.size < 2) {
                    log.warn(
                        "Skipping line {} in stat_weights.csv: expected 2 columns but found {}",
                        lineNumber,
                        row.size,
                    )
                    return@eachRow
                }
                val stat = row[0].normalize()
                if (stat.isEmpty()) {
                    return@eachRow
                }
                val weight = row[1].normalize().toDoubleOrNull()
                if (weight == null) {
                    log.warn(
                        "Skipping line {} in stat_weights.csv due to invalid weight: {}",
                        lineNumber,
                        row[1],
                    )
                    return@eachRow
                }
                weights[stat] = weight
            }
        }
        return weights
    }

    private fun readSkillMap(skillsReader: Reader): Map<String, ScoreSkill> {
        val skillMap = LinkedHashMap<String, ScoreSkill>()
        csvReader(skillsReader).use { reader ->
            reader.eachRow("score_skills.csv") { row, lineNumber ->
                if (row.size < 5) {
                    log.warn(
                        "Skipping line {} in score_skills.csv: expected 5 columns but found {}",
                        lineNumber,
                        row.size,
                    )
                    return@eachRow
                }
                val skillKey = row[0].normalize()
                if (skillKey.isEmpty()) {
                    return@eachRow
                }
                skillMap[skillKey] = ScoreSkill(
                    skillKey = skillKey,
                    cardType = row[1].normalize().uppercase(),
                    position = row[2].normalize().uppercase(),
                    name = row[3].normalize(),
                    description = row[4].normalize(),
                )
            }
        }
        return skillMap
    }

    private fun attachEffects(skillMap: Map<String, ScoreSkill>, effectsReader: Reader) {
        csvReader(effectsReader).use { reader ->
            reader.eachRow("score_effects.csv") { row, lineNumber ->
                if (row.size < 4) {
                    log.warn(
                        "Skipping line {} in score_effects.csv: expected at least 4 columns but found {}",
                        lineNumber,
                        row.size,
                    )
                    return@eachRow
                }
                val skillKey = row[0].normalize()
                val skill = skillMap[skillKey]
                if (skill == null) {
                    log.warn(
                        "Skipping line {} in score_effects.csv: unknown skill_id '{}'",
                        lineNumber,
                        skillKey,
                    )
                    return@eachRow
                }
                val condition = row[2].normalize().ifEmpty { "ALWAYS" }
                val baseStat = row.getOrNull(4)?.normalize()?.ifEmpty { null }
                skill.effects += ScoreEffect(
                    stat = row[1].normalize(),
                    condition = condition,
                    values = row[3].normalize(),
                    baseStat = baseStat,
                    skill = skill,
                )
            }
        }
    }

    private fun csvReader(reader: Reader): CSVReader =
        CSVReaderBuilder(reader).withSkipLines(1).build()

    /**
     * 헤더를 건너뛴 뒤 남은 행을 하나씩 넘긴다.
     *
     * 깨진 행은 예외로 올리지 않고 경고만 남기고 멈춘다. 데이터 한 줄 때문에 부팅이
     * 죽는 것보다 낫다는 판단이 원본 로직에 담겨 있었다.
     */
    private inline fun CSVReader.eachRow(source: String, action: (List<String>, Int) -> Unit) {
        var lineNumber = 1
        while (true) {
            lineNumber++
            val row = try {
                readNext()
            } catch (ex: CsvValidationException) {
                log.warn("Skipping malformed line {} in {}: {}", lineNumber, source, ex.message)
                return
            } catch (ex: IOException) {
                log.warn("Stopped reading {} at line {}: {}", source, lineNumber, ex.message)
                return
            } ?: return
            action(row.toList(), lineNumber)
        }
    }

    private fun ClassPathResource.utf8Reader(): Reader =
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

    private fun String?.normalize(): String = this?.trim() ?: ""
}
