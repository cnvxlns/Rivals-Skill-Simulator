package com.example.skillsim.repository;

import com.example.skillsim.model.ScoreSkill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreSkillRepository extends JpaRepository<ScoreSkill, Long> {
    Optional<ScoreSkill> findBySkillKey(String skillKey);

    List<ScoreSkill> findByCardTypeIgnoreCase(String cardType);

    List<ScoreSkill> findByCardTypeIgnoreCaseAndPositionIgnoreCase(String cardType, String position);
}
