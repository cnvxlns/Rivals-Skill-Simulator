package com.example.skillsim.service;

import com.example.skillsim.config.ScoreDataLoader;
import com.example.skillsim.dto.RollRequest;
import com.example.skillsim.dto.RollResponse;
import com.example.skillsim.dto.SkillDto;
import com.example.skillsim.dto.SkillSlot;
import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.TicketType;
import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.ScoreSkill;
import com.example.skillsim.repository.ScoreSkillRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class SkillService {

    private static final Map<Level, Double> MOMENT_GRADE_WEIGHTS = Map.of(Level.S, 100.0);
    private static final Map<Level, Double> BLACK_GRADE_WEIGHTS = Map.of(
            Level.D, 40.0D,
            Level.C, 30.0D,
            Level.B, 20.0D,
            Level.A, 7.0D,
            Level.S, 3.0D
    );

    private final ScoreSkillRepository scoreSkillRepository;
    private final ScoreCalculator scoreCalculator;
    private final Supplier<Map<String, Double>> statWeightsSupplier;

    @Autowired
    public SkillService(
            ScoreSkillRepository scoreSkillRepository,
            ScoreCalculator scoreCalculator,
            ScoreDataLoader scoreDataLoader
    ) {
        this(scoreSkillRepository, scoreCalculator, scoreDataLoader::getStatWeights);
    }

    SkillService(
            ScoreSkillRepository scoreSkillRepository,
            ScoreCalculator scoreCalculator,
            Supplier<Map<String, Double>> statWeightsSupplier
    ) {
        this.scoreSkillRepository = scoreSkillRepository;
        this.scoreCalculator = scoreCalculator;
        this.statWeightsSupplier = statWeightsSupplier;
    }

    public RollResponse rollSkills(RollRequest request) {
        validateLockRules(request);

        String cardType = normalizeCardType(request.getCardType());
        int slotCount = SkillRules.slotCount(cardType);
        String selectedTheme = request.getSelectedTheme();
        List<Level> normalizedLevels = normalizeGrades(request.getCurrentLevels(), slotCount, cardType);
        List<Long> normalizedSkillIds = normalizeSkillIds(request.getCurrentSkillIds(), slotCount);
        List<Boolean> protectionFlags = normalizeProtectionFlags(request.getUseLevelProtectionSlots(), slotCount);

        Set<Integer> lockedIndices = new HashSet<>(Optional.ofNullable(request.getLockedSlots()).orElse(List.of()));
        lockedIndices.removeIf(idx -> idx == null || idx < 0 || idx >= slotCount);

        TicketType ticketType = request.getTicketType();
        ProbabilityTable probabilityTable = ProbabilityTable.fromTicket(ticketType);
        String position = normalizeRequired(request.getPosition(), "Position selection is required.");
        String subPosition = normalizeSubPosition(request.getSubPosition());

        if ("MOMENT".equals(cardType) && ticketType == TicketType.SUPREME_SKILL_CHANGE
                && (selectedTheme == null || selectedTheme.trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected theme is required for MOMENT cards.");
        }

        Set<Long> usedSkillIds = new HashSet<>();
        List<SkillSlot> slots = Arrays.asList(new SkillSlot[slotCount]);

        for (int i = 0; i < slotCount; i++) {
            if (lockedIndices.contains(i)) {
                ScoreSkill lockedSkill = resolveSkill(normalizedSkillIds.get(i));
                Level lockedLevel = normalizedLevels.get(i);
                slots.set(i, buildSlot(lockedSkill, lockedLevel, position));
                if (lockedSkill != null && lockedSkill.getId() != null) {
                    usedSkillIds.add(lockedSkill.getId());
                }
            }
        }

        boolean blackTierAlreadyRolled = slots.stream()
                .filter(Objects::nonNull)
                .map(SkillSlot::getSkill)
                .filter(Objects::nonNull)
                .anyMatch(skill -> skill.getTier() == Tier.BLACK);
        Integer forcedBlackSlot = null;
        if (Set.of("BLACK", "WBC_BLACK").contains(cardType)
                && ticketType == TicketType.SUPREME_SKILL_CHANGE
                && !blackTierAlreadyRolled) {
            List<Integer> availableSlots = IntStream.range(0, slotCount)
                    .filter(i -> slots.get(i) == null)
                    .boxed()
                    .toList();
            if (!availableSlots.isEmpty()) {
                forcedBlackSlot = availableSlots.get(ThreadLocalRandom.current().nextInt(availableSlots.size()));
            }
        }

        for (int i = 0; i < slotCount; i++) {
            if (slots.get(i) != null) {
                continue;
            }

            SkillSlot rolledSlot;
            if ("MOMENT".equals(cardType) && ticketType == TicketType.SUPREME_SKILL_CHANGE && i == 0) {
                rolledSlot = rollMomentSlotOne(selectedTheme, cardType, position, subPosition, normalizedLevels.get(i), usedSkillIds);
            } else if ("HOF".equals(cardType)) {
                rolledSlot = rollHofSlot(i, ticketType, cardType, normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
            } else if ("BLACK".equals(cardType)) {
                if (ticketType == TicketType.SUPREME_SKILL_CHANGE) {
                    rolledSlot = Objects.equals(forcedBlackSlot, i)
                            ? rollBlackTierSlot(normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition)
                            : rollSignatureBlackSupremeNormalSlot(i, normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
                } else if (!blackTierAlreadyRolled && ThreadLocalRandom.current().nextDouble(100.0) < 5.0) {
                    rolledSlot = rollBlackTierSlot(normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
                } else {
                    rolledSlot = rollNormalTierSlot(probabilityTable, ticketType, i, normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
                }
            } else if ("WBC_BLACK".equals(cardType)) {
                rolledSlot = rollWbcSignatureBlackSlot(
                        ticketType,
                        Objects.equals(forcedBlackSlot, i),
                        blackTierAlreadyRolled,
                        normalizedLevels.get(i),
                        protectionFlags.get(i),
                        usedSkillIds,
                        position,
                        subPosition
                );
            } else if ("WBC".equals(cardType)) {
                rolledSlot = rollWbcSlot(ticketType, normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
            } else if ("MOMENT".equals(cardType)) {
                rolledSlot = rollNormalTierSlot(probabilityTable, ticketType, i, normalizedLevels.get(i), true, usedSkillIds, position, subPosition);
            } else {
                rolledSlot = rollNormalTierSlot(probabilityTable, ticketType, i, normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
            }

            slots.set(i, rolledSlot);
            if (rolledSlot.getSkill() != null && rolledSlot.getSkill().getId() != null) {
                usedSkillIds.add(rolledSlot.getSkill().getId());
            }
            if (rolledSlot.getSkill() != null && rolledSlot.getSkill().getTier() == Tier.BLACK) {
                blackTierAlreadyRolled = true;
            }
        }

        return buildResponse(slots);
    }

    public List<String> getMomentThemeNames(String position, String subPosition) {
        String normalizedPosition = normalizeRequired(position, "Position selection is required.");
        String normalizedSubPosition = normalizeSubPosition(subPosition);

        return candidates("MOMENT", normalizedPosition, normalizedSubPosition).stream()
                .map(ScoreSkill::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
    }

    public RollResponse initialSlots(String cardType, String position, String subPosition) {
        String normalizedCardType;
        try {
            normalizedCardType = SkillRules.normalizeCardType(cardType);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        String normalizedPosition = normalizeRequired(position, "Position selection is required.");
        String normalizedSubPosition = normalizeSubPosition(subPosition);
        Set<Long> usedSkillIds = new HashSet<>();
        int slotCount = SkillRules.slotCount(normalizedCardType);
        List<SkillSlot> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            SkillSlot slot = buildInitialSlot(normalizedCardType, i, slotCount, usedSkillIds, normalizedPosition, normalizedSubPosition);
            slots.add(slot);
            if (slot.getSkill() != null && slot.getSkill().getId() != null) {
                usedSkillIds.add(slot.getSkill().getId());
            }
        }
        return buildResponse(slots);
    }

    /**
     * 선수 영입(초기 슬롯) 규칙.
     * - SIGNATURE(NORMAL)/MOMENT/HOF: 슬롯별 독립 80% 아이언 / 20% 브론즈, D 레벨.
     * - WBC: 전 슬롯 S 레벨 랜덤 골드.
     * - SIGNATURE_BLACK(BLACK): 1~3 슬롯 S 레벨 랜덤 골드, 마지막(4번) 슬롯 S 레벨 랜덤 블랙.
     * - WBC_SIGNATURE_BLACK(WBC_BLACK): 1~3 슬롯 S 레벨(슬롯별 골드 90% / WBC 10%), 마지막(4번) 슬롯 S 레벨 랜덤 블랙.
     */
    private SkillSlot buildInitialSlot(String normalizedCardType, int slotIndex, int slotCount, Set<Long> usedSkillIds,
                                       String position, String subPosition) {
        boolean lastSlot = slotIndex == slotCount - 1;
        return switch (normalizedCardType) {
            case "WBC" -> buildSlot(pickSkillByTier("NORMAL", Tier.GOLD, usedSkillIds, position, subPosition), Level.S, position);
            case "BLACK" -> lastSlot
                    ? buildSlot(pickSkillByTier("BLACK", Tier.BLACK, usedSkillIds, position, subPosition), Level.S, position)
                    : buildSlot(pickSkillByTier("NORMAL", Tier.GOLD, usedSkillIds, position, subPosition), Level.S, position);
            case "WBC_BLACK" -> {
                if (lastSlot) {
                    yield buildSlot(pickSkillByTier("BLACK", Tier.BLACK, usedSkillIds, position, subPosition), Level.S, position);
                }
                boolean wbcTier = ThreadLocalRandom.current().nextDouble(100.0) < 10.0;
                ScoreSkill skill = wbcTier
                        ? pickSkillByTier("WBC", Tier.WBC, usedSkillIds, position, subPosition)
                        : pickSkillByTier("NORMAL", Tier.GOLD, usedSkillIds, position, subPosition);
                yield buildSlot(skill, Level.S, position);
            }
            default -> {
                // SIGNATURE(NORMAL)/MOMENT/HOF
                Tier tier = ThreadLocalRandom.current().nextDouble(100.0) < 80.0 ? Tier.IRON : Tier.BRONZE;
                yield buildSlot(pickSkillByTier("NORMAL", tier, usedSkillIds, position, subPosition), Level.D, position);
            }
        };
    }

    private SkillSlot rollMomentSlotOne(String selectedTheme, String cardType, String position, String subPosition,
                                        Level currentLevel, Set<Long> usedSkillIds) {
        ScoreSkill selectedSkill = findMomentSkillByName(selectedTheme, position, subPosition);
        boolean hitSelectedTheme = selectedSkill != null && ThreadLocalRandom.current().nextDouble(100) < 6.0;
        ScoreSkill skill = hitSelectedTheme
                ? selectedSkill
                : pickSkillByTier("NORMAL", Tier.GOLD, usedSkillIds, position, subPosition);
        Level level = hitSelectedTheme
                ? applyProtection(WeightedRandom.pick(MOMENT_GRADE_WEIGHTS, Level.S), currentLevel, true, cardType)
                : applyProtection(rollGrade(ProbabilityTable.SUPREME, Tier.GOLD, true, currentLevel, "NORMAL"), currentLevel, true, "NORMAL");
        return buildSlot(skill, level, position);
    }

    private SkillSlot rollNormalTierSlot(ProbabilityTable table, TicketType ticketType, int slotIndex, Level currentLevel,
                                         boolean protectionFlag, Set<Long> usedSkillIds, String position, String subPosition) {
        Tier tier = rollTier(table, ticketType, slotIndex);
        ScoreSkill skill = pickSkillByTier("NORMAL", tier, usedSkillIds, position, subPosition);
        Level level = rollGrade(table, tier, protectionFlag, currentLevel, "NORMAL");
        return buildSlot(skill, level, position);
    }

    private SkillSlot rollGoldNormalSlot(Level currentLevel, boolean protectionFlag, Set<Long> usedSkillIds,
                                         String position, String subPosition) {
        ScoreSkill skill = pickSkillByTier("NORMAL", Tier.GOLD, usedSkillIds, position, subPosition);
        Level level = rollGrade(ProbabilityTable.SUPREME, Tier.GOLD, protectionFlag, currentLevel, "NORMAL");
        return buildSlot(skill, level, position);
    }

    private SkillSlot rollSignatureBlackSupremeNormalSlot(int slotIndex, Level currentLevel, boolean protectionFlag,
                                                          Set<Long> usedSkillIds, String position, String subPosition) {
        if (slotIndex == 0) {
            return rollGoldNormalSlot(currentLevel, protectionFlag, usedSkillIds, position, subPosition);
        }
        return rollNormalTierSlot(
                ProbabilityTable.SIGNATURE_BLACK_SUPREME_OTHER,
                TicketType.SUPREME_SKILL_CHANGE,
                slotIndex,
                currentLevel,
                protectionFlag,
                usedSkillIds,
                position,
                subPosition
        );
    }

    private SkillSlot rollBlackTierSlot(Level currentLevel, boolean protectionFlag, Set<Long> usedSkillIds,
                                        String position, String subPosition) {
        ScoreSkill skill = pickSkillByTier("BLACK", Tier.BLACK, usedSkillIds, position, subPosition);
        Level level = applyProtection(WeightedRandom.pick(BLACK_GRADE_WEIGHTS, Level.S), currentLevel, protectionFlag, "BLACK");
        return buildSlot(skill, level, position);
    }

    private SkillSlot rollWbcSlot(TicketType ticketType, Level currentLevel, boolean protectionFlag, Set<Long> usedSkillIds,
                                  String position, String subPosition) {
        double wbcChance = switch (ticketType) {
            case SKILL_CHANGE -> 0.0;
            case PREMIUM_SKILL_CHANGE -> 0.5;
            case SUPREME_SKILL_CHANGE -> 10.0;
        };
        boolean rollWbcTier = ThreadLocalRandom.current().nextDouble(100.0) < wbcChance;
        ScoreSkill skill = rollWbcTier
                ? pickSkillByTier("WBC", Tier.WBC, usedSkillIds, position, subPosition)
                : pickSkillByTier("NORMAL", Tier.GOLD, usedSkillIds, position, subPosition);
        Level level = applyProtection(Level.S, currentLevel, protectionFlag, rollWbcTier ? "WBC" : "NORMAL");
        return buildSlot(skill, level, position);
    }

    private SkillSlot rollWbcSignatureBlackSlot(TicketType ticketType, boolean forceBlackSlot, boolean blackTierAlreadyRolled,
                                                Level currentLevel, boolean protectionFlag, Set<Long> usedSkillIds,
                                                String position, String subPosition) {
        if (ticketType == TicketType.SKILL_CHANGE) {
            return rollWbcSignatureBlackTierSlot("NORMAL", Tier.GOLD, currentLevel, protectionFlag, usedSkillIds, position, subPosition);
        }

        if (ticketType == TicketType.SUPREME_SKILL_CHANGE) {
            if (forceBlackSlot) {
                return rollWbcSignatureBlackTierSlot("BLACK", Tier.BLACK, currentLevel, protectionFlag, usedSkillIds, position, subPosition);
            }
            boolean rollWbcTier = ThreadLocalRandom.current().nextDouble(100.0) < 10.0;
            return rollWbcSignatureBlackTierSlot(
                    rollWbcTier ? "WBC" : "NORMAL",
                    rollWbcTier ? Tier.WBC : Tier.GOLD,
                    currentLevel,
                    protectionFlag,
                    usedSkillIds,
                    position,
                    subPosition
            );
        }

        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (!blackTierAlreadyRolled && roll < 5.0) {
            return rollWbcSignatureBlackTierSlot("BLACK", Tier.BLACK, currentLevel, protectionFlag, usedSkillIds, position, subPosition);
        }
        double wbcThreshold = blackTierAlreadyRolled ? 0.5 : 5.5;
        boolean rollWbcTier = roll < wbcThreshold;
        return rollWbcSignatureBlackTierSlot(
                rollWbcTier ? "WBC" : "NORMAL",
                rollWbcTier ? Tier.WBC : Tier.GOLD,
                currentLevel,
                protectionFlag,
                usedSkillIds,
                position,
                subPosition
        );
    }

    private SkillSlot rollWbcSignatureBlackTierSlot(String skillCardType, Tier tier, Level currentLevel,
                                                    boolean protectionFlag, Set<Long> usedSkillIds,
                                                    String position, String subPosition) {
        ScoreSkill skill = pickSkillByTier(skillCardType, tier, usedSkillIds, position, subPosition);
        Level level = applyProtection(Level.S, currentLevel, protectionFlag, "WBC_BLACK");
        return buildSlot(skill, level, position);
    }

    private SkillSlot rollHofSlot(int slotIndex, TicketType ticketType, String cardType, Level currentLevel,
                                  boolean protectionFlag, Set<Long> usedSkillIds, String position, String subPosition) {
        HofProbabilityTable table = resolveHofTable(ticketType, slotIndex);
        Tier tier = WeightedRandom.pick(table.tierWeights(), Tier.HOF);
        String candidateCardType = tier == Tier.HOF ? cardType : "NORMAL";
        ScoreSkill skill = pickSkillByTier(candidateCardType, tier, usedSkillIds, position, subPosition);
        Level level = rollGrade(table, tier, protectionFlag, currentLevel, candidateCardType);
        return buildSlot(skill, level, position);
    }

    private SkillSlot rollFixedCardTypeSlot(String cardType, Level currentLevel, boolean useProtection,
                                            Set<Long> usedSkillIds, String position, String subPosition) {
        ScoreSkill skill = pickAnySkill(cardType, usedSkillIds, position, subPosition);
        Level rolledLevel = switch (cardType) {
            case "BLACK" -> WeightedRandom.pick(BLACK_GRADE_WEIGHTS, Level.S);
            case "WBC" -> Level.S;
            case "MOMENT" -> Level.S;
            default -> SkillRules.defaultLevel(cardType);
        };
        Level level = applyProtection(rolledLevel, currentLevel, useProtection, cardType);
        return buildSlot(skill, level, position);
    }

    private ScoreSkill pickSkillByTier(String cardType, Tier tier, Set<Long> excludedIds, String position, String subPosition) {
        List<ScoreSkill> availableSkills = candidates(cardType, position, subPosition).stream()
                .filter(skill -> skill.getId() == null || !excludedIds.contains(skill.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (availableSkills.isEmpty()) {
            return null;
        }

        List<ScoreSkill> tierMatches = availableSkills.stream()
                .filter(skill -> SkillRules.rollTier(skill) == tier)
                .toList();
        return pickWeightedSkill(tierMatches.isEmpty() ? availableSkills : tierMatches);
    }

    private ScoreSkill pickAnySkill(String cardType, Set<Long> excludedIds, String position, String subPosition) {
        List<ScoreSkill> availableSkills = candidates(cardType, position, subPosition).stream()
                .filter(skill -> skill.getId() == null || !excludedIds.contains(skill.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        return pickWeightedSkill(availableSkills);
    }

    private List<ScoreSkill> candidates(String cardType, String position, String subPosition) {
        return scoreSkillRepository.findByCardTypeIgnoreCase(SkillRules.normalizeCardType(cardType)).stream()
                .filter(skill -> SkillRules.matchesPosition(skill.getPosition(), position))
                .filter(skill -> SkillRules.matchesSubPosition(skill, subPosition))
                .toList();
    }

    private ScoreSkill pickWeightedSkill(List<ScoreSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        int total = skills.stream().mapToInt(SkillRules::rollWeight).sum();
        if (total <= 0) {
            return skills.get(ThreadLocalRandom.current().nextInt(skills.size()));
        }

        int roll = ThreadLocalRandom.current().nextInt(total) + 1;
        int cumulative = 0;
        for (ScoreSkill skill : skills) {
            cumulative += SkillRules.rollWeight(skill);
            if (roll <= cumulative) {
                return skill;
            }
        }
        return skills.get(0);
    }

    private SkillSlot buildSlot(ScoreSkill skill, Level level, String position) {
        if (skill == null) {
            return SkillSlot.builder()
                    .skill(null)
                    .level(level)
                    .score(0.0)
                    .build();
        }

        int scoreLevel = SkillRules.levelIndex(level, skill.getCardType());
        ScoreCalculator.Result score = scoreCalculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, scoreLevel)),
                statWeightsSupplier.get(),
                ScoreCalculator.conditionProbabilitiesForPosition(position),
                Map.of()
        );

        return SkillSlot.builder()
                .skill(SkillDto.from(skill, SkillRules.rollTier(skill)))
                .level(level)
                .score(score.total())
                .build();
    }

    private RollResponse buildResponse(List<SkillSlot> slots) {
        double total = slots.stream()
                .filter(Objects::nonNull)
                .mapToDouble(SkillSlot::getScore)
                .sum();
        return RollResponse.builder()
                .slots(slots)
                .totalScore(Math.round(total * 100.0) / 100.0)
                .build();
    }

    private ScoreSkill findMomentSkillByName(String selectedTheme, String position, String subPosition) {
        if (selectedTheme == null || selectedTheme.trim().isEmpty()) {
            return null;
        }
        return candidates("MOMENT", position, subPosition).stream()
                .filter(skill -> skill.getName() != null && skill.getName().equalsIgnoreCase(selectedTheme.trim()))
                .findFirst()
                .orElse(null);
    }

    private void validateLockRules(RollRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Roll request is required.");
        }
        List<Integer> lockedSlots = Optional.ofNullable(request.getLockedSlots()).orElse(List.of());
        if (!lockedSlots.contains(0)) {
            return;
        }

        CardType cardType = request.getCardType();
        switch (cardType) {
            case SIGNATURE, WBC -> { }
            case MOMENT -> {
                ScoreSkill slotOneSkill = resolveSkill(firstSkillId(request.getCurrentSkillIds()));
                if (slotOneSkill == null || SkillRules.rollTier(slotOneSkill) != Tier.MOMENT) {
                    throw new IllegalArgumentException("Slot 1 lock for MOMENT is only allowed when current slot 1 is MOMENT tier.");
                }
            }
            case SIGNATURE_BLACK, WBC_SIGNATURE_BLACK, HOF ->
                    throw new IllegalArgumentException("Slot 1 lock not allowed for this card type: " + cardType);
            default -> throw new IllegalArgumentException("Unsupported card type: " + cardType);
        }
    }

    private Tier rollTier(ProbabilityTable table, TicketType ticketType, int slotIndex) {
        if (ticketType == TicketType.SUPREME_SKILL_CHANGE && slotIndex == 0) {
            return Tier.GOLD;
        }
        return WeightedRandom.pick(table.tierWeights(), Tier.BRONZE);
    }

    private Level rollGrade(ProbabilityTable table, Tier tier, boolean useProtection, Level currentLevel, String cardType) {
        Level defaultLevel = currentLevel != null ? currentLevel : SkillRules.defaultLevel(cardType);
        Level rolledLevel = WeightedRandom.pick(table.gradeWeights(tier), defaultLevel);
        return applyProtection(rolledLevel, currentLevel, useProtection, cardType);
    }

    private Level rollGrade(HofProbabilityTable table, Tier tier, boolean useProtection, Level currentLevel, String cardType) {
        Level defaultLevel = currentLevel != null ? currentLevel : SkillRules.defaultLevel(cardType);
        Level rolledLevel = WeightedRandom.pick(table.gradeWeights(tier), defaultLevel);
        return applyProtection(rolledLevel, currentLevel, useProtection, cardType);
    }

    private Level applyProtection(Level rolledLevel, Level currentLevel, boolean useProtection, String cardType) {
        Level safeRolled = coerceToLadder(rolledLevel, cardType);
        Level safeCurrent = coerceToLadder(currentLevel, cardType);
        if (useProtection && safeCurrent != null
                && SkillRules.gradeLadder(cardType).indexOf(safeRolled) < SkillRules.gradeLadder(cardType).indexOf(safeCurrent)) {
            return safeCurrent;
        }
        return safeRolled;
    }

    private Level coerceToLadder(Level level, String cardType) {
        List<Level> ladder = SkillRules.gradeLadder(cardType);
        if (level != null && ladder.contains(level)) {
            return level;
        }
        if (level == null) {
            return ladder.get(0);
        }
        return ladder.get(Math.max(0, Math.min(level.ordinal(), ladder.size() - 1)));
    }

    private HofProbabilityTable resolveHofTable(TicketType ticketType, int slotIndex) {
        return switch (ticketType) {
            case SUPREME_SKILL_CHANGE -> (slotIndex == 0)
                    ? HofProbabilityTable.supremeSlotOne()
                    : HofProbabilityTable.supremeOtherSlots();
            case PREMIUM_SKILL_CHANGE -> HofProbabilityTable.advanced();
            case SKILL_CHANGE -> HofProbabilityTable.skillChange();
        };
    }

    private List<Level> normalizeGrades(List<Level> list, int slotCount, String cardType) {
        List<Level> normalized = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            Level value = (list != null && i < list.size()) ? list.get(i) : SkillRules.defaultLevel(cardType);
            normalized.add(coerceToLadder(value, cardType));
        }
        return normalized;
    }

    private List<Long> normalizeSkillIds(List<Long> list, int slotCount) {
        List<Long> normalized = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            normalized.add((list != null && i < list.size()) ? list.get(i) : null);
        }
        return normalized;
    }

    private List<Boolean> normalizeProtectionFlags(List<Boolean> list, int slotCount) {
        List<Boolean> normalized = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            normalized.add((list != null && i < list.size() && list.get(i) != null) ? list.get(i) : false);
        }
        return normalized;
    }

    private Long firstSkillId(List<Long> skillIds) {
        return (skillIds != null && !skillIds.isEmpty()) ? skillIds.get(0) : null;
    }

    private ScoreSkill resolveSkill(Long id) {
        if (id == null) {
            return null;
        }
        return scoreSkillRepository.findById(id).orElse(null);
    }

    private String normalizeCardType(CardType cardType) {
        try {
            return SkillRules.normalizeCardType(cardType);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private String normalizeRequired(String value, String message) {
        try {
            return SkillRules.normalizeRequired(value, message);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String normalizeSubPosition(String subPosition) {
        String normalized = SkillRules.normalizePosition(subPosition);
        return normalized.isEmpty() || "ALL".equals(normalized) ? null : normalized;
    }
}
