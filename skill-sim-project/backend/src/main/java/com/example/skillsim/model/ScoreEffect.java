package com.example.skillsim.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "score_effect")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stat;

    @Column(name = "condition_key", nullable = false)
    private String condition;

    @Column(name = "effect_values", nullable = false, columnDefinition = "TEXT")
    private String values;

    @Column(name = "base_stat")
    private String baseStat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_skill_id", nullable = false)
    @JsonIgnore
    private ScoreSkill skill;
}
