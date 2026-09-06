package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.DeckSaveRequest
import com.example.skillsim.dto.DeckScoreResponse
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.repository.ScoreSkillRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * 덱 요청의 진입점. 검증 실패를 HTTP 상태로 옮기고 채점으로 넘긴다.
 *
 * [ScoreService]가 `normalizeRequiredOrThrow`로 [IllegalArgumentException]을 400으로 바꾸는 것과
 * 같은 방식이다. 검증 규칙 자체는 [DeckValidator]에 있고 Spring을 모른다.
 */
@Service
class DeckService private constructor(
    private val deckScoreService: DeckScoreService,
    private val validator: DeckValidator,
) {

    @Autowired
    constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreService: ScoreService,
        deckScoreService: DeckScoreService,
        scoreDataLoader: ScoreDataLoader,
    ) : this(
        deckScoreService,
        DeckValidator(
            scoreSkillRepository = scoreSkillRepository,
            allowedSkillCardTypes = scoreService::allowedSkillCardTypes,
            allowedStatNames = { scoreDataLoader.statWeights.keys },
        ),
    )

    internal constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreService: ScoreService,
        deckScoreService: DeckScoreService,
        statNames: Set<String>,
    ) : this(
        deckScoreService,
        DeckValidator(
            scoreSkillRepository = scoreSkillRepository,
            allowedSkillCardTypes = scoreService::allowedSkillCardTypes,
            allowedStatNames = { statNames },
        ),
    )

    /** 저장 없이 채점만 한다. 덱을 편집하는 동안 점수를 미리 보는 용도다. */
    fun score(request: DeckSaveRequest?, includeBreakdown: Boolean = false): DeckScoreResponse =
        deckScoreService.score(validate(request), includeBreakdown)

    internal fun validate(request: DeckSaveRequest?): DeckRoster {
        if (request == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Deck request is required.")
        }
        return try {
            validator.validate(request)
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid deck.")
        }
    }
}
