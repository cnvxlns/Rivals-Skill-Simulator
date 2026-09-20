package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.PositionTrainingRequest
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.repository.PositionTrainingRepository
import com.example.skillsim.repository.ScoreSkillRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * 구단의 포지션 훈련 현황을 읽고 쓴다.
 *
 * 계정당 한 벌이므로 목록도 식별자도 없다. [DeckService]처럼 검증 실패만 HTTP 상태로 옮긴다.
 */
@Service
class PositionTrainingService private constructor(
    private val repository: PositionTrainingRepository,
    private val validator: PositionTrainingValidator,
) {

    @Autowired
    constructor(
        repository: PositionTrainingRepository,
        scoreSkillRepository: ScoreSkillRepository,
        scoreDataLoader: ScoreDataLoader,
    ) : this(
        repository,
        PositionTrainingValidator(scoreSkillRepository, { scoreDataLoader.statWeights.keys }),
    )

    internal constructor(
        repository: PositionTrainingRepository,
        scoreSkillRepository: ScoreSkillRepository,
        statNames: Set<String>,
    ) : this(repository, PositionTrainingValidator(scoreSkillRepository, { statNames }))

    /** 저장한 적이 없으면 빈 설정을 돌려준다. 화면이 404를 다루지 않아도 되게 한다. */
    fun get(userId: Long): PositionTraining = repository.find(userId) ?: PositionTraining.EMPTY

    fun save(userId: Long, request: PositionTrainingRequest?): PositionTraining {
        val training = try {
            validator.validate(request)
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                ex.message ?: "Invalid position training.",
            )
        }
        return repository.save(userId, training)
    }
}
