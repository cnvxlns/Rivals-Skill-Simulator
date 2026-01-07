package com.example.skillsim.config;

import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
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
import java.util.List;

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
        log.info("Loaded {} skills from CSV.", skills.size());
    }

    private List<Skill> readSkills(ClassPathResource resource) throws IOException {
        List<Skill> skills = new ArrayList<>();

        try (Reader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] row;
            int lineNumber = 1; // header line already skipped

            try {
                while ((row = csvReader.readNext()) != null) {
                    lineNumber++;

                    if (row.length < 4) {
                        log.warn("Skipping line {} in skills.csv: expected 4 columns but found {}", lineNumber, row.length);
                        continue;
                    }

                    try {
                        Skill skill = Skill.builder()
                                .name(normalize(row[0]))
                                .tier(parseTier(row[1]))
                                .position(normalize(row[2]).toUpperCase())
                                .description(normalize(row[3]))
                                .build();
                        skills.add(skill);
                    } catch (IllegalArgumentException ex) {
                        log.warn("Skipping line {} in skills.csv due to parse error: {}", lineNumber, ex.getMessage());
                    }
                }
            } catch (CsvValidationException ex) {
                log.error("Failed to parse skills.csv on line {}: {}", lineNumber + 1, ex.getMessage());
            }
        }

        return skills;
    }

    private Tier parseTier(String value) {
        return Tier.valueOf(normalize(value).toUpperCase());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
