package com.example.skillsim.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ScoreSelection(
    @field:NotBlank
    val skillId: String? = null,
    @field:NotNull
    @field:Min(1)
    @field:Max(9)
    val level: Int? = null,
)
