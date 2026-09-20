package com.example.skillsim.controller

import com.example.skillsim.auth.AuthPrincipal
import com.example.skillsim.auth.CurrentUser
import com.example.skillsim.dto.PositionTrainingRequest
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.service.PositionTrainingService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 구단의 포지션 훈련 현황. 계정당 한 벌이라 목록도 식별자도 없다.
 *
 * 둘 다 @CurrentUser를 받으므로 인증이 필요하다. 로그인하지 않고 쓰는 계산기 화면은 이
 * 설정과 섞지 않고 그 자리에서 보너스를 입력한다.
 */
@RestController
@RequestMapping("/api/position-training")
class PositionTrainingController(
    private val positionTrainingService: PositionTrainingService,
) {

    @GetMapping
    fun get(@CurrentUser principal: AuthPrincipal): PositionTraining =
        positionTrainingService.get(principal.userId)

    /** 통째로 갈아끼운다. 보내지 않은 자리는 훈련이 없는 것이 된다. */
    @PutMapping
    fun save(
        @Valid @RequestBody request: PositionTrainingRequest,
        @CurrentUser principal: AuthPrincipal,
    ): PositionTraining = positionTrainingService.save(principal.userId, request)
}
