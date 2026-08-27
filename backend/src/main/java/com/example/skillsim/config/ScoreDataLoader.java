package com.example.skillsim.config;

import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import com.example.skillsim.repository.ScoreSkillRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ScoreDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ScoreDataLoader.class);

    private final ScoreSkillRepository scoreSkillRepository;
    private final Map<String, Double> statWeights = new ConcurrentHashMap<>();

    public ScoreDataLoader(ScoreSkillRepository scoreSkillRepository) {
        this.scoreSkillRepository = scoreSkillRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        ClassPathResource skillsResource = new ClassPathResource("score_skills.csv");
        ClassPathResource effectsResource = new ClassPathResource("score_effects.csv");
        ClassPathResource weightsResource = new ClassPathResource("stat_weights.csv");

        if (!skillsResource.exists() || !effectsResource.exists()) {
            log.warn("score_skills.csv or score_effects.csv not found on classpath; skipping score seed load.");
            return;
        }

        List<ScoreSkill> skills;
        try (Reader skillsReader = utf8Reader(skillsResource);
             Reader effectsReader = utf8Reader(effectsResource)) {
            skills = readScoreSkills(skillsReader, effectsReader);
        }

        if (skills.isEmpty()) {
            log.warn("Score seed CSV files are empty; no score data loaded.");
        } else if (scoreSkillRepository != null) {
            scoreSkillRepository.deleteAll();
            scoreSkillRepository.saveAll(skills);
            int effectCount = skills.stream().mapToInt(skill -> skill.getEffects().size()).sum();
            log.info("Loaded {} score skills and {} score effects from CSV.", skills.size(), effectCount);
        }

        if (!weightsResource.exists()) {
            log.warn("stat_weights.csv not found on classpath; score stat weights remain empty.");
            return;
        }

        try (Reader weightsReader = utf8Reader(weightsResource)) {
            statWeights.clear();
            statWeights.putAll(readStatWeights(weightsReader));
            log.info("Loaded {} score stat weights from CSV.", statWeights.size());
        }
    }

    public Map<String, Double> getStatWeights() {
        return Collections.unmodifiableMap(statWeights);
    }

    List<ScoreSkill> readScoreSkills(Reader skillsReader, Reader effectsReader) throws IOException {
        Map<String, ScoreSkill> skillMap = readSkillMap(skillsReader);
        attachEffects(skillMap, effectsReader);
        return new ArrayList<>(skillMap.values());
    }

    Map<String, Double> readStatWeights(Reader weightsReader) throws IOException {
        Map<String, Double> weights = new LinkedHashMap<>();
        try (CSVReader csvReader = new CSVReaderBuilder(weightsReader).withSkipLines(1).build()) {
            String[] row;
            int lineNumber = 1;
            while ((row = readNext(csvReader, ++lineNumber, "stat_weights.csv")) != null) {
                if (row.length < 2) {
                    log.warn("Skipping line {} in stat_weights.csv: expected 2 columns but found {}", lineNumber, row.length);
                    continue;
                }
                String stat = normalize(row[0]);
                if (stat.isEmpty()) {
                    continue;
                }
                try {
                    weights.put(stat, Double.parseDouble(normalize(row[1])));
                } catch (NumberFormatException ex) {
                    log.warn("Skipping line {} in stat_weights.csv due to invalid weight: {}", lineNumber, row[1]);
                }
            }
        }
        return weights;
    }

    private Map<String, ScoreSkill> readSkillMap(Reader skillsReader) throws IOException {
        Map<String, ScoreSkill> skillMap = new LinkedHashMap<>();
        try (CSVReader csvReader = new CSVReaderBuilder(skillsReader).withSkipLines(1).build()) {
            String[] row;
            int lineNumber = 1;
            while ((row = readNext(csvReader, ++lineNumber, "score_skills.csv")) != null) {
                if (row.length < 5) {
                    log.warn("Skipping line {} in score_skills.csv: expected 5 columns but found {}", lineNumber, row.length);
                    continue;
                }
                String skillKey = normalize(row[0]);
                if (skillKey.isEmpty()) {
                    continue;
                }
                skillMap.put(skillKey, ScoreSkill.builder()
                        .skillKey(skillKey)
                        .cardType(normalize(row[1]).toUpperCase())
                        .position(normalize(row[2]).toUpperCase())
                        .name(normalize(row[3]))
                        .description(normalize(row[4]))
                        .build());
            }
        }
        return skillMap;
    }

    private void attachEffects(Map<String, ScoreSkill> skillMap, Reader effectsReader) throws IOException {
        try (CSVReader csvReader = new CSVReaderBuilder(effectsReader).withSkipLines(1).build()) {
            String[] row;
            int lineNumber = 1;
            while ((row = readNext(csvReader, ++lineNumber, "score_effects.csv")) != null) {
                if (row.length < 4) {
                    log.warn("Skipping line {} in score_effects.csv: expected at least 4 columns but found {}", lineNumber, row.length);
                    continue;
                }
                String skillKey = normalize(row[0]);
                ScoreSkill skill = skillMap.get(skillKey);
                if (skill == null) {
                    log.warn("Skipping line {} in score_effects.csv: unknown skill_id '{}'", lineNumber, skillKey);
                    continue;
                }
                ScoreEffect effect = ScoreEffect.builder()
                        .stat(normalize(row[1]))
                        .condition(normalize(row[2]).isEmpty() ? "ALWAYS" : normalize(row[2]))
                        .values(normalize(row[3]))
                        .baseStat(row.length > 4 && !normalize(row[4]).isEmpty() ? normalize(row[4]) : null)
                        .skill(skill)
                        .build();
                skill.getEffects().add(effect);
            }
        }
    }

    private String[] readNext(CSVReader csvReader, int lineNumber, String source) throws IOException {
        try {
            return csvReader.readNext();
        } catch (CsvValidationException ex) {
            log.warn("Skipping malformed line {} in {}: {}", lineNumber, source, ex.getMessage());
            return null;
        }
    }

    private Reader utf8Reader(ClassPathResource resource) throws IOException {
        return new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
