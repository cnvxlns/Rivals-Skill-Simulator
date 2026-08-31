package com.example.skillsim.controller

import com.example.skillsim.dto.MethodologyResponse
import com.example.skillsim.dto.ScoreRequest
import com.example.skillsim.dto.ScoreResponse
import com.example.skillsim.dto.ScoreSkillOption
import com.example.skillsim.dto.ScoreTableRequest
import com.example.skillsim.dto.ScoreTableResponse
import com.example.skillsim.service.MethodologyService
import com.example.skillsim.service.ScoreService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/score")
class ScoreController(
    private val scoreService: ScoreService,
    private val methodologyService: MethodologyService,
) {

    @GetMapping("/skills")
    fun listSkills(
        @RequestParam cardType: String,
        @RequestParam position: String,
    ): List<ScoreSkillOption> = scoreService.listSkills(cardType, position)

    @PostMapping
    fun calculate(@Valid @RequestBody request: ScoreRequest): ScoreResponse =
        scoreService.calculate(request)

    /**
     * 티어별 스킬 점수표. 전체 스킬을 S레벨 기준으로 채점해 내림차순으로 돌려준다.
     *
     * @param topN 티어별 상위 개수. 0 이하면 전부.
     */
    @PostMapping("/table")
    fun scoreTable(
        @Valid @RequestBody request: ScoreTableRequest,
        @RequestParam(name = "topN", defaultValue = "10") topN: Int,
    ): ScoreTableResponse = scoreService.buildScoreTable(request, topN)

    @GetMapping("/methodology")
    fun getMethodology(): MethodologyResponse = methodologyService.getMethodology()
}
