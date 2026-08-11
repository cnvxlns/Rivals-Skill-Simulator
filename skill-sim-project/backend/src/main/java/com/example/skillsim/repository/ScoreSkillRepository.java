package com.example.skillsim.repository;

import com.example.skillsim.model.ScoreSkill;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 스킬 조회 계약.
 *
 * <p>데이터 원천은 클래스패스의 CSV이고 사용자 쓰기 경로가 없다. 부팅 시 한 번 적재한 뒤
 * 읽기만 하므로 DB 없이 인메모리로 구현한다({@link InMemoryScoreSkillRepository}).
 */
public interface ScoreSkillRepository {

    Optional<ScoreSkill> findBySkillKey(String skillKey);

    List<ScoreSkill> findByCardTypeIgnoreCase(String cardType);

    Optional<ScoreSkill> findById(Long id);

    List<ScoreSkill> findAll();

    void deleteAll();

    void saveAll(Collection<ScoreSkill> skills);
}
