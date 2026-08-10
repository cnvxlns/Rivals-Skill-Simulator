package com.example.skillsim.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreSkill {

    /** CSV에는 없는 값이다. 적재 시 리포지토리가 순번을 부여한다. */
    private Long id;

    private String skillKey;

    private String cardType;

    private String position;

    private String name;

    private String description;

    @Builder.Default
    private List<ScoreEffect> effects = new ArrayList<>();
}
