package com.rivals.skillsim.data.api

import com.rivals.skillsim.data.model.RollRequest
import com.rivals.skillsim.data.model.RollResponse
import com.rivals.skillsim.data.model.ScoreRequest
import com.rivals.skillsim.data.model.ScoreResponse
import com.rivals.skillsim.data.model.ScoreSkillOption
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SkillApi {
    @POST("/api/skills/roll")
    suspend fun rollSkills(@Body payload: RollRequest): RollResponse

    @GET("/api/skills/initial")
    suspend fun fetchInitialSkills(
        @Query("cardType") cardType: String,
        @Query("position") position: String,
        @Query("subPosition") subPosition: String? = null,
    ): RollResponse

    @GET("/api/score/skills")
    suspend fun fetchScoreSkills(
        @Query("cardType") cardType: String,
        @Query("position") position: String,
    ): List<ScoreSkillOption>

    @GET("/api/skills/themes")
    suspend fun fetchThemes(
        @Query("position") position: String,
        @Query("subPosition") subPosition: String? = null,
    ): List<String>

    @POST("/api/score")
    suspend fun calculateScore(@Body payload: ScoreRequest): ScoreResponse
}
