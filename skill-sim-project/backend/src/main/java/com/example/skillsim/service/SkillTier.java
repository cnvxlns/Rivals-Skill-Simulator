package com.example.skillsim.service;

import com.example.skillsim.model.ScoreSkill;
import java.util.List;
import java.util.Locale;

/**
 * 점수표를 나누는 티어.
 *
 * <p>분류 기준은 {@code card_type} + {@code skill_id} 접두사이며,
 * docs/skill_dataset_audit.md에 기록된 롤 메타 산출 규칙과 동일하다.
 */
public enum SkillTier {
    IRON("iron"),
    BRONZE("bronze"),
    SILVER("silver"),
    GOLD("gold"),
    HOF("hof"),
    MOMENT("moment"),
    WBC("wbc"),
    BLACK("black");

    /** 표시 순서. 낮은 티어부터 올라간다. */
    public static final List<SkillTier> DISPLAY_ORDER =
            List.of(IRON, BRONZE, SILVER, GOLD, HOF, MOMENT, WBC, BLACK);

    private final String key;

    SkillTier(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** 분류 불가 시 null을 반환한다. 호출자가 제외 여부를 정한다. */
    public static SkillTier of(ScoreSkill skill) {
        String cardType = skill.getCardType() == null ? "" : skill.getCardType().toUpperCase(Locale.ROOT);
        switch (cardType) {
            case "BLACK":
                return BLACK;
            case "WBC":
                return WBC;
            case "HOF":
                return HOF;
            case "MOMENT":
            case "SUPREME_MOMENT":
                return MOMENT;
            default:
                break;
        }
        String id = skill.getSkillKey() == null ? "" : skill.getSkillKey().toUpperCase(Locale.ROOT);
        if (id.startsWith("G_")) {
            return GOLD;
        }
        if (id.startsWith("S_")) {
            return SILVER;
        }
        if (id.startsWith("B_")) {
            return BRONZE;
        }
        if (id.startsWith("I_")) {
            return IRON;
        }
        return null;
    }
}
