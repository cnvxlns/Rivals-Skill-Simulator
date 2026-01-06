package com.example.skillsim.repository;

import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findByTier(Tier tier);
}
