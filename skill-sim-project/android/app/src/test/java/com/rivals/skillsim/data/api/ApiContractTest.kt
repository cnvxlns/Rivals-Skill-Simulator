package com.rivals.skillsim.data.api

import com.google.gson.Gson
import com.rivals.skillsim.data.model.Grade
import com.rivals.skillsim.data.model.RollRequest
import com.rivals.skillsim.data.model.RollResponse
import com.rivals.skillsim.data.model.ScoreResponse
import com.rivals.skillsim.data.model.TicketType
import retrofit2.http.GET
import retrofit2.http.POST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApiContractTest {
    private val gson = Gson()

    @Test
    fun rollResponseParsesSlotsAndScores() {
        val json = """
            {
              "slots": [
                {
                  "skill": {
                    "id": 1,
                    "skillId": "G_001",
                    "name": "Ace",
                    "tier": "GOLD",
                    "description": null,
                    "position": "PITCHER",
                    "subPositions": "SP",
                    "effects": [
                      {"condition": "ALWAYS", "logic": "ADD", "description": "Always active"}
                    ],
                    "levelEffects": {"1": "+1"}
                  },
                  "grade": "S1",
                  "score": 12.5
                }
              ],
              "totalScore": 12.5
            }
        """.trimIndent()

        val response = gson.fromJson(json, RollResponse::class.java)

        assertEquals(12.5, response.totalScore, 0.0001)
        assertEquals(1, response.slots.size)
        assertEquals(Grade.S1, response.slots.single().grade)
        assertEquals("G_001", response.slots.single().skill?.skillId)
        assertEquals("Always active", response.slots.single().skill?.effects?.single()?.description)
    }

    @Test
    fun scoreResponseParsesNestedBreakdowns() {
        val json = """
            {
              "total": 21.75,
              "perSkill": [
                {
                  "skillId": "G_001",
                  "name": "Ace",
                  "score": 21.75,
                  "perStat": [{"stat": "구위", "value": 10.5}],
                  "breakdown": [
                    {
                      "stat": "구위",
                      "condition": "ALWAYS",
                      "weight": 1.2,
                      "value": 7,
                      "conditionProbability": 1.0,
                      "subtotal": 8.4,
                      "baseStat": "변화",
                      "baseValue": 120,
                      "rawValue": 0.03
                    }
                  ],
                  "warnings": ["sample"]
                }
              ],
              "perStat": [{"stat": "구위", "value": 10.5}],
              "warnings": ["top"]
            }
        """.trimIndent()

        val response = gson.fromJson(json, ScoreResponse::class.java)

        assertEquals(21.75, response.total, 0.0001)
        assertEquals("G_001", response.perSkill.single().skillId)
        assertEquals("변화", response.perSkill.single().breakdown?.single()?.baseStat)
        assertEquals(0.03, response.perSkill.single().breakdown?.single()?.rawValue ?: 0.0, 0.0001)
        assertEquals("top", response.warnings?.single())
    }

    @Test
    fun skillApiMethodsMatchBackendEndpoints() {
        assertEquals("/api/skills/roll", postValue("rollSkills"))
        assertEquals("/api/skills/initial", getValue("fetchInitialSkills"))
        assertEquals("/api/score/skills", getValue("fetchScoreSkills"))
        assertEquals("/api/score", postValue("calculateScore"))
    }

    @Test
    fun rollRequestPreservesApiFieldNames() {
        val request = RollRequest(
            cardType = com.rivals.skillsim.data.model.CardType.SIGNATURE,
            ticketType = TicketType.SKILL_CHANGE,
            useLevelProtectionSlots = listOf(false, true, false),
            lockedSlots = listOf(0),
            currentSkillIds = listOf(1, null, 3),
            currentGrades = listOf(Grade.S, Grade.A, Grade.D),
            selectedTheme = null,
            position = "PITCHER",
            subPosition = null,
        )

        val json = gson.toJson(request)

        assertNotNull(gson.fromJson(json, RollRequest::class.java))
        assertEquals(true, json.contains("currentGrades"))
        assertEquals(true, json.contains("useLevelProtectionSlots"))
    }

    private fun postValue(methodName: String): String =
        SkillApi::class.java.methods
            .single { it.name == methodName }
            .getAnnotation(POST::class.java)
            ?.value
            ?: error("Missing POST annotation on $methodName")

    private fun getValue(methodName: String): String =
        SkillApi::class.java.methods
            .single { it.name == methodName }
            .getAnnotation(GET::class.java)
            ?.value
            ?: error("Missing GET annotation on $methodName")
}
