package com.example.skillsim.config

import com.example.skillsim.auth.TokenInvalidException
import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * 의도적으로 던진 오류의 사유를 응답에 실어 준다.
 *
 * 스프링 부트 기본값(`server.error.include-message=never`)에서는 [ResponseStatusException]의
 * 메시지가 응답에서 사라져 클라이언트는 빈 400만 받는다. 입력이 몇 개뿐인 단일 선수 채점에서는
 * 견딜 만했지만, 26칸짜리 덱은 어느 자리가 왜 거부됐는지 모르면 고칠 수가 없다.
 *
 * [ResponseStatusException]만 다루는 이유는 여기 실리는 문구가 전부 코드에 직접 쓴 것이라
 * 노출해도 안전하기 때문이다. 예상치 못한 예외는 기존대로 부트의 기본 처리에 맡겨,
 * 내부 사정이 그대로 새어 나가지 않게 한다.
 *
 * 본문 모양은 부트의 기본 오류 응답과 같고 `message`만 채워진다. 기존 클라이언트가 읽던
 * 필드는 그대로 남는다.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        ex: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity
        .status(ex.statusCode)
        .body(
            body(
                status = ex.statusCode.value(),
                error = ex.statusCode.toString().substringAfter(' ').ifBlank { "Error" },
                message = ex.reason.orEmpty(),
                path = request.requestURI,
            ),
        )

    /** 토큰이 없거나 유효하지 않은 경우. 이유를 세분화하지 않는다. */
    @ExceptionHandler(TokenInvalidException::class)
    fun handleTokenInvalid(
        ex: TokenInvalidException,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(
            body(
                status = HttpStatus.UNAUTHORIZED.value(),
                error = HttpStatus.UNAUTHORIZED.reasonPhrase,
                message = ex.message ?: "Authentication is required.",
                path = request.requestURI,
            ),
        )

    /**
     * 요청 본문의 모양이 어긋난 경우. 여기도 기본값에서는 메시지가 사라진다.
     *
     * 덱은 필드가 많아 어느 필드가 왜 틀렸는지 없이는 고칠 수가 없다. 필드 목록을 `errors`에
     * 따로 실어, 화면이 해당 입력칸을 짚어 줄 수 있게 한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val errors = ex.bindingResult.fieldErrors.map { error ->
            mapOf(
                "field" to error.field,
                "message" to (error.defaultMessage ?: "is invalid"),
            )
        }
        val summary = errors.joinToString("; ") { "${it["field"]} ${it["message"]}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                body(
                    status = HttpStatus.BAD_REQUEST.value(),
                    error = HttpStatus.BAD_REQUEST.reasonPhrase,
                    message = summary,
                    path = request.requestURI,
                ) + ("errors" to errors),
            )
    }

    private fun body(status: Int, error: String, message: String, path: String): Map<String, Any?> =
        mapOf(
            "timestamp" to Instant.now().toString(),
            "status" to status,
            "error" to error,
            "message" to message,
            "path" to path,
        )
}
