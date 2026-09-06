package com.example.skillsim.controller

import com.example.skillsim.dto.DeckSaveRequest
import com.example.skillsim.dto.DeckScoreResponse
import com.example.skillsim.service.DeckService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/decks")
class DeckController(
    private val deckService: DeckService,
) {

    /**
     * 저장하지 않고 덱을 채점한다.
     *
     * @param detail 효과 단위 계산 근거까지 담을지. 응답이 크게 불어나므로 기본은 false다.
     */
    @PostMapping("/score")
    fun score(
        @Valid @RequestBody request: DeckSaveRequest,
        @RequestParam(name = "detail", defaultValue = "false") detail: Boolean,
    ): DeckScoreResponse = deckService.score(request, detail)
}
