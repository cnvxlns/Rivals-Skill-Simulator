package com.example.skillsim.repository;

import com.example.skillsim.model.ScoreSkill;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * CSV에서 적재한 스킬을 메모리에 보관하는 구현체.
 *
 * <p>적재는 부팅 시 {@code ScoreDataLoader}가 한 번 수행하고 이후에는 읽기만 발생한다.
 * 읽기 경로가 여러 요청 스레드에서 동시에 호출되므로 조회용 자료구조는 교체 방식으로 갱신한다.
 */
@Repository
public class InMemoryScoreSkillRepository implements ScoreSkillRepository {

    private final AtomicLong sequence = new AtomicLong();

    private volatile Map<String, ScoreSkill> bySkillKey = Map.of();
    private volatile Map<Long, ScoreSkill> byId = Map.of();
    private volatile Map<String, List<ScoreSkill>> byCardType = Map.of();

    @Override
    public Optional<ScoreSkill> findBySkillKey(String skillKey) {
        return Optional.ofNullable(bySkillKey.get(skillKey));
    }

    @Override
    public List<ScoreSkill> findByCardTypeIgnoreCase(String cardType) {
        if (cardType == null) {
            return List.of();
        }
        return byCardType.getOrDefault(cardType.toUpperCase(Locale.ROOT), List.of());
    }

    @Override
    public Optional<ScoreSkill> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public void deleteAll() {
        bySkillKey = Map.of();
        byId = Map.of();
        byCardType = Map.of();
        sequence.set(0);
    }

    @Override
    public void saveAll(Collection<ScoreSkill> skills) {
        Map<String, ScoreSkill> keyIndex = new LinkedHashMap<>();
        Map<Long, ScoreSkill> idIndex = new LinkedHashMap<>();
        Map<String, List<ScoreSkill>> cardTypeIndex = new LinkedHashMap<>();

        for (ScoreSkill skill : skills) {
            if (skill.getId() == null) {
                skill.setId(sequence.incrementAndGet());
            }
            keyIndex.put(skill.getSkillKey(), skill);
            idIndex.put(skill.getId(), skill);

            String cardType = skill.getCardType() == null ? "" : skill.getCardType().toUpperCase(Locale.ROOT);
            cardTypeIndex.computeIfAbsent(cardType, key -> new ArrayList<>()).add(skill);
        }

        cardTypeIndex.replaceAll((key, value) -> Collections.unmodifiableList(value));

        this.bySkillKey = Collections.unmodifiableMap(keyIndex);
        this.byId = Collections.unmodifiableMap(idIndex);
        this.byCardType = Collections.unmodifiableMap(cardTypeIndex);
    }
}
