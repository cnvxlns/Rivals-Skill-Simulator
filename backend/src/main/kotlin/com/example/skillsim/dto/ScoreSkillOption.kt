package com.example.skillsim.dto

data class ScoreSkillOption(
    val skillId: String,
    val cardType: String,
    val position: String,
    val name: String,
    val description: String?,
    val maxLevel: Int,
    val levelLabels: List<String>,
)
