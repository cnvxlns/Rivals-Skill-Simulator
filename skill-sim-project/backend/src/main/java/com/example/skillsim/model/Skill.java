// 스킬 테이블과 매핑되는 JPA 엔티티
package com.example.skillsim.model;

import com.example.skillsim.enums.Grade;
import com.example.skillsim.enums.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skill")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // 현재 DB에는 포지션 컬럼만 존재하므로 문자열로 그대로 저장한다.
    private String position;

    @Enumerated(EnumType.STRING)
    // 데이터 파일에는 등급이 없으므로 nullable 허용
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 데이터에는 없을 수 있으므로 nullable 허용
    private Integer weight;
}
