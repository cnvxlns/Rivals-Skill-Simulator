package com.example.skillsim.service;

import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDescriptionsTest {

    @Test
    void 맨_x는_해당_등급의_효과_값으로_바뀐다() {
        // S_001 좌투선호. 사다리 1..9의 5번째가 S다.
        ScoreSkill skill = skill("좌투수 상대할 때 파워, 정확 능력치가 x 증가합니다",
                effect("파워", "1/2/3/4/5/6/7/8/9"),
                effect("정확", "1/2/3/4/5/6/7/8/9"));

        assertThat(SkillDescriptions.resolve(skill, 5))
                .isEqualTo("좌투수 상대할 때 파워, 정확 능력치가 5 증가합니다");
    }

    @Test
    void x_더하기_1은_표현_전체가_효과_값으로_바뀐다() {
        // S_003 타격집중. 사다리가 2..10이라 5번째는 6이다.
        // "x"만 6으로 바꾸면 "6+1"이 되어 실제 채점값(6)과 어긋난다. 산술은 사다리에 이미 반영돼 있다.
        ScoreSkill skill = skill("주자가 2명 이상일 때 파워 능력치가 x+1 증가합니다",
                effect("파워", "2/3/4/5/6/7/8/9/10"));

        assertThat(SkillDescriptions.resolve(skill, 5))
                .isEqualTo("주자가 2명 이상일 때 파워 능력치가 6 증가합니다");
    }

    @Test
    void x와_y는_각각_첫째_둘째_효과에_대응한다() {
        // B_006 평정심. 두 효과의 사다리가 서로 달라서 글자마다 값이 달라야 한다.
        ScoreSkill skill = skill("구위 능력치가 x, 제구 능력치가 y 증가합니다",
                effect("구위", "1/2/3/4/5/6/7/8/9"),
                effect("제구", "1/1/2/2/3/3/4/5/6"));

        assertThat(SkillDescriptions.resolve(skill, 5))
                .isEqualTo("구위 능력치가 5, 제구 능력치가 3 증가합니다");
    }

    @Test
    void z는_셋째_효과에_대응한다() {
        ScoreSkill skill = skill("파워 x, 정확 y, 선구 z 증가합니다",
                effect("파워", "1/2/3"),
                effect("정확", "4/5/6"),
                effect("선구", "7/8/9"));

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("파워 2, 정확 5, 선구 8 증가합니다");
    }

    @Test
    void 곱셈_표현도_표현_전체가_바뀐다() {
        ScoreSkill skill = skill("체크스윙 확률이 3*x% 증가합니다", effect("파워", "3/6/9/12/15"));

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("체크스윙 확률이 15% 증가합니다");
    }

    @Test
    void 대응하는_효과가_없으면_원문을_그대로_둔다() {
        // S_005 방망이처럼 능력치 효과 행이 아예 없는 스킬이 있다.
        ScoreSkill skill = skill("체크스윙 확률이 3*x% 증가합니다");

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("체크스윙 확률이 3*x% 증가합니다");
    }

    @Test
    void 글자가_효과_수보다_많으면_남는_글자는_그대로_둔다() {
        ScoreSkill skill = skill("파워 x, 정확 y 증가합니다", effect("파워", "1/2/3"));

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("파워 2, 정확 y 증가합니다");
    }

    @Test
    void 레벨이_사다리보다_높으면_마지막_값을_쓴다() {
        ScoreSkill skill = skill("파워 능력치가 x 증가합니다", effect("파워", "1/2/3"));

        assertThat(SkillDescriptions.resolve(skill, 9)).isEqualTo("파워 능력치가 3 증가합니다");
    }

    @Test
    void 소수_값은_사다리에_적힌_그대로_쓴다() {
        ScoreSkill skill = skill("스페셜 덱 스코어의 x만큼 증가합니다", effect("파워", "0.01"));

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("스페셜 덱 스코어의 0.01만큼 증가합니다");
    }

    @Test
    void 한글_조사가_붙어도_치환한다() {
        // G_050 등 원문에 띄어쓰기가 빠진 표기가 4건 있다. 한글을 경계로 막으면 이것만 조용히 빠진다.
        ScoreSkill skill = skill("상대 투수의 구위 능력치가 x증가합니다", effect("구위", "1/2/3/4/5"));

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("상대 투수의 구위 능력치가 5증가합니다");
    }

    @Test
    void 단어_속의_x는_건드리지_않는다() {
        ScoreSkill skill = skill("MAX 수치와 xy 표기는 그대로 둔다", effect("파워", "1/2/3"));

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("MAX 수치와 xy 표기는 그대로 둔다");
    }

    @Test
    void 플레이스홀더가_없으면_원문_그대로다() {
        ScoreSkill skill = skill("타격 시 삼진을 당하지 않습니다", effect("파워", "1/2/3"));

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("타격 시 삼진을 당하지 않습니다");
    }

    @Test
    void 설명이_비어도_안전하다() {
        assertThat(SkillDescriptions.resolve(skill(null, effect("파워", "1/2/3")), 2)).isNull();
        assertThat(SkillDescriptions.resolve(skill("", effect("파워", "1/2/3")), 2)).isEmpty();
        assertThat(SkillDescriptions.resolve(null, 2)).isNull();
    }

    private ScoreSkill skill(String description, ScoreEffect... effects) {
        ScoreSkill skill = ScoreSkill.builder()
                .skillKey("T_001")
                .cardType("NORMAL")
                .position("BATTER")
                .name("테스트")
                .description(description)
                .effects(new ArrayList<>(List.of(effects)))
                .build();
        skill.getEffects().forEach(effect -> effect.setSkill(skill));
        return skill;
    }

    private ScoreEffect effect(String stat, String values) {
        return ScoreEffect.builder().stat(stat).condition("ALWAYS").values(values).build();
    }
}
