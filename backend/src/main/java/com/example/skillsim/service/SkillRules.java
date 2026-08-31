package com.example.skillsim.service;

import com.example.skillsim.enums.Level;
import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SkillRules {

    static final int DEFAULT_SLOT_COUNT = 3;

    private static final Set<String> BATTER_POSITIONS = Set.of("BATTER", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH", "IF", "OF");
    private static final Set<String> INFIELD_POSITIONS = Set.of("1B", "2B", "3B", "SS");
    private static final Set<String> PITCHER_POSITIONS = Set.of("PITCHER", "SP", "RP", "CP");

    private SkillRules() {
    }

    static String normalizeCardType(String cardType) {
        String normalized = normalizeRequired(cardType, "Card type is required.");
        return switch (normalized) {
            case "SIGNATURE", "NORMAL" -> "NORMAL";
            case "SIGNATURE_BLACK", "BLACK" -> "BLACK";
            case "WBC_SIGNATURE_BLACK", "WBC_BLACK" -> "WBC_BLACK";
            case "WBC", "MOMENT", "HOF", "SUPREME_MOMENT" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported card type: " + cardType);
        };
    }

    static int slotCount(String cardType) {
        String normalized = normalizeCardType(cardType);
        return Set.of("BLACK", "WBC_BLACK").contains(normalized) ? 4 : DEFAULT_SLOT_COUNT;
    }

    static List<Level> gradeLadder(String cardType) {
        return switch (normalizeCardType(cardType)) {
            case "MOMENT", "SUPREME_MOMENT" -> List.of(Level.S);
            case "WBC", "WBC_BLACK" -> List.of(Level.S, Level.S1, Level.S2);
            case "BLACK" -> List.of(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2);
            case "HOF" -> List.of(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1);
            default -> List.of(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2, Level.S3, Level.S4);
        };
    }

    static List<String> gradeLabels(String cardType, int maxLevel) {
        List<Level> ladder = gradeLadder(cardType);
        int count = Math.max(1, Math.min(maxLevel, ladder.size()));
        return ladder.subList(0, count).stream().map(Level::name).toList();
    }

    static int levelIndex(Level level, String cardType) {
        List<Level> ladder = gradeLadder(cardType);
        int idx = ladder.indexOf(level);
        if (idx >= 0) {
            return idx + 1;
        }
        return Math.max(1, Math.min(level == null ? 1 : level.ordinal() + 1, ladder.size()));
    }

    static int maxLevel(ScoreSkill skill) {
        int valueCount = skill.getEffects().stream()
                .map(ScoreEffect::getValues)
                .mapToInt(values -> values == null || values.isBlank() ? 1 : values.split("/").length)
                .max()
                .orElse(1);
        return Math.max(1, Math.min(valueCount, gradeLadder(skill.getCardType()).size()));
    }

    static boolean matchesPosition(String skillPosition, String requestedPosition) {
        String skill = normalize(skillPosition);
        String requested = normalize(requestedPosition);
        if (skill.isEmpty() || requested.isEmpty()) {
            return false;
        }
        if (skill.equals(requested)) {
            return true;
        }
        Set<String> skillTokens = splitPositionTokens(skill);
        if ("BATTER".equals(requested)) {
            return skillTokens.stream().anyMatch(BATTER_POSITIONS::contains);
        }
        if ("PITCHER".equals(requested)) {
            return skillTokens.stream().anyMatch(PITCHER_POSITIONS::contains);
        }
        if ("OF".equals(requested)) {
            return skillTokens.stream().anyMatch(token -> Set.of("OF", "LF", "CF", "RF").contains(token));
        }
        if ("IF".equals(requested)) {
            return skillTokens.stream().anyMatch(token -> "IF".equals(token) || INFIELD_POSITIONS.contains(token));
        }
        if (Set.of("LF", "CF", "RF").contains(requested) && skillTokens.contains("OF")) {
            return true;
        }
        if (INFIELD_POSITIONS.contains(requested) && skillTokens.contains("IF")) {
            return true;
        }
        if (PITCHER_POSITIONS.contains(requested) && skillTokens.contains("PITCHER")) {
            return true;
        }
        if (BATTER_POSITIONS.contains(requested) && skillTokens.contains("BATTER")) {
            return true;
        }
        return skillTokens.contains(requested);
    }

    static String normalizePosition(String position) {
        return normalize(position);
    }

    static String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    static String roleForPosition(String position) {
        String normalized = normalize(position);
        if ("CP".equals(normalized)) {
            return "CP";
        }
        if ("RP".equals(normalized)) {
            return "RP";
        }
        if ("SP".equals(normalized) || "PITCHER".equals(normalized)) {
            return "SP";
        }
        return "BATTER";
    }

    private static Set<String> splitPositionTokens(String position) {
        String normalized = position.replace("CR", "CF").replace(".", ",");
        String[] tokens = normalized.split("[,/|\s]+");
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                result.add(token.trim());
            }
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
