// 스킬 데이터를 조회하는 JPA 리포지토리 인터페이스
package com.example.skillsim.repository;

import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findByTier(Tier tier);
    List<Skill> findByTierAndPositionIgnoreCase(Tier tier, String position);
    List<Skill> findByPositionIgnoreCase(String position);

    @Query("""
        SELECT s FROM Skill s
        WHERE s.tier = :tier
          AND (UPPER(TRIM(s.position)) = UPPER(TRIM(:position)) OR UPPER(TRIM(s.position)) = 'SHARED')
        """)
    List<Skill> findMomentThemesByPositionOrShared(@Param("tier") Tier tier, @Param("position") String position);

    @Query(value = """
        SELECT name
        FROM skill
        WHERE UPPER(tier) = UPPER(:tier)
          AND (UPPER(TRIM(position)) = UPPER(TRIM(:position)) OR UPPER(TRIM(position)) = 'SHARED')
        """, nativeQuery = true)
    List<String> findMomentThemeNamesNative(@Param("tier") String tier, @Param("position") String position);
}
