package com.example.skillsim.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreTableResponse {

    /** 티어 그룹. 아이언/브론즈/실버/골드/HOF/모먼트/WBC/블랙 순. */
    private List<TierGroup> tiers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierGroup {
        /** 화면 표시에 쓰는 티어 키. i18n 키로도 사용한다. */
        private String tier;
        /** 이 티어에서 요청 포지션에 해당하는 스킬 수. entries는 상위 일부만 담을 수 있다. */
        private int totalCount;
        private List<Entry> entries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String skillId;
        private String name;
        private String description;
        private double score;
        /** 실제 채점에 쓰인 등급. S가 없는 스킬은 자기 최대 등급으로 내려간다. */
        private String appliedGrade;
    }
}
