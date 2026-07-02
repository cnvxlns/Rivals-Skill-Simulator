package com.rivals.skillsim.data.repository

import com.rivals.skillsim.data.api.SkillApi
import com.rivals.skillsim.data.model.RollRequest
import com.rivals.skillsim.data.model.RollResponse
import com.rivals.skillsim.data.model.ScoreRequest
import com.rivals.skillsim.data.model.ScoreResponse
import com.rivals.skillsim.data.model.ScoreSkillOption

interface SkillRepositoryContract {
    suspend fun rollSkills(payload: RollRequest): RollResponse

    suspend fun fetchInitialSkills(
        cardType: String,
        position: String,
        subPosition: String?,
    ): RollResponse

    suspend fun fetchScoreSkills(cardType: String, position: String): List<ScoreSkillOption>

    suspend fun fetchThemes(position: String, subPosition: String?): List<String>

    suspend fun calculateScore(payload: ScoreRequest): ScoreResponse
}

class SkillRepository(
    private val api: SkillApi,
) : SkillRepositoryContract {
    override suspend fun rollSkills(payload: RollRequest): RollResponse =
        api.rollSkills(payload)

    override suspend fun fetchInitialSkills(
        cardType: String,
        position: String,
        subPosition: String?,
    ): RollResponse =
        api.fetchInitialSkills(cardType, position, subPosition)

    override suspend fun fetchScoreSkills(cardType: String, position: String): List<ScoreSkillOption> =
        api.fetchScoreSkills(cardType, position)

    override suspend fun fetchThemes(position: String, subPosition: String?): List<String> =
        api.fetchThemes(position, subPosition)

    override suspend fun calculateScore(payload: ScoreRequest): ScoreResponse =
        api.calculateScore(payload)
}
