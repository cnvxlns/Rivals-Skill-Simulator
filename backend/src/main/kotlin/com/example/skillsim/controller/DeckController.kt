package com.example.skillsim.controller

import com.example.skillsim.auth.AuthPrincipal
import com.example.skillsim.auth.CurrentUser
import com.example.skillsim.dto.DeckDetailResponse
import com.example.skillsim.dto.DeckImportResponse
import com.example.skillsim.dto.DeckRulesResponse
import com.example.skillsim.dto.DeckSaveRequest
import com.example.skillsim.dto.DeckScoreResponse
import com.example.skillsim.dto.DeckSummaryResponse
import com.example.skillsim.service.DeckRulesService
import com.example.skillsim.service.DeckService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 덱 CRUD.
 *
 * `/score`를 뺀 나머지는 @CurrentUser를 받으므로 인증이 필요하다
 * ([com.example.skillsim.auth.AuthInterceptor] 참고).
 *
 * `detail` 파라미터는 효과 단위 계산 근거까지 담을지 정한다. 26명 × 3~4스킬 × 여러 효과면
 * 중첩 객체가 수백 개가 되어 응답이 크게 불어나므로 기본은 false다.
 */
@RestController
@RequestMapping("/api/decks")
class DeckController(
    private val deckService: DeckService,
    private val deckRulesService: DeckRulesService,
) {

    /** 저장하지 않고 채점만 한다. 편집 중 미리보기용이라 인증이 필요 없다. */
    @PostMapping("/score")
    fun score(
        @Valid @RequestBody request: DeckSaveRequest,
        @RequestParam(name = "detail", defaultValue = "false") detail: Boolean,
    ): DeckScoreResponse = deckService.score(request, detail)

    /**
     * 덱 편집기가 화면을 그리는 데 쓰는 규칙. 사다리 54칸과 카드별 성장 상한이다.
     *
     * 사용자 데이터가 아니라 우리 CSV에서 나오는 상수라 인증을 요구하지 않는다.
     */
    @GetMapping("/rules")
    fun rules(): DeckRulesResponse = deckRulesService.rules()

    /**
     * 덱 관리 워크북을 읽어 편집기 초안으로 돌려준다.
     *
     * **저장하지 않는다.** 그래서 @CurrentUser를 받지 않고, 인증도 요구하지 않는다
     * (`/score`와 같다). 인증을 붙이려는 사람이 있을까 봐 적어 둔다 — 여기서 나가는 것은
     * 요청자가 방금 올린 파일의 내용뿐이고 서버에는 아무것도 남지 않는다.
     *
     * 검증도 돌리지 않는다. 워크북에는 18명뿐이라 26명 규칙에 애초에 걸린다. 검증은
     * 사용자가 편집기에서 나머지를 채우고 저장을 누를 때 걸린다.
     */
    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun import(@RequestPart("file") file: MultipartFile): DeckImportResponse =
        deckService.import(file)

    @GetMapping
    fun list(@CurrentUser principal: AuthPrincipal): List<DeckSummaryResponse> =
        deckService.list(principal.userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: DeckSaveRequest,
        @CurrentUser principal: AuthPrincipal,
    ): DeckDetailResponse = deckService.create(request, principal.userId)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @CurrentUser principal: AuthPrincipal,
        @RequestParam(name = "detail", defaultValue = "false") detail: Boolean,
    ): DeckDetailResponse = deckService.get(id, principal.userId, detail)

    @GetMapping("/{id}/score")
    fun scoreOf(
        @PathVariable id: Long,
        @CurrentUser principal: AuthPrincipal,
        @RequestParam(name = "detail", defaultValue = "false") detail: Boolean,
    ): DeckScoreResponse = deckService.scoreOf(id, principal.userId, detail)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: DeckSaveRequest,
        @CurrentUser principal: AuthPrincipal,
    ): DeckDetailResponse = deckService.update(id, request, principal.userId)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, @CurrentUser principal: AuthPrincipal) {
        deckService.delete(id, principal.userId)
    }
}
