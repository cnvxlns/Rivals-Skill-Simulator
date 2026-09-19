package com.example.skillsim.controller

import com.example.skillsim.dto.MethodologyResponse
import com.example.skillsim.dto.ScoreRequest
import com.example.skillsim.dto.ScoreResponse
import com.example.skillsim.dto.ScoreSkillOption
import com.example.skillsim.dto.ScoreTableRequest
import com.example.skillsim.dto.ScoreTableResponse
import com.example.skillsim.dto.TicketExpectationRequest
import com.example.skillsim.dto.TicketExpectationResponse
import com.example.skillsim.service.MethodologyService
import com.example.skillsim.service.ScoreService
import com.example.skillsim.service.TicketExpectationService
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
    private val ticketExpectationService: TicketExpectationService,
) {

    /**
     * @param cardGrade 카드 등급. 예전 이름(`cardType`)으로 보내도 등급과 변형으로 풀린다.
     * @param cardVariant 카드 변형. 생략하면 기본형이다.
     */
    @GetMapping("/skills")
    fun listSkills(
        @RequestParam(required = false) cardGrade: String?,
        @RequestParam(required = false) cardVariant: String?,
        @RequestParam(required = false) cardType: String?,
        @RequestParam position: String,
    ): List<ScoreSkillOption> = scoreService.listSkills(cardGrade ?: cardType, cardVariant, position)

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

    /**
     * 스킬 변경권을 몇 장쯤 쓰면 지금보다 나아지는가.
     *
     * 채점과 같은 조건을 받아 티켓 세 종류를 각각 많이 뽑아 보고 기댓값을 돌려준다.
     * 저장하지 않으므로 인증도 필요 없다.
     */
    @PostMapping("/tickets")
    fun expectedImprovement(
        @Valid @RequestBody request: TicketExpectationRequest,
    ): TicketExpectationResponse = ticketExpectationService.evaluate(request)

    @GetMapping("/methodology")
    fun getMethodology(): MethodologyResponse = methodologyService.getMethodology()
}
