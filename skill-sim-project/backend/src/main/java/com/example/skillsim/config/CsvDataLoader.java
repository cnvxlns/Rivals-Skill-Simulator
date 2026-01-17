package com.example.skillsim.config;

import com.example.skillsim.enums.GrowthPattern;
import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
import com.example.skillsim.model.SkillEffect;
import com.example.skillsim.repository.SkillRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads static skill data from a CSV file at startup so we avoid maintaining SQL seed scripts.
 */
@Component
public class CsvDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CsvDataLoader.class);

    private final SkillRepository skillRepository;

    public CsvDataLoader(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        ClassPathResource resource = new ClassPathResource("skills.csv");
        if (!resource.exists()) {
            log.warn("skills.csv not found on classpath; skipping seed load.");
            return;
        }

        List<Skill> skills = readSkills(resource);
        if (skills.isEmpty()) {
            log.warn("skills.csv is empty; no seed data loaded.");
            return;
        }

        skillRepository.saveAll(skills);
        long momentCount = skills.stream().filter(s -> s.getTier() == Tier.MOMENT).count();
        log.info("Loaded {} skills from CSV. Moment tier count={}", skills.size(), momentCount);
    }

    private List<Skill> readSkills(ClassPathResource resource) throws IOException {
        Map<String, Skill> skillMap = new LinkedHashMap<>();

        try (Reader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] row;
            int lineNumber = 1; // header line already skipped

            try {
                while ((row = csvReader.readNext()) != null) {
                    lineNumber++;

                    if (row.length < 5) {
                        log.warn("Skipping line {} in skills.csv: expected at least 5 columns but found {}", lineNumber, row.length);
                        continue;
                    }

                    String name = normalize(row[0]);
                    String tierValue = normalize(row[1]);
                    String position = normalize(row[2]).toUpperCase();
                    String condition = normalize(row[3]);
                    String logicCode = row.length > 4 ? normalize(row[4]) : "";
                    String growthPatternRaw = row.length > 5 ? normalize(row[5]) : "";
                    String description = row.length > 6 ? normalize(row[6]) : "";

                    try {
                        Tier tier = parseTier(tierValue);
                        GrowthPattern growthPattern = GrowthPattern.fromCode(growthPatternRaw);
                        String key = buildSkillKey(name, tier, position);
                        Skill skill = skillMap.computeIfAbsent(key, k -> Skill.builder()
                                .name(name)
                                .tier(tier)
                                .position(position)
                                .description("")
                                .build());

                        if ((skill.getDescription() == null || skill.getDescription().isBlank()) && !description.isBlank()) {
                            skill.setDescription(description);
                        }

                        String normalizedCondition = condition.isEmpty() ? "ALWAYS" : condition.toUpperCase();
                        SkillEffect effect = SkillEffect.builder()
                                .condition(normalizedCondition)
                                .logicCode(logicCode)
                                .description(description)
                                .growthPattern(growthPattern)
                                .skill(skill)
                                .build();
                        skill.getEffects().add(effect);
                    } catch (IllegalArgumentException ex) {
                        log.warn("Skipping line {} in skills.csv due to parse error: {}", lineNumber, ex.getMessage());
                    }
                }
            } catch (CsvValidationException ex) {
                log.error("Failed to parse skills.csv on line {}: {}", lineNumber + 1, ex.getMessage());
            }
        }

        return new ArrayList<>(skillMap.values());
    }

    private Tier parseTier(String value) {
        return Tier.valueOf(normalize(value).toUpperCase());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildSkillKey(String name, Tier tier, String position) {
        return normalize(name) + "|" + tier.name() + "|" + normalize(position).toUpperCase();
    }
}
