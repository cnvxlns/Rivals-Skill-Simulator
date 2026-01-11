package com.example.skillsim.service;

import com.example.skillsim.dto.RollRequest;
import com.example.skillsim.dto.RollResponse;
import com.example.skillsim.dto.SkillDto;
import com.example.skillsim.dto.SkillSlot;
import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.TicketType;
import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
import com.example.skillsim.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private static final int DEFAULT_SLOT_COUNT = 3;
    private static final Map<Level, Double> MOMENT_GOLD_GRADE_WEIGHTS = Map.of(
            Level.D, 37.60D,
            Level.C, 28.20D,
            Level.B, 18.80D,
            Level.A, 6.58D,
            Level.S, 2.82D
    );
    private static final Map<Level, Double> BLACK_GRADE_WEIGHTS = Map.of(
            Level.D, 40.0D,
            Level.C, 30.0D,
            Level.B, 20.0D,
            Level.A, 7.0D,
            Level.S, 3.0D
    );
    private static final Map<Tier, Double> SIGNATURE_BLACK_NON_BLACK_TIER_WEIGHTS = Map.of(
            Tier.GOLD, 40.0D,
            Tier.SILVER, 20.0D,
            Tier.BRONZE, 12.0D,
            Tier.IRON, 23.0D
    );

    private final SkillRepository skillRepository;

    public RollResponse rollSkills(RollRequest request) {
        validateLockRules(request);

        boolean isMomentCard = request.getCardType() == CardType.MOMENT;
        boolean isHofCard = request.getCardType() == CardType.HOF;
        boolean isSignatureBlack = request.getCardType() == CardType.SIGNATURE_BLACK;
        int slotCount = resolveSlotCount(request.getCardType());
        String selectedTheme = request.getSelectedTheme();
        List<Level> normalizedLevels = normalizeGrades(request.getCurrentLevels(), slotCount);
        List<Long> normalizedSkillIds = normalizeSkillIds(request.getCurrentSkillIds(), slotCount);
        List<Boolean> protectionFlags = normalizeProtectionFlags(request.getUseLevelProtectionSlots(), slotCount);

        // 잠금 요청된 슬롯 인덱스 (null 방지)
        Set<Integer> lockedIndices = new HashSet<>(Optional.ofNullable(request.getLockedSlots()).orElse(List.of()));
        lockedIndices.removeIf(idx -> idx == null || idx < 0 || idx >= slotCount);

        TicketType ticketType = request.getTicketType();
        ProbabilityTable probabilityTable = ProbabilityTable.fromTicket(ticketType);
        String position = normalizePosition(request.getPosition());
        String subPosition = normalizeSubPosition(request.getSubPosition());
        validatePositionRequired(position);
        if (isMomentCard && ticketType == TicketType.SUPREME_SKILL_CHANGE
                && (selectedTheme == null || selectedTheme.trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected theme is required for MOMENT cards.");
        }

        // [중복 방지 핵심] 이번 롤에서 확정된 스킬 ID들을 저장하는 공간
        Set<Long> usedSkillIds = new HashSet<>();

        // 결과 슬롯을 담을 리스트 (크기 3으로 초기화)
        List<SkillSlot> slots = Arrays.asList(new SkillSlot[slotCount]);

        // ==========================================
        // STEP 1: 잠금(Lock) 슬롯 먼저 확정 짓기
        // ==========================================
        for (int i = 0; i < slotCount; i++) {
            if (lockedIndices.contains(i)) {
                // 기존 스킬 유지
                Skill lockedSkill = resolveSkill(normalizedSkillIds.get(i));
                Level lockedLevel = normalizedLevels.get(i);

                slots.set(i, buildSlot(lockedSkill, lockedLevel));

                // 잠긴 스킬도 중복 방지 목록에 등록 (다른 슬롯에서 나오면 안 되니까)
                if (lockedSkill != null && lockedSkill.getId() != null) {
                    usedSkillIds.add(lockedSkill.getId());
                }
            }
        }

        // ==========================================
        // STEP 2: 시그니처 블랙 전용 로직 (최고급권)
        // ==========================================
        if (isSignatureBlack && ticketType == TicketType.SUPREME_SKILL_CHANGE) {
            rollSignatureBlackSupreme(slotCount, slots, normalizedLevels, protectionFlags, usedSkillIds, lockedIndices, position, subPosition);
            return RollResponse.builder().slots(slots).build();
        }

        // ==========================================
        // STEP 3: 나머지 슬롯 랜덤 뽑기 (일반 카드)
        // ==========================================
        for (int i = 0; i < slotCount; i++) {
            // 이미 채워진(잠긴) 슬롯은 패스
            if (slots.get(i) != null) {
                continue;
            }

            SkillSlot rolledSlot;
            if (isMomentCard && ticketType == TicketType.SUPREME_SKILL_CHANGE && i == 0) {
                rolledSlot = rollMomentSlotOne(selectedTheme, position, subPosition, normalizedLevels.get(i), usedSkillIds);
            } else if (isHofCard) {
                rolledSlot = rollHofSlot(i, ticketType, normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
            } else {
                // 1. 티어 결정 (최고급권 1슬롯 골드 보장 로직 포함)
                Tier tier = rollTier(probabilityTable, ticketType, i);

                // 2. 스킬 결정 (중복 방지 적용)
                Skill skill = pickSkillByTier(tier, usedSkillIds, position, subPosition);

                // 3. 등급 결정 (Moment 카드는 자동 보호 적용)
                boolean effectiveProtection = isMomentCard || protectionFlags.get(i);
                Level level = rollGrade(probabilityTable, tier, effectiveProtection, normalizedLevels.get(i));

                rolledSlot = buildSlot(skill, level);
            }

            slots.set(i, rolledSlot);

            // 뽑힌 스킬 ID 등록 (다음 루프에서 중복 안 나오게)
            SkillDto rolledSkill = rolledSlot.getSkill();
            if (rolledSkill != null && rolledSkill.getId() != null) {
                usedSkillIds.add(rolledSkill.getId());
            }
        }

        return RollResponse.builder().slots(slots).build();
    }

    /**
     * 티어와 포지션에 맞는 스킬을 가져오되, 이미 사용된(excludedIds) 스킬은 후보군에서 배제한다.
     */
    private Skill pickSkillByTier(Tier tier, Set<Long> excludedIds, String position, String subPosition) {
        List<Skill> candidates = skillRepository.findByTierAndPositionIgnoreCase(tier, position);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<Skill> availableSkills = candidates.stream()
                .filter(s -> s.getId() != null && !excludedIds.contains(s.getId()))
                .filter(s -> matchesSubPosition(s, subPosition))
                .collect(Collectors.toList());

        if (availableSkills.isEmpty()) {
            return null;
        }

        return pickWeightedSkill(availableSkills);
    }

    private SkillSlot rollMomentSlotOne(String selectedTheme, String position, String subPosition,
                                        Level currentLevel, Set<Long> usedSkillIds) {
        Skill exclusiveSkill = findMomentSkillByName(selectedTheme, position, subPosition);
        if (exclusiveSkill == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected theme was not found for the chosen position.");
        }
        boolean hitExclusive = ThreadLocalRandom.current().nextDouble(100) < 6.0;

        if (hitExclusive) {
            return buildSlot(exclusiveSkill, Level.S);
        }

        Skill goldSkill = pickSkillByTier(Tier.GOLD, usedSkillIds, position, subPosition);
        Level level = rollMomentGoldGrade(currentLevel);
        return buildSlot(goldSkill, level);
    }

    private Level rollMomentGoldGrade(Level currentLevel) {
        Level rolledLevel = WeightedRandom.pick(MOMENT_GOLD_GRADE_WEIGHTS, Level.D);
        return applyProtection(rolledLevel, currentLevel, true);
    }

    private SkillSlot rollHofSlot(int slotIndex, TicketType ticketType, Level currentLevel, boolean protectionFlag,
                                  Set<Long> usedSkillIds, String position, String subPosition) {
        HofProbabilityTable table = resolveHofTable(ticketType, slotIndex);
        Tier tier = WeightedRandom.pick(table.tierWeights(), Tier.GOLD);
        Skill skill = pickSkillByTier(tier, usedSkillIds, position, subPosition);
        Level level = rollHofGrade(table, tier, protectionFlag, currentLevel);

        return buildSlot(skill, level);
    }

    private void rollSignatureBlackSupreme(int slotCount,
                                           List<SkillSlot> slots,
                                           List<Level> normalizedLevels,
                                           List<Boolean> protectionFlags,
                                           Set<Long> usedSkillIds,
                                           Set<Integer> lockedIndices,
                                           String position,
                                           String subPosition) {
        boolean hasBlackAlready = slots.stream()
                .filter(Objects::nonNull)
                .map(SkillSlot::getSkill)
                .filter(Objects::nonNull)
                .anyMatch(skill -> skill.getTier() == Tier.BLACK);

        List<Integer> availableSlots = IntStream.range(0, slotCount)
                .filter(idx -> !lockedIndices.contains(idx))
                .boxed()
                .collect(Collectors.toList());

        Integer blackSlotIndex = null;
        if (!hasBlackAlready) {
            if (availableSlots.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Black tier must appear, but all slots are locked.");
            }
            blackSlotIndex = availableSlots.get(ThreadLocalRandom.current().nextInt(availableSlots.size()));
        }

        for (int i = 0; i < slotCount; i++) {
            if (slots.get(i) != null) continue;

            boolean forceBlack = blackSlotIndex != null && blackSlotIndex == i;
            SkillSlot rolledSlot;

            if (forceBlack) {
                rolledSlot = rollBlackSlot(normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
                hasBlackAlready = true;
            } else {
                rolledSlot = rollSignatureBlackNonBlackSlot(normalizedLevels.get(i), protectionFlags.get(i), usedSkillIds, position, subPosition);
            }

            slots.set(i, rolledSlot);
            SkillDto rolledSkill = rolledSlot.getSkill();
            if (rolledSkill != null && rolledSkill.getId() != null) {
                usedSkillIds.add(rolledSkill.getId());
            }
        }

        if (!hasBlackAlready) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to assign Black tier skill.");
        }
    }

    private SkillSlot rollSignatureBlackNonBlackSlot(Level currentLevel,
                                                     boolean useProtection,
                                                     Set<Long> usedSkillIds,
                                                     String position,
                                                     String subPosition) {
        Tier tier = WeightedRandom.pick(SIGNATURE_BLACK_NON_BLACK_TIER_WEIGHTS, Tier.GOLD);
        ProbabilityTable gradeTable = (tier == Tier.IRON) ? ProbabilityTable.NORMAL_PREMIUM : ProbabilityTable.SUPREME;
        Skill skill = pickSkillByTier(tier, usedSkillIds, position, subPosition);
        Level level = rollGrade(gradeTable, tier, useProtection, currentLevel);

        return buildSlot(skill, level);
    }

    private SkillSlot rollBlackSlot(Level currentLevel,
                                    boolean useProtection,
                                    Set<Long> usedSkillIds,
                                    String position,
                                    String subPosition) {
        Skill skill = pickSkillByTier(Tier.BLACK, usedSkillIds, position, subPosition);
        Level rolledLevel = WeightedRandom.pick(BLACK_GRADE_WEIGHTS, Level.D);
        Level level = applyProtection(rolledLevel, currentLevel, useProtection);

        return buildSlot(skill, level);
    }

    private HofProbabilityTable resolveHofTable(TicketType ticketType, int slotIndex) {
        return switch (ticketType) {
            case SUPREME_SKILL_CHANGE -> (slotIndex == 0)
                    ? HofProbabilityTable.supremeSlotOne()
                    : HofProbabilityTable.supremeOtherSlots();
            case PREMIUM_SKILL_CHANGE -> HofProbabilityTable.advanced();
            case SKILL_CHANGE -> HofProbabilityTable.advanced(); // default to advanced table for basic ticket on HOF
        };
    }

    private Level rollHofGrade(HofProbabilityTable table, Tier tier, boolean useProtection, Level currentLevel) {
        Level defaultLevel = currentLevel != null ? currentLevel : Level.D;
        Level rolledLevel = WeightedRandom.pick(table.gradeWeights(tier), defaultLevel);
        return applyProtection(rolledLevel, currentLevel, useProtection);
    }

    private Skill findMomentSkillByName(String selectedTheme, String position, String subPosition) {
        if (selectedTheme == null || selectedTheme.trim().isEmpty()) {
            return null;
        }
        List<Skill> candidates = skillRepository.findMomentThemesByPositionOrShared(Tier.MOMENT, position);
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(skill -> matchesSubPosition(skill, subPosition))
                .filter(skill -> skill.getName() != null && skill.getName().equalsIgnoreCase(selectedTheme.trim()))
                .findFirst()
                .orElse(null);
    }

    public List<String> getMomentThemeNames(String position, String subPosition) {
        log.info("[themes] raw position='{}', subPosition='{}'", position, subPosition);
        String normalizedPosition = normalizePosition(position);
        String normalizedSubPosition = normalizeSubPosition(subPosition);
        validatePositionRequired(normalizedPosition);
        log.info("[themes] normalized position='{}', normalized subPosition='{}'", normalizedPosition, normalizedSubPosition);

        List<Skill> jpqlResult = skillRepository.findMomentThemesByPositionOrShared(Tier.MOMENT, normalizedPosition);
        log.info("[themes] JPQL result size={}", jpqlResult.size());

        List<String> names = jpqlResult.stream()
                .filter(skill -> skill != null && skill.getName() != null && !skill.getName().isBlank())
                .filter(skill -> matchesSubPosition(skill, normalizedSubPosition))
                .sorted(Comparator.comparing(skill -> skill.getName().toLowerCase(Locale.ROOT)))
                .map(Skill::getName)
                .collect(Collectors.toList());

        List<String> nativeNames = skillRepository.findMomentThemeNamesNative(Tier.MOMENT.name(), normalizedPosition);
        log.info("[themes] Native result size={} (tier={}, position={})", nativeNames.size(), Tier.MOMENT.name(), normalizedPosition);

        return names;
    }

    private void validateLockRules(RollRequest request) {
        List<Integer> lockedSlots = Optional.ofNullable(request.getLockedSlots()).orElse(List.of());
        if (!lockedSlots.contains(0)) {
            return;
        }

        CardType cardType = request.getCardType();
        switch (cardType) {
            case PRIME -> { /* OK */ }
            case MOMENT -> {
                Skill slotOneSkill = resolveSkill(firstSkillId(request.getCurrentSkillIds()));
                if (slotOneSkill == null || slotOneSkill.getTier() != Tier.MOMENT) {
                    throw new IllegalArgumentException("Slot 1 lock for MOMENT is only allowed when current slot 1 is MOMENT tier.");
                }
            }
            case SIGNATURE, SIGNATURE_BLACK, HOF -> throw new IllegalArgumentException("Slot 1 lock not allowed for this card type: " + cardType);
            default -> throw new IllegalArgumentException("Unsupported card type: " + cardType);
        }
    }

    private Tier rollTier(ProbabilityTable table, TicketType ticketType, int slotIndex) {
        // 최고급 스킬 변경권 + 1번 슬롯(index 0) = 무조건 골드
        if (ticketType == TicketType.SUPREME_SKILL_CHANGE && slotIndex == 0) {
            return Tier.GOLD;
        }
        return WeightedRandom.pick(table.tierWeights(), Tier.BRONZE);
    }

    private Level rollGrade(ProbabilityTable table, Tier tier, boolean useProtection, Level currentLevel) {
        Level defaultLevel = currentLevel != null ? currentLevel : Level.D;
        Level rolledLevel = WeightedRandom.pick(table.gradeWeights(tier), defaultLevel);
        return applyProtection(rolledLevel, currentLevel, useProtection);
    }

    private Level applyProtection(Level rolledLevel, Level currentLevel, boolean useProtection) {
        if (useProtection && currentLevel != null && rolledLevel.ordinal() < currentLevel.ordinal()) {
            return currentLevel;
        }
        return rolledLevel;
    }

    private Skill pickWeightedSkill(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        int total = skills.stream().mapToInt(this::safeWeight).sum();
        if (total <= 0) {
            return skills.get(ThreadLocalRandom.current().nextInt(skills.size()));
        }

        int roll = ThreadLocalRandom.current().nextInt(total) + 1;
        int cumulative = 0;
        for (Skill skill : skills) {
            cumulative += safeWeight(skill);
            if (roll <= cumulative) {
                return skill;
            }
        }
        return skills.get(0);
    }

    private int safeWeight(Skill skill) {
        if (skill == null) return 1;
        return safeWeight(skill.getWeight());
    }

    private int safeWeight(Integer weight) {
        if (weight == null || weight <= 0) return 1;
        return weight;
    }

    private boolean matchesSubPosition(Skill skill, String requestedSubPosition) {
        if (skill == null) {
            return false;
        }
        if (requestedSubPosition == null || requestedSubPosition.isBlank()) {
            return true; // ALL
        }

        String skillSubPositions = skill.getSubPositions();
        if (skillSubPositions == null || skillSubPositions.isBlank()) {
            return true; // 공용 스킬
        }

        String[] tokens = skillSubPositions.split("[/|]");
        for (String token : tokens) {
            if (token != null && token.trim().equalsIgnoreCase(requestedSubPosition)) {
                return true;
            }
        }
        return false;
    }

    private SkillSlot buildSlot(Skill skill, Level level) {
        return SkillSlot.builder()
                .skill(SkillDto.from(skill))
                .level(level)
                .build();
    }

    private int resolveSlotCount(CardType cardType) {
        return cardType == CardType.SIGNATURE_BLACK ? 4 : DEFAULT_SLOT_COUNT;
    }

    // Normalization Helpers
    private List<Level> normalizeGrades(List<Level> list, int slotCount) {
        List<Level> normalized = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            normalized.add((list != null && i < list.size()) ? list.get(i) : Level.D);
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

    private Skill resolveSkill(Long id) {
        if (id == null) return null;
        return skillRepository.findById(id).orElse(null);
    }

    private String normalizePosition(String position) {
        if (position == null) return null;
        String trimmed = position.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeSubPosition(String subPosition) {
        if (subPosition == null) return null;
        String trimmed = subPosition.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("ALL")) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private void validatePositionRequired(String position) {
        if (position == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position selection is required.");
        }
    }
}
