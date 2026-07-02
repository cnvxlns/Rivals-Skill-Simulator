package com.rivals.skillsim.ui.simulator

import com.rivals.skillsim.MainDispatcherRule
import com.rivals.skillsim.data.model.CardType
import com.rivals.skillsim.data.model.Grade
import com.rivals.skillsim.data.model.MethodologyResponse
import com.rivals.skillsim.data.model.RollRequest
import com.rivals.skillsim.data.model.RollResponse
import com.rivals.skillsim.data.model.ScoreRequest
import com.rivals.skillsim.data.model.ScoreResponse
import com.rivals.skillsim.data.model.ScoreSkillOption
import com.rivals.skillsim.data.model.Skill
import com.rivals.skillsim.data.model.SkillSlot
import com.rivals.skillsim.data.model.TicketType
import com.rivals.skillsim.data.model.Tier
import com.rivals.skillsim.data.model.HealthResponse
import com.rivals.skillsim.data.repository.SkillRepositoryContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rollStandardTicketAppliesSlotsAndUsageCounts() = runTest {
        val rolledSlots = listOf(
            SkillSlot(
                skill = Skill(id = 1, skillId = "G_001", name = "Ace", tier = Tier.GOLD),
                grade = Grade.S,
                score = 10.0,
            ),
        )
        val repository = FakeSkillRepository(rollResponse = RollResponse(rolledSlots, totalScore = 10.0))
        val viewModel = SimulatorViewModel(repository)

        viewModel.roll()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(rolledSlots, state.slots)
        assertEquals(10.0, state.totalScore, 0.0001)
        assertEquals(1, state.ticketUsageCounts.getValue(TicketType.SKILL_CHANGE))
        assertEquals(0, state.protectionUsageCount)
        assertEquals(CardType.SIGNATURE, repository.lastRollRequest?.cardType)
        assertEquals(listOf(Grade.D, Grade.D, Grade.D), repository.lastRollRequest?.currentGrades)
    }

    @Test
    fun loadInitialSkillsAppliesInitialSlots() = runTest {
        val initialSlots = listOf(
            SkillSlot(
                skill = Skill(id = 2, skillId = "G_002", name = "Starter", tier = Tier.GOLD),
                grade = Grade.D,
                score = 1.5,
            ),
        )
        val repository = FakeSkillRepository(
            rollResponse = RollResponse(),
            initialResponse = RollResponse(initialSlots, totalScore = 1.5),
        )
        val viewModel = SimulatorViewModel(repository)

        viewModel.loadInitialSkills()
        advanceUntilIdle()

        assertEquals(initialSlots, viewModel.state.value.slots)
        assertEquals(1.5, viewModel.state.value.totalScore, 0.0001)
        assertEquals("SIGNATURE", repository.lastInitialCardType)
        assertEquals("PITCHER", repository.lastInitialPosition)
        assertEquals(null, repository.lastInitialSubPosition)
    }

    private class FakeSkillRepository(
        private val rollResponse: RollResponse,
        private val initialResponse: RollResponse = RollResponse(),
    ) : SkillRepositoryContract {
        var lastRollRequest: RollRequest? = null
        var lastInitialCardType: String? = null
        var lastInitialPosition: String? = null
        var lastInitialSubPosition: String? = null

        override suspend fun checkHealth(): HealthResponse = HealthResponse("ok")

        override suspend fun rollSkills(payload: RollRequest): RollResponse {
            lastRollRequest = payload
            return rollResponse
        }

        override suspend fun fetchInitialSkills(
            cardType: String,
            position: String,
            subPosition: String?,
        ): RollResponse {
            lastInitialCardType = cardType
            lastInitialPosition = position
            lastInitialSubPosition = subPosition
            return initialResponse
        }

        override suspend fun fetchScoreSkills(cardType: String, position: String): List<ScoreSkillOption> =
            emptyList()

        override suspend fun fetchThemes(position: String, subPosition: String?): List<String> =
            emptyList()

        override suspend fun calculateScore(payload: ScoreRequest): ScoreResponse =
            ScoreResponse()

        override suspend fun fetchMethodology(): MethodologyResponse =
            throw NotImplementedError("not used in this test")
    }
}
