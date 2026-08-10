package com.example.skillsim.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class ScoreEffect {

    private Long id;

    private String stat;

    private String condition;

    private String values;

    private String baseStat;

    /** 부모 참조. 직렬화하면 순환하므로 제외한다. */
    @JsonIgnore
    private ScoreSkill skill;
}
