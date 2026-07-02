package com.rivals.skillsim.ui.calculator

import com.rivals.skillsim.MainDispatcherRule
import com.rivals.skillsim.data.local.CalculatorSettings
import com.rivals.skillsim.data.local.UserPrefsSnapshot
import com.rivals.skillsim.data.local.UserPrefsStore
import com.rivals.skillsim.data.model.CardType
import com.rivals.skillsim.data.model.RollRequest
import com.rivals.skillsim.data.model.RollResponse
import com.rivals.skillsim.data.model.ScoreRequest
import com.rivals.skillsim.data.model.ScoreResponse
import com.rivals.skillsim.data.model.ScoreSkillOption
import com.rivals.skillsim.data.repository.SkillRepositoryContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun calculateSendsSelectionsAndStatsThenAppliesResult() = runTest {
        val skill = ScoreSkillOption(
            skillId = "G_001",
            cardType = "SIGNATURE",
            position = "BATTER",
            name = "Ace",
            maxLevel = 9,
            levelLabels = listOf("D", "C", "B", "A", "S", "S1", "S2", "S3", "S4"),
        )
        val repository = FakeSkillRepository(
            scoreSkills = listOf(skill),
            scoreResponse = ScoreResponse(total = 33.5),
        )
        val viewModel = CalculatorViewModel(repository)
        advanceUntilIdle()

        viewModel.updateSkill(0, "G_001")
        viewModel.updateSkill(1, "G_001")
        viewModel.updateSkill(2, "G_001")
        viewModel.updateLevel(0, 2)
        viewModel.updateUserStat("파워", 135.0)
        viewModel.calculate()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(33.5, state.result?.total ?: 0.0, 0.0001)
        assertEquals(135.0, repository.lastScoreRequest?.userStats?.get("파워") ?: 0.0, 0.0001)
        assertEquals("SIGNATURE", repository.lastScoreRequest?.cardType)
        assertEquals("BATTER", repository.lastScoreRequest?.position)
        assertEquals(3, repository.lastScoreRequest?.selections?.size)
        assertEquals(2, repository.lastScoreRequest?.selections?.first()?.level)
        assertNotNull(state.skills.singleOrNull { it.skillId == "G_001" })
    }

    @Test
    fun restoresCalculatorPrefsAndPersistsUserStats() = runTest {
        val repository = FakeSkillRepository(scoreSkills = emptyList(), scoreResponse = ScoreResponse())
        val prefsStore = FakePrefsStore(
            UserPrefsSnapshot(
                calculatorSettings = CalculatorSettings(
                    cardType = CardType.WBC,
                    position = "PITCHER",
                    subPosition = "SP",
                    battingOrder = null,
                ),
                userStats = mapOf("구속" to 140.0),
            ),
        )
        val viewModel = CalculatorViewModel(repository, prefsStore)
        advanceUntilIdle()

        assertEquals(CardType.WBC, viewModel.state.value.cardType)
        assertEquals("PITCHER", viewModel.state.value.position)
        assertEquals("SP", viewModel.state.value.subPosition)
        assertEquals(140.0, viewModel.state.value.userStats.getValue("구속"), 0.0001)

        viewModel.updateUserStat("구속", 141.0)
        advanceUntilIdle()

        assertEquals(141.0, prefsStore.savedUserStats.getValue("구속"), 0.0001)
    }

    private class FakeSkillRepository(
        private val scoreSkills: List<ScoreSkillOption>,
        private val scoreResponse: ScoreResponse,
    ) : SkillRepositoryContract {
        var lastScoreRequest: ScoreRequest? = null

        override suspend fun rollSkills(payload: RollRequest): RollResponse =
            RollResponse()

        override suspend fun fetchInitialSkills(
            cardType: String,
            position: String,
            subPosition: String?,
        ): RollResponse = RollResponse()

        override suspend fun fetchScoreSkills(cardType: String, position: String): List<ScoreSkillOption> =
            scoreSkills

        override suspend fun fetchThemes(position: String, subPosition: String?): List<String> =
            emptyList()

        override suspend fun calculateScore(payload: ScoreRequest): ScoreResponse {
            lastScoreRequest = payload
            return scoreResponse
        }
    }

    private class FakePrefsStore(
        initialSnapshot: UserPrefsSnapshot,
    ) : UserPrefsStore {
        private val snapshots = MutableStateFlow(initialSnapshot)
        var savedUserStats: Map<String, Double> = emptyMap()

        override val preferences: StateFlow<UserPrefsSnapshot> = snapshots

        override suspend fun saveLanguage(languageCode: String) = Unit

        override suspend fun saveCalculatorSettings(settings: CalculatorSettings) {
            snapshots.value = snapshots.value.copy(calculatorSettings = settings)
        }

        override suspend fun saveUserStats(userStats: Map<String, Double>) {
            savedUserStats = userStats
            snapshots.value = snapshots.value.copy(userStats = userStats)
        }
    }
}
