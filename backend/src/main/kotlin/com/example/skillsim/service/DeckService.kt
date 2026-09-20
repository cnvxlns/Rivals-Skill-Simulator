package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.DeckDetailResponse
import com.example.skillsim.dto.DeckSaveRequest
import com.example.skillsim.dto.DeckScoreResponse
import com.example.skillsim.dto.DeckSummaryResponse
import com.example.skillsim.model.Deck
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.repository.DeckRepository
import com.example.skillsim.repository.PositionTrainingRepository
import com.example.skillsim.repository.ScoreSkillRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * 덱 요청의 진입점. 검증 실패를 HTTP 상태로 옮기고 저장·채점으로 넘긴다.
 *
 * [ScoreService]가 `normalizeRequiredOrThrow`로 [IllegalArgumentException]을 400으로 바꾸는 것과
 * 같은 방식이다. 검증 규칙 자체는 [DeckValidator]에 있고 Spring도 HTTP도 모른다.
 *
 * 소유권 검사는 이 계층에 없다. 저장소가 모든 문장의 where에 사용자 번호를 넣기 때문에
 * 남의 덱은 애초에 조회되지 않는다.
 */
@Service
class DeckService private constructor(
    private val deckScoreService: DeckScoreService,
    private val deckRepository: DeckRepository,
    private val positionTrainingRepository: PositionTrainingRepository?,
    private val validator: DeckValidator,
    private val trainingValidator: PositionTrainingValidator,
) {

    @Autowired
    constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreService: ScoreService,
        deckScoreService: DeckScoreService,
        deckRepository: DeckRepository,
        positionTrainingRepository: PositionTrainingRepository,
        scoreDataLoader: ScoreDataLoader,
    ) : this(
        deckScoreService,
        deckRepository,
        positionTrainingRepository,
        DeckValidator(
            scoreSkillRepository = scoreSkillRepository,
            skillPoolsFor = scoreService::skillPoolsFor,
            allowedStatNames = { scoreDataLoader.statWeights.keys },
        ),
        PositionTrainingValidator(scoreSkillRepository, { scoreDataLoader.statWeights.keys }),
    )

    internal constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreService: ScoreService,
        deckScoreService: DeckScoreService,
        deckRepository: DeckRepository,
        statNames: Set<String>,
        positionTrainingRepository: PositionTrainingRepository? = null,
    ) : this(
        deckScoreService,
        deckRepository,
        positionTrainingRepository,
        DeckValidator(
            scoreSkillRepository = scoreSkillRepository,
            skillPoolsFor = scoreService::skillPoolsFor,
            allowedStatNames = { statNames },
        ),
        PositionTrainingValidator(scoreSkillRepository, { statNames }),
    )

    /**
     * 저장 없이 채점만 한다. 덱을 편집하는 동안 점수를 미리 보는 용도다.
     *
     * 인증이 없는 경로라 계정에 저장된 포지션 훈련을 읽을 수 없다. 화면이 들고 있는 값을
     * 요청에 실어 보내면 그것으로 채점한다.
     */
    fun score(request: DeckSaveRequest?, includeBreakdown: Boolean = false): DeckScoreResponse =
        deckScoreService.score(validate(request), includeBreakdown, validateTraining(request))

    fun list(userId: Long): List<DeckSummaryResponse> =
        deckRepository.findSummaries(userId).map {
            DeckSummaryResponse(
                id = it.id,
                name = it.name,
                totalScore = it.totalScore,
                updatedAt = it.updatedAt.toString(),
            )
        }

    fun get(id: Long, userId: Long, includeBreakdown: Boolean = false): DeckDetailResponse {
        val deck = deckRepository.findByIdAndUser(id, userId) ?: throw notFound()
        // 저장된 total_score를 믿지 않고 지금 기준으로 다시 계산한다.
        return deck.toDetail(deckScoreService.score(deck.roster, includeBreakdown, trainingOf(userId)))
    }

    fun scoreOf(id: Long, userId: Long, includeBreakdown: Boolean = false): DeckScoreResponse {
        val deck = deckRepository.findByIdAndUser(id, userId) ?: throw notFound()
        return deckScoreService.score(deck.roster, includeBreakdown, trainingOf(userId))
    }

    fun create(request: DeckSaveRequest?, userId: Long): DeckDetailResponse {
        val roster = validate(request)
        val name = requireName(request?.name)
        if (deckRepository.countByUser(userId) >= Deck.MAX_PER_USER) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "You can keep at most ${Deck.MAX_PER_USER} decks.",
            )
        }
        val score = deckScoreService.score(roster, training = trainingOf(userId))
        val deck = try {
            deckRepository.create(userId, name, roster, score.total)
        } catch (ex: DuplicateKeyException) {
            throw duplicateName(name)
        }
        return deck.toDetail(score)
    }

    fun update(id: Long, request: DeckSaveRequest?, userId: Long): DeckDetailResponse {
        val roster = validate(request)
        val name = requireName(request?.name)
        val score = deckScoreService.score(roster, training = trainingOf(userId))
        val deck = try {
            deckRepository.update(id, userId, name, roster, score.total)
        } catch (ex: DuplicateKeyException) {
            throw duplicateName(name)
        } ?: throw notFound()
        return deck.toDetail(score)
    }

    fun delete(id: Long, userId: Long) {
        if (!deckRepository.delete(id, userId)) {
            throw notFound()
        }
    }

    /** 계정에 저장된 포지션 훈련. 저장한 적이 없으면 빈 설정이다. */
    private fun trainingOf(userId: Long): PositionTraining =
        positionTrainingRepository?.find(userId) ?: PositionTraining.EMPTY

    private fun validateTraining(request: DeckSaveRequest?): PositionTraining = try {
        trainingValidator.validate(request?.positionTraining)
    } catch (ex: IllegalArgumentException) {
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "Invalid position training.",
        )
    }

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

    private fun requireName(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Deck name is required.")
        }
        return trimmed
    }

    private fun Deck.toDetail(score: DeckScoreResponse) = DeckDetailResponse(
        id = id,
        name = name,
        roster = roster,
        score = score,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    /** 남의 덱도 없는 덱과 같은 404로 답한다. 존재 여부를 알려주지 않는다. */
    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found.")

    private fun duplicateName(name: String) =
        ResponseStatusException(HttpStatus.CONFLICT, "You already have a deck named \"$name\".")
}
