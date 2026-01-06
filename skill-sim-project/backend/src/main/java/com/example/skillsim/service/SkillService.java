// 스킬 뽑기 로직과 잠금/등급 보호 규칙을 처리하는 서비스 계층
package com.example.skillsim.service;

import com.example.skillsim.dto.RollRequest;
import com.example.skillsim.dto.RollResponse;
import com.example.skillsim.dto.SkillSlot;
import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Grade;
import com.example.skillsim.enums.TicketType;
import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
import com.example.skillsim.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SkillService {

    private static final int SLOT_COUNT = 3;
    private static final Map<TicketType, Map<Tier, Integer>> TIER_WEIGHTS = buildTierWeights();
    private static final Map<TicketType, Map<Grade, Integer>> GRADE_WEIGHTS = buildGradeWeights();

    private final SkillRepository skillRepository;

    public RollResponse rollSkills(RollRequest request) {
        validateLockRules(request);

        List<Grade> normalizedGrades = normalizeGrades(request.getCurrentGrades());
        List<Long> normalizedSkillIds = normalizeSkillIds(request.getCurrentSkillIds());
        List<Boolean> protectionFlags = normalizeProtectionFlags(request.getUseLevelProtectionSlots());
        Set<Integer> locked = new HashSet<>(Optional.ofNullable(request.getLockedSlots()).orElse(List.of()));

        List<SkillSlot> slots = new ArrayList<>();

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (locked.contains(i)) {
                Skill lockedSkill = resolveSkill(normalizedSkillIds.get(i));
                slots.add(SkillSlot.builder()
                        .skill(lockedSkill)
                        .grade(normalizedGrades.get(i))
                        .build());
                continue;
            }

            TicketType ticketType = request.getTicketType();
            Tier tier = pickTier(ticketType);
            Skill skill = pickSkillByTier(tier);
            Grade grade = pickGrade(ticketType, protectionFlags.get(i), normalizedGrades.get(i));

            slots.add(SkillSlot.builder()
                    .skill(skill)
                    .grade(grade)
                    .build());
        }

        return RollResponse.builder().slots(slots).build();
    }

    private void validateLockRules(RollRequest request) {
        List<Integer> lockedSlots = Optional.ofNullable(request.getLockedSlots()).orElse(List.of());
        if (!lockedSlots.contains(0)) {
            return;
        }

        CardType cardType = request.getCardType();
        switch (cardType) {
            case PRIME -> {
                // always allowed
            }
            case MOMENT -> {
                Skill slotOneSkill = resolveSkill(firstSkillId(request.getCurrentSkillIds()));
                if (slotOneSkill == null || slotOneSkill.getTier() != Tier.MOMENT) {
                    throw new IllegalArgumentException("Slot 1 lock for MOMENT is only allowed when current slot 1 is MOMENT tier.");
                }
            }
            case SIGNATURE -> throw new IllegalArgumentException("Slot 1 lock not allowed for SIGNATURE cards.");
            default -> throw new IllegalArgumentException("Unsupported card type: " + cardType);
        }
    }

    private Tier pickTier(TicketType ticketType) {
        if (ticketType == TicketType.SUPREME_SKILL_CHANGE) {
            // Gold guarantee for Supreme tickets
            return Tier.GOLD;
        }
        return pickWeighted(TIER_WEIGHTS.get(ticketType), Tier.BRONZE);
    }

    private Skill pickSkillByTier(Tier tier) {
        List<Skill> skills = skillRepository.findByTier(tier);
        if (skills.isEmpty()) {
            skills = skillRepository.findAll();
        }
        return pickWeightedSkill(skills);
    }

    private Grade pickGrade(TicketType ticketType, boolean useProtection, Grade currentGrade) {
        Map<Grade, Integer> weights = new EnumMap<>(GRADE_WEIGHTS.get(ticketType));

        if (useProtection && currentGrade != null) {
            weights.entrySet().removeIf(entry -> entry.getKey().ordinal() < currentGrade.ordinal());
        }

        if (weights.isEmpty()) {
            weights.put(currentGrade != null ? currentGrade : Grade.D, 1);
        }

        return pickWeighted(weights, Grade.D);
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

    private <T> T pickWeighted(Map<T, Integer> weights, T defaultValue) {
        if (weights == null || weights.isEmpty()) {
            return defaultValue;
        }

        int totalWeight = weights.values().stream()
                .mapToInt(this::safeWeight)
                .sum();

        int roll = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int cumulative = 0;
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            cumulative += safeWeight(entry.getValue());
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return defaultValue;
    }

    private List<Grade> normalizeGrades(List<Grade> currentGrades) {
        List<Grade> normalized = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (currentGrades != null && i < currentGrades.size() && currentGrades.get(i) != null) {
                normalized.add(currentGrades.get(i));
            } else {
                normalized.add(Grade.D);
            }
        }
        return normalized;
    }

    private List<Long> normalizeSkillIds(List<Long> currentSkillIds) {
        List<Long> normalized = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (currentSkillIds != null && i < currentSkillIds.size()) {
                normalized.add(currentSkillIds.get(i));
            } else {
                normalized.add(null);
            }
        }
        return normalized;
    }

    private List<Boolean> normalizeProtectionFlags(List<Boolean> useLevelProtectionSlots) {
        List<Boolean> normalized = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (useLevelProtectionSlots != null && i < useLevelProtectionSlots.size() && useLevelProtectionSlots.get(i) != null) {
                normalized.add(useLevelProtectionSlots.get(i));
            } else {
                normalized.add(false);
            }
        }
        return normalized;
    }

    private Long firstSkillId(List<Long> skillIds) {
        return skillIds != null && !skillIds.isEmpty() ? skillIds.get(0) : null;
    }

    private Skill resolveSkill(Long id) {
        if (id == null) {
            return null;
        }
        return skillRepository.findById(id).orElse(null);
    }

    private int safeWeight(Skill skill) {
        return safeWeight(skill != null ? skill.getWeight() : 1);
    }

    private int safeWeight(Integer weight) {
        return Math.max(1, weight != null ? weight : 1);
    }

    private static Map<TicketType, Map<Tier, Integer>> buildTierWeights() {
        Map<TicketType, Map<Tier, Integer>> map = new EnumMap<>(TicketType.class);

        Map<Tier, Integer> normal = new EnumMap<>(Tier.class);
        normal.put(Tier.MOMENT, 1);
        normal.put(Tier.IRON, 35);
        normal.put(Tier.BRONZE, 30);
        normal.put(Tier.SILVER, 20);
        normal.put(Tier.GOLD, 14);

        Map<Tier, Integer> premium = new EnumMap<>(Tier.class);
        premium.put(Tier.MOMENT, 2);
        premium.put(Tier.IRON, 20);
        premium.put(Tier.BRONZE, 28);
        premium.put(Tier.SILVER, 28);
        premium.put(Tier.GOLD, 22);

        map.put(TicketType.SKILL_CHANGE, normal);
        map.put(TicketType.PREMIUM_SKILL_CHANGE, premium);
        // Supreme handled as guaranteed gold in pickTier
        map.put(TicketType.SUPREME_SKILL_CHANGE, premium);

        return map;
    }

    private static Map<TicketType, Map<Grade, Integer>> buildGradeWeights() {
        Map<TicketType, Map<Grade, Integer>> map = new EnumMap<>(TicketType.class);

        Map<Grade, Integer> normal = new EnumMap<>(Grade.class);
        normal.put(Grade.D, 40);
        normal.put(Grade.C, 30);
        normal.put(Grade.B, 20);
        normal.put(Grade.A, 8);
        normal.put(Grade.S, 2);

        Map<Grade, Integer> premium = new EnumMap<>(Grade.class);
        premium.put(Grade.D, 20);
        premium.put(Grade.C, 25);
        premium.put(Grade.B, 25);
        premium.put(Grade.A, 20);
        premium.put(Grade.S, 10);

        Map<Grade, Integer> supreme = new EnumMap<>(Grade.class);
        supreme.put(Grade.D, 5);
        supreme.put(Grade.C, 10);
        supreme.put(Grade.B, 25);
        supreme.put(Grade.A, 30);
        supreme.put(Grade.S, 30);

        map.put(TicketType.SKILL_CHANGE, normal);
        map.put(TicketType.PREMIUM_SKILL_CHANGE, premium);
        map.put(TicketType.SUPREME_SKILL_CHANGE, supreme);
        return map;
    }
}
