package com.example.skillsim.service;

import com.example.skillsim.dto.ScoreTableRequest;
import com.example.skillsim.dto.ScoreTableResponse;
import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import com.example.skillsim.repository.ScoreSkillRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreTableTest {

    @Test
    void 티어별로_묶고_점수_내림차순으로_정렬한다() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                skill("G_001", "NORMAL", "약한골드", "1/1/1/1/1/1/1/1/1"),
                skill("G_002", "NORMAL", "강한골드", "9/9/9/9/9/9/9/9/9"),
                skill("I_001", "NORMAL", "아이언", "2/2/2/2/2/2/2/2/2")
        ));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        ScoreTableResponse table = service.buildScoreTable(
                ScoreTableRequest.builder().position("BATTER").battingOrder(3).build(), 0);

        // 아이언이 골드보다 앞선다 (DISPLAY_ORDER)
        assertThat(table.getTiers()).extracting(ScoreTableResponse.TierGroup::getTier)
                .containsExactly("iron", "gold");

        ScoreTableResponse.TierGroup gold = table.getTiers().get(1);
        assertThat(gold.getTotalCount()).isEqualTo(2);
        assertThat(gold.getEntries()).extracting(ScoreTableResponse.Entry::getName)
                .containsExactly("강한골드", "약한골드");
        assertThat(gold.getEntries().get(0).getScore())
                .isGreaterThan(gold.getEntries().get(1).getScore());
    }

    @Test
    void topN으로_티어별_상위만_남기되_전체_개수는_보존한다() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                skill("G_001", "NORMAL", "a", "1/1/1/1/1/1/1/1/1"),
                skill("G_002", "NORMAL", "b", "2/2/2/2/2/2/2/2/2"),
                skill("G_003", "NORMAL", "c", "3/3/3/3/3/3/3/3/3")
        ));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        ScoreTableResponse.TierGroup gold = service.buildScoreTable(
                ScoreTableRequest.builder().position("BATTER").battingOrder(3).build(), 2)
                .getTiers().get(0);

        assertThat(gold.getEntries()).hasSize(2);
        assertThat(gold.getTotalCount()).isEqualTo(3);
    }

    @Test
    void S가_없는_스킬은_자기_최대등급으로_채점한다() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        // 수치가 3단계뿐이면 S(5번째)에 도달하지 못하므로 B로 내려간다.
        when(repository.findAll()).thenReturn(List.of(skill("G_001", "NORMAL", "짧은사다리", "1/2/3")));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        ScoreTableResponse.Entry entry = service.buildScoreTable(
                ScoreTableRequest.builder().position("BATTER").battingOrder(3).build(), 0)
                .getTiers().get(0).getEntries().get(0);

        assertThat(entry.getAppliedGrade()).isEqualTo("B");
        assertThat(entry.getScore()).isEqualTo(3.0);
    }

    private static ScoreSkill skill(String key, String cardType, String name, String values) {
        return ScoreSkill.builder()
                .skillKey(key)
                .cardType(cardType)
                .position("BATTER")
                .name(name)
                .effects(List.of(ScoreEffect.builder()
                        .stat("파워")
                        .condition("ALWAYS")
                        .values(values)
                        .build()))
                .build();
    }
}
