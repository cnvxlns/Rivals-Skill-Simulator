package com.example.skillsim.service;

import com.example.skillsim.dto.ScoreRequest;
import com.example.skillsim.dto.ScoreResponse;
import com.example.skillsim.dto.ScoreSelection;
import com.example.skillsim.dto.ScoreSkillOption;
import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import com.example.skillsim.repository.ScoreSkillRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreServiceTest {

    @Test
    void listSkillsFiltersByCardTypeAndPosition() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill batter = scoreSkill("S_001", "NORMAL", "BATTER", "좌투선호",
                effect("파워", "ALWAYS", "1/2/3"));
        ScoreSkill pitcher = scoreSkill("S_006", "NORMAL", "PITCHER", "좌타 스페셜리스트",
                effect("파워", "ALWAYS", "1/2/3"));
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(batter, pitcher));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        List<ScoreSkillOption> options = service.listSkills("normal", "batter");

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getSkillId()).isEqualTo("S_001");
        assertThat(options.get(0).getMaxLevel()).isEqualTo(3);
        assertThat(options.get(0).getDescription()).isEqualTo("좌투선호");
    }

    @Test
    void listSkillsExposesCardTypeGradeLabelsAndClampedMaxLevel() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill black = scoreSkill("BLACK_001", "BLACK", "BATTER", "퓨어 히터",
                effect("파워", "ALWAYS", "5/8/11/14/17"));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(black));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        List<ScoreSkillOption> options = service.listSkills("SIGNATURE_BLACK", "BATTER");

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getMaxLevel()).isEqualTo(5);
        assertThat(options.get(0).getLevelLabels()).containsExactly("D", "C", "B", "A", "S");
    }

    @Test
    void listSkillsForSpecialCardTypesIncludesNormalRollPoolSkills() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill gold = scoreSkill("G_001", "NORMAL", "BATTER", "배팅머신",
                effect("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"));
        ScoreSkill wbc = scoreSkill("WBC_001", "WBC", "BATTER", "WBC 플레이어",
                effect("파워", "ALWAYS", "9/11/13"));
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(gold));
        when(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(List.of(wbc));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        List<ScoreSkillOption> options = service.listSkills("WBC", "BATTER");

        assertThat(options).extracting(ScoreSkillOption::getSkillId)
                .containsExactly("G_001", "WBC_001");
    }

    @Test
    void listSkillsForWbcSignatureBlackIncludesNormalWbcAndBlackPools() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill gold = scoreSkill("G_001", "NORMAL", "BATTER", "배팅머신",
                effect("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"));
        ScoreSkill wbc = scoreSkill("WBC_001", "WBC", "BATTER", "WBC 플레이어",
                effect("파워", "ALWAYS", "9/11/13"));
        ScoreSkill black = scoreSkill("BLACK_001", "BLACK", "BATTER", "시그니처 블랙",
                effect("파워", "ALWAYS", "9/11/13"));
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(gold));
        when(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(List.of(wbc));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(black));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        List<ScoreSkillOption> options = service.listSkills("WBC_SIGNATURE_BLACK", "BATTER");

        assertThat(options).extracting(ScoreSkillOption::getSkillId)
                .containsExactly("G_001", "WBC_001", "BLACK_001");
    }

    @Test
    void calculateReturnsTotalPerSkillAndPerStat() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill skill = scoreSkill("S_001", "NORMAL", "BATTER", "좌투선호",
                effect("파워", "ALWAYS", "1/2/3"),
                effect("정확", "ALWAYS", "2/4/6"));
        when(repository.findBySkillKey("S_001")).thenReturn(Optional.of(skill));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.10, "정확", 0.90));

        ScoreResponse response = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(1)
                .selections(List.of(ScoreSelection.builder().skillId("S_001").level(2).build()))
                .build());

        assertThat(response.getTotal()).isEqualTo(5.80);
        assertThat(response.getPerSkill()).hasSize(1);
        assertThat(response.getPerSkill().get(0).getSkillId()).isEqualTo("S_001");
        assertThat(response.getPerSkill().get(0).getBreakdown()).hasSize(2);
        assertThat(response.getPerSkill().get(0).getBreakdown().get(0).getStat()).isEqualTo("파워");
        assertThat(response.getPerSkill().get(0).getBreakdown().get(0).getSubtotal()).isEqualTo(2.20);
        // perStat 은 스킬로 증가한 스탯 절대치(순수 증가량)이므로 weight·조건확률을 제외한 value 만 표시한다.
        assertThat(response.getPerStat())
                .extracting("stat", "value")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("파워", 2.00),
                        org.assertj.core.groups.Tuple.tuple("정확", 4.00)
                );
    }

    @Test
    void calculateUsesPositionInningProbabilitiesAndUserStats() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill skill = scoreSkill("G_057", "NORMAL", "PITCHER", "하드 트레이닝",
                effect("구위", "7회이후", "10"),
                proportionalEffect("변화", "ALWAYS", "0.05", "지구력"));
        when(repository.findBySkillKey("G_057")).thenReturn(Optional.of(skill));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("구위", 1.0, "변화", 1.0));

        ScoreResponse response = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("RP")
                .pitcherSlot(1)
                .userStats(Map.of("지구력", 200.0))
                .selections(List.of(ScoreSelection.builder().skillId("G_057").level(1).build()))
                .build());

        assertThat(response.getTotal()).isEqualTo(17.90);
        assertThat(response.getPerStat())
                .extracting("stat", "value")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("구위", 10.00),
                        org.assertj.core.groups.Tuple.tuple("변화", 10.00)
                );
    }

    @Test
    void calculateAllowsNormalRollPoolSkillForSpecialCardType() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill gold = scoreSkill("G_001", "NORMAL", "BATTER", "배팅머신",
                effect("파워", "ALWAYS", "10/10/10/10/10/10/10/10/10"));
        when(repository.findBySkillKey("G_001")).thenReturn(Optional.of(gold));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        ScoreResponse response = service.calculate(ScoreRequest.builder()
                .cardType("WBC")
                .position("BATTER")
                .battingOrder(1)
                .selections(List.of(ScoreSelection.builder().skillId("G_001").level(5).build()))
                .build());

        assertThat(response.getTotal()).isEqualTo(10.00);
    }

    @Test
    void calculateUsesRequestedBattingOrderAsConditionGate() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill skill = scoreSkill("G_025", "NORMAL", "BATTER", "테이블 세터",
                effect("POWER", "타순4_5", "10/10/10/10/10/10/10/10/10"));
        when(repository.findBySkillKey("G_025")).thenReturn(Optional.of(skill));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("POWER", 1.0));

        ScoreResponse cleanupOrder = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(4)
                .selections(List.of(ScoreSelection.builder().skillId("G_025").level(1).build()))
                .build());
        ScoreResponse leadoffOrder = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(1)
                .selections(List.of(ScoreSelection.builder().skillId("G_025").level(1).build()))
                .build());

        assertThat(cleanupOrder.getTotal()).isEqualTo(10.00);
        assertThat(leadoffOrder.getTotal()).isEqualTo(0.00);
    }

    @Test
    void calculatePassesCardGradeToConditionProbabilities() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill skill = scoreSkill("G_015", "NORMAL", "BATTER", "Challenge",
                effect("POWER", "상대등급우세", "10/10/10/10/10/10/10/10/10"));
        when(repository.findBySkillKey("G_015")).thenReturn(Optional.of(skill));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("POWER", 1.0));

        ScoreResponse defaultGrade = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(1)
                .selections(List.of(ScoreSelection.builder().skillId("G_015").level(1).build()))
                .build());
        ScoreResponse prime = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(1)
                .cardGrade("PRIME")
                .selections(List.of(ScoreSelection.builder().skillId("G_015").level(1).build()))
                .build());

        assertThat(defaultGrade.getTotal()).isEqualTo(2.00);
        assertThat(defaultGrade.getPerSkill().get(0).getBreakdown().get(0).getConditionProbability()).isEqualTo(0.20);
        assertThat(prime.getTotal()).isEqualTo(8.00);
        assertThat(prime.getPerSkill().get(0).getBreakdown().get(0).getConditionProbability()).isEqualTo(0.80);
    }

    @Test
    void calculateRejectsUnknownCardGrade() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of());

        ScoreRequest request = ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(1)
                .cardGrade("UNKNOWN")
                .selections(List.of(ScoreSelection.builder().skillId("S_001").level(1).build()))
                .build();

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calculateRejectsInvalidBattingOrder() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill skill = scoreSkill("G_025", "NORMAL", "BATTER", "테이블 세터",
                effect("POWER", "타순4_5", "10/10/10/10/10/10/10/10/10"));
        when(repository.findBySkillKey("G_025")).thenReturn(Optional.of(skill));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("POWER", 1.0));

        ScoreRequest request = ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(10)
                .selections(List.of(ScoreSelection.builder().skillId("G_025").level(1).build()))
                .build();

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calculateReturnsWarningAndZeroContributionForUnknownCondition() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill skill = scoreSkill("X_001", "NORMAL", "BATTER", "Unknown Condition Skill",
                effect("POWER", "미정의조건", "10/10/10/10/10/10/10/10/10"));
        when(repository.findBySkillKey("X_001")).thenReturn(Optional.of(skill));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("POWER", 1.0));

        ScoreResponse response = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(1)
                .selections(List.of(ScoreSelection.builder().skillId("X_001").level(1).build()))
                .build());

        assertThat(response.getTotal()).isEqualTo(0.00);
        assertThat(response.getWarnings())
                .containsExactly("X_001 contains undefined condition: 미정의조건");
        assertThat(response.getPerSkill().get(0).getWarnings())
                .containsExactly("Undefined condition: 미정의조건");
        assertThat(response.getPerSkill().get(0).getBreakdown().get(0).getConditionProbability()).isEqualTo(0.0);
        assertThat(response.getPerSkill().get(0).getBreakdown().get(0).getSubtotal()).isEqualTo(0.0);
    }

    @Test
    void calculateRejectsDuplicateSkillSelection() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of());

        ScoreRequest request = ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .battingOrder(1)
                .selections(List.of(
                        ScoreSelection.builder().skillId("S_001").level(1).build(),
                        ScoreSelection.builder().skillId("S_001").level(2).build()
                ))
                .build();

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calculateRejectsMissingBattingOrderForBatter() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of());

        ScoreRequest request = ScoreRequest.builder()
                .cardType("NORMAL")
                .position("BATTER")
                .selections(List.of(ScoreSelection.builder().skillId("S_001").level(1).build()))
                .build();

        assertThatThrownBy(() -> service.calculate(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calculateRejectsMissingOrInvalidPitcherSlotForStartingPitcher() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of());

        ScoreRequest requestMissing = ScoreRequest.builder()
                .cardType("NORMAL")
                .position("SP")
                .selections(List.of(ScoreSelection.builder().skillId("S_001").level(1).build()))
                .build();

        assertThatThrownBy(() -> service.calculate(requestMissing))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        ScoreRequest requestInvalid = ScoreRequest.builder()
                .cardType("NORMAL")
                .position("SP")
                .pitcherSlot(6)
                .selections(List.of(ScoreSelection.builder().skillId("S_001").level(1).build()))
                .build();

        assertThatThrownBy(() -> service.calculate(requestInvalid))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calculateRejectsMissingOrInvalidPitcherSlotForReliefPitcher() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of());

        ScoreRequest requestInvalid = ScoreRequest.builder()
                .cardType("NORMAL")
                .position("RP")
                .pitcherSlot(7)
                .selections(List.of(ScoreSelection.builder().skillId("S_001").level(1).build()))
                .build();

        assertThatThrownBy(() -> service.calculate(requestInvalid))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calculateAllowsMissingSlotForCloserPitcher() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill skill = scoreSkill("S_001", "NORMAL", "PITCHER", "마무리",
                effect("파워", "ALWAYS", "1"));
        when(repository.findBySkillKey("S_001")).thenReturn(Optional.of(skill));
        ScoreService service = new ScoreService(repository, new ScoreCalculator(), Map.of("파워", 1.0));

        ScoreResponse response = service.calculate(ScoreRequest.builder()
                .cardType("NORMAL")
                .position("CP")
                .selections(List.of(ScoreSelection.builder().skillId("S_001").level(1).build()))
                .build());

        assertThat(response.getTotal()).isEqualTo(1.00);
    }

    private ScoreSkill scoreSkill(String skillKey, String cardType, String position, String name, ScoreEffect... effects) {
        ScoreSkill skill = ScoreSkill.builder()
                .skillKey(skillKey)
                .cardType(cardType)
                .position(position)
                .name(name)
                .description(name)
                .build();
        for (ScoreEffect effect : effects) {
            effect.setSkill(skill);
            skill.getEffects().add(effect);
        }
        return skill;
    }

    private ScoreEffect effect(String stat, String condition, String values) {
        return ScoreEffect.builder()
                .stat(stat)
                .condition(condition)
                .values(values)
                .build();
    }

    private ScoreEffect proportionalEffect(String stat, String condition, String values, String baseStat) {
        return ScoreEffect.builder()
                .stat(stat)
                .condition(condition)
                .values(values)
                .baseStat(baseStat)
                .build();
    }
}
