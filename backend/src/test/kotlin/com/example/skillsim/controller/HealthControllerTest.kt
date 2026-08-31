package com.example.skillsim.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HealthControllerTest {

    @Test
    fun `checkHealth returns status ok`() {
        val response = HealthController().checkHealth()

        assertThat(response).containsEntry("status", "ok")
    }
}
