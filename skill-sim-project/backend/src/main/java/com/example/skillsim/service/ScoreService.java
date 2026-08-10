package com.example.skillsim.service;

import com.example.skillsim.config.ScoreDataLoader;
import com.example.skillsim.dto.ScoreRequest;
import com.example.skillsim.dto.ScoreResponse;
import com.example.skillsim.dto.ScoreSelection;
import com.example.skillsim.dto.ScoreSkillOption;
import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import com.example.skillsim.repository.ScoreSkillRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScoreService {

    private final ScoreSkillRepository scoreSkillRepository;
    private final ScoreCalculator scoreCalculator;
    private final Supplier<Map<String, Double>> statWeightsSupplier;

    @Autowired
    public ScoreService(
            ScoreSkillRepository scoreSkillRepository,
            ScoreCalculator scoreCalculator,
            ScoreDataLoader scoreDataLoader
    ) {
        this(scoreSkillRepository, scoreCalculator, scoreDataLoader::getStatWeights);
    }

    ScoreService(
            ScoreSkillRepository scoreSkillRepository,
            ScoreCalculator scoreCalculator,
            Map<String, Double> statWeights
    ) {
        this(scoreSkillRepository, scoreCalculator, () -> statWeights);
    }

    private ScoreService(
            ScoreSkillRepository scoreSkillRepository,
            ScoreCalculator scoreCalculator,
            Supplier<Map<String, Double>> statWeightsSupplier
    ) {
        this.scoreSkillRepository = scoreSkillRepository;
        this.scoreCalculator = scoreCalculator;
        this.statWeightsSupplier = statWeightsSupplier;
    }

    public List<ScoreSkillOption> listSkills(String cardType, String position) {
        String normalizedCardType = normalizeCardTypeOrThrow(cardType);
        String normalizedPosition = normalizeRequiredOrThrow(position, "Position selection is required.");

        return scoreSkillsForCardType(normalizedCardType).stream()
                .filter(skill -> SkillRules.matchesPosition(skill.getPosition(), normalizedPosition))
                .map(this::toOption)
                .toList();
    }

    public ScoreResponse calculate(ScoreRequest request) {
        if (request == null) {
            throw badRequest("Score request is required.");
        }

        String normalizedCardType = normalizeCardTypeOrThrow(request.getCardType());
        String normalizedPosition = normalizeRequiredOrThrow(request.getPosition(), "Position selection is required.");
        List<ScoreSelection> selections = request.getSelections();
        validateSelections(selections, normalizedCardType);

        String role = SkillRules.roleForPosition(normalizedPosition);
        Integer battingOrder = null;
        Integer pitcherSlot = request.getPitcherSlot();
        if ("BATTER".equals(role)) {
            battingOrder = validateBattingOrder(request.getBattingOrder());
        } else if ("SP".equals(role)) {
            if (pitcherSlot == null || pitcherSlot < 1 || pitcherSlot > 5) {
                throw badRequest("Pitcher slot must be between 1 and 5 for starting pitchers.");
            }
        } else if ("RP".equals(role)) {
            if (pitcherSlot == null || pitcherSlot < 1 || pitcherSlot > 6) {
                throw badRequest("Pitcher slot must be between 1 and 6 for relief pitchers.");
            }
        }
        Set<String> seenSkillIds = new HashSet<>();
        List<ScoreCalculator.Selection> calculatorSelections = new ArrayList<>();

        for (ScoreSelection selection : selections) {
            String skillId = normalizeRequiredOrThrow(selection.getSkillId(), "Skill selection is required.");
            if (!seenSkillIds.add(skillId)) {
                throw badRequest("Duplicate skill selection is not allowed.");
            }

            ScoreSkill skill = scoreSkillRepository.findBySkillKey(skillId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Score skill not found: " + skillId));
            if (!allowedSkillCardTypes(normalizedCardType).contains(normalizeCardTypeOrThrow(skill.getCardType()))) {
                throw badRequest("Selected skill does not match requested card type.");
            }
            if (!SkillRules.matchesPosition(skill.getPosition(), normalizedPosition)) {
                throw badRequest("Selected skill does not match requested position.");
            }
            int maxLevel = maxLevel(skill);
            if (selection.getLevel() == null || selection.getLevel() < 1 || selection.getLevel() > maxLevel) {
                throw badRequest("Skill level must be between 1 and " + maxLevel + ".");
            }

            calculatorSelections.add(new ScoreCalculator.Selection(skill, selection.getLevel()));
        }

        Map<String, Double> conditionProbabilities = ScoreCalculator.conditionProbabilitiesForPosition(
                normalizedPosition,
                battingOrder,
                pitcherSlot,
                normalizedCardType,
                request.getThrowHand(),
                request.getBatHand()
        );
        UndefinedConditionWarnings undefinedConditionWarnings = applyUndefinedConditionWarnings(
                calculatorSelections,
                conditionProbabilities
        );

        ScoreCalculator.Result result = scoreCalculator.calculate(
                calculatorSelections,
                statWeightsSupplier.get(),
                conditionProbabilities,
                request.getUserStats()
        );
        return toResponse(result, undefinedConditionWarnings);
    }

    private Integer validateBattingOrder(Integer battingOrder) {
        if (battingOrder == null) {
            throw badRequest("Batting order is required for batters.");
        }
        if (battingOrder < 1 || battingOrder > 9) {
            throw badRequest("Batting order must be between 1 and 9.");
        }
        return battingOrder;
    }

    private void validateSelections(List<ScoreSelection> selections, String cardType) {
        if (selections == null || selections.isEmpty()) {
            throw badRequest("At least one skill selection is required.");
        }
        int slotCount = SkillRules.slotCount(cardType);
        if (selections.size() > slotCount) {
            throw badRequest("Too many skill selections for card type " + cardType + ".");
        }
        Set<String> seenSkillIds = new HashSet<>();
        for (ScoreSelection selection : selections) {
            if (selection == null) {
                throw badRequest("Skill selection is required.");
            }
            String skillId = normalizeRequiredOrThrow(selection.getSkillId(), "Skill selection is required.");
            if (!seenSkillIds.add(skillId)) {
                throw badRequest("Duplicate skill selection is not allowed.");
            }
            if (selection.getLevel() == null || selection.getLevel() < 1) {
                throw badRequest("Skill level must be at least 1.");
            }
        }
    }

    private ScoreSkillOption toOption(ScoreSkill skill) {
        return ScoreSkillOption.builder()
                .skillId(skill.getSkillKey())
                .cardType(skill.getCardType())
                .position(skill.getPosition())
                .name(skill.getName())
                .description(skill.getDescription())
                .maxLevel(maxLevel(skill))
                .levelLabels(SkillRules.gradeLabels(skill.getCardType(), maxLevel(skill)))
                .build();
    }

    private List<ScoreSkill> scoreSkillsForCardType(String cardType) {
        return allowedSkillCardTypes(cardType).stream()
                .flatMap(type -> scoreSkillRepository.findByCardTypeIgnoreCase(type).stream())
                .toList();
    }

    private List<String> allowedSkillCardTypes(String cardType) {
        return switch (cardType) {
            case "BLACK" -> List.of("NORMAL", "BLACK");
            case "WBC" -> List.of("NORMAL", "WBC");
            case "WBC_BLACK" -> List.of("NORMAL", "WBC", "BLACK");
            case "MOMENT" -> List.of("NORMAL", "MOMENT");
            case "SUPREME_MOMENT" -> List.of("NORMAL", "MOMENT");
            case "HOF" -> List.of("NORMAL", "HOF");
            default -> List.of(cardType);
        };
    }

    private int maxLevel(ScoreSkill skill) {
        return SkillRules.maxLevel(skill);
    }

    private UndefinedConditionWarnings applyUndefinedConditionWarnings(
            List<ScoreCalculator.Selection> selections,
            Map<String, Double> conditionProbabilities
    ) {
        List<String> globalWarnings = new ArrayList<>();
        Map<String, List<String>> perSkillWarnings = new HashMap<>();
        Set<String> seenWarnings = new HashSet<>();

        for (ScoreCalculator.Selection selection : selections) {
            ScoreSkill skill = selection.skill();
            for (ScoreEffect effect : skill.getEffects()) {
                for (String token : conditionTokens(effect.getCondition())) {
                    if (conditionProbabilities.containsKey(token)) {
                        continue;
                    }
                    conditionProbabilities.put(token, 0.0);
                    String skillKey = skill.getSkillKey();
                    String globalWarning = skillKey + " contains undefined condition: " + token;
                    if (seenWarnings.add(globalWarning)) {
                        globalWarnings.add(globalWarning);
                        perSkillWarnings
                                .computeIfAbsent(skillKey, ignored -> new ArrayList<>())
                                .add("Undefined condition: " + token);
                    }
                }
            }
        }

        return new UndefinedConditionWarnings(globalWarnings, perSkillWarnings);
    }

    private List<String> conditionTokens(String condition) {
        if (condition == null || condition.isBlank() || condition.equalsIgnoreCase("ALWAYS")) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : condition.split("\\+")) {
            String token = part.trim();
            if (!token.isEmpty() && !"ALWAYS".equalsIgnoreCase(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private ScoreResponse toResponse(ScoreCalculator.Result result, UndefinedConditionWarnings undefinedConditionWarnings) {
        return ScoreResponse.builder()
                .total(result.total())
                .perSkill(result.perSkill().stream()
                        .map(skill -> ScoreResponse.SkillScore.builder()
                                .skillId(skill.skillKey())
                                .name(skill.name())
                                .score(skill.score())
                                .perStat(toStatScores(skill.perStat()))
                                .breakdown(skill.breakdown().stream()
                                        .map(this::toEffectBreakdown)
                                        .toList())
                                .warnings(undefinedConditionWarnings.perSkillWarnings()
                                        .getOrDefault(skill.skillKey(), List.of()))
                                .build())
                        .toList())
                .perStat(toStatScores(result.perStat()))
                .warnings(undefinedConditionWarnings.globalWarnings())
                .build();
    }

    private ScoreResponse.EffectBreakdown toEffectBreakdown(ScoreCalculator.EffectBreakdown breakdown) {
        return ScoreResponse.EffectBreakdown.builder()
                .stat(breakdown.stat())
                .condition(breakdown.condition())
                .weight(breakdown.weight())
                .value(breakdown.value())
                .conditionProbability(breakdown.conditionProbability())
                .subtotal(breakdown.subtotal())
                .baseStat(breakdown.baseStat())
                .baseValue(breakdown.baseValue())
                .rawValue(breakdown.rawValue())
                .build();
    }

    private List<ScoreResponse.StatScore> toStatScores(Map<String, Double> perStat) {
        return perStat.entrySet().stream()
                .map(entry -> ScoreResponse.StatScore.builder()
                        .stat(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    private String normalizeCardTypeOrThrow(String cardType) {
        try {
            return SkillRules.normalizeCardType(cardType);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private String normalizeRequiredOrThrow(String value, String message) {
        try {
            return SkillRules.normalizeRequired(value, message);
        } catch (IllegalArgumentException ex) {
            throw badRequest(message);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record UndefinedConditionWarnings(
            List<String> globalWarnings,
            Map<String, List<String>> perSkillWarnings
    ) {
    }
}
