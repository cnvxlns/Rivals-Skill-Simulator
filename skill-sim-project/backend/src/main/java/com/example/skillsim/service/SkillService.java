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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SkillService {

    private static final int SLOT_COUNT = 3;

    private final SkillRepository skillRepository;

    public RollResponse rollSkills(RollRequest request) {
        validateLockRules(request);

        List<Grade> normalizedGrades = normalizeGrades(request.getCurrentGrades());
        List<Long> normalizedSkillIds = normalizeSkillIds(request.getCurrentSkillIds());
        List<Boolean> protectionFlags = normalizeProtectionFlags(request.getUseLevelProtectionSlots());
        Set<Integer> locked = new HashSet<>(Optional.ofNullable(request.getLockedSlots()).orElse(List.of()));
        TicketType ticketType = request.getTicketType();
        ProbabilityTable probabilityTable = ProbabilityTable.fromTicket(ticketType);
        String position = normalizePosition(request.getPosition());
        validatePositionRequired(position);
        Set<Long> usedSkillIds = new HashSet<>();

        List<SkillSlot> slots = new ArrayList<>();

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (locked.contains(i)) {
                Skill lockedSkill = resolveSkill(normalizedSkillIds.get(i));
                slots.add(SkillSlot.builder()
                        .skill(lockedSkill)
                        .grade(normalizedGrades.get(i))
                        .build());
                if (lockedSkill != null) {
                    usedSkillIds.add(lockedSkill.getId());
                }
                continue;
            }

            Tier tier = rollTier(probabilityTable, ticketType, i);
            Skill skill = pickSkillByTier(tier, usedSkillIds, position);
            Grade grade = rollGrade(probabilityTable, tier, protectionFlags.get(i), normalizedGrades.get(i));

            slots.add(SkillSlot.builder()
                    .skill(skill)
                    .grade(grade)
                    .build());
            if (skill != null) {
                usedSkillIds.add(skill.getId());
            }
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

    private Tier rollTier(ProbabilityTable table, TicketType ticketType, int slotIndex) {
        if (ticketType == TicketType.SUPREME_SKILL_CHANGE && slotIndex == 0) {
            return Tier.GOLD;
        }
        return WeightedRandom.pick(table.tierWeights(), Tier.BRONZE);
    }

    private Skill pickSkillByTier(Tier tier, Set<Long> excludedIds, String position) {
        List<Skill> skills = findAvailableSkills(tier, position, excludedIds);
        if (skills.isEmpty()) {
            return null;
        }

        Skill picked = pickWeightedSkill(skills);
        while (picked != null && picked.getId() != null && excludedIds.contains(picked.getId())) {
            final Long pickedId = picked.getId();
            List<Skill> remaining = skills.stream()
                    .filter(skill -> skill != null && skill.getId() != null && !Objects.equals(skill.getId(), pickedId))
                    .toList();
            if (remaining.isEmpty()) {
                return null;
            }
            picked = pickWeightedSkill(remaining);
        }
        return picked;
    }

    private Grade rollGrade(ProbabilityTable table, Tier tier, boolean useProtection, Grade currentGrade) {
        Grade defaultGrade = currentGrade != null ? currentGrade : Grade.D;
        Grade rolledGrade = WeightedRandom.pick(table.gradeWeights(tier), defaultGrade);

        if (useProtection && currentGrade != null && rolledGrade.ordinal() < currentGrade.ordinal()) {
            return currentGrade;
        }
        return rolledGrade;
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
        if (skill == null) {
            return 1;
        }
        return safeWeight(skill.getWeight());
    }

    private int safeWeight(Integer weight) {
        if (weight == null || weight <= 0) {
            return 1;
        }
        return weight;
    }

    private List<Skill> findAvailableSkills(Tier tier, String position, Set<Long> excludedIds) {
        List<List<Skill>> pools = new ArrayList<>();
        pools.add(skillRepository.findByTierAndPositionIgnoreCase(tier, position));
        pools.add(skillRepository.findByPositionIgnoreCase(position));
        pools.add(skillRepository.findByTier(tier));
        pools.add(skillRepository.findAll());

        for (List<Skill> pool : pools) {
            List<Skill> filtered = pool.stream()
                    .filter(Objects::nonNull)
                    .filter(skill -> skill.getId() != null && !excludedIds.contains(skill.getId()))
                    .collect(LinkedHashMap<Long, Skill>::new,
                            (map, skill) -> map.putIfAbsent(skill.getId(), skill),
                            LinkedHashMap::putAll)
                    .values()
                    .stream()
                    .toList();
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }
        return List.of();
    }

    private String normalizePosition(String position) {
        if (position == null) {
            return null;
        }
        String trimmed = position.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private void validatePositionRequired(String position) {
        if (position == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position selection is required to roll skills.");
        }
    }
}
