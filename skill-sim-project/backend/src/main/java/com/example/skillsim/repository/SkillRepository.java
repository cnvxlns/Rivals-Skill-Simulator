// 스킬 데이터를 조회하는 JPA 리포지토리 인터페이스
package com.example.skillsim.repository;

import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findByTier(Tier tier);
}
