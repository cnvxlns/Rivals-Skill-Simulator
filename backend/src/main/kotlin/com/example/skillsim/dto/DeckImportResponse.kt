package com.example.skillsim.dto

/**
 * 워크북 업로드 결과.
 *
 * **저장하지 않는다.** 화면이 덱 편집기에 그대로 부어 넣고, 사용자가 나머지를 채운 뒤
 * 저장 버튼을 누른다. 그래서 검증도 여기서 돌리지 않는다 — 워크북에는 18명뿐이라
 * 26명 규칙에 애초에 걸린다.
 *
 * @param deck 편집기가 그대로 쓸 수 있는 초안. 앱이 이미 보내는 요청과 같은 모양이다.
 * @param positionTraining 워크북이 적어 둔 자리별 포지션 훈련. 계정 전체에 걸리는 설정이라
 *   자동으로 적용하지 않고, 화면이 한 번 물어본 뒤 따로 저장한다.
 * @param warnings 읽으며 넘긴 것들. 실패가 아니라 알림이다.
 */
data class DeckImportResponse(
    val deck: DeckSaveRequest,
    val positionTraining: PositionTrainingRequest?,
    val warnings: List<ImportWarning>,
) {
    /**
     * @param cell 문제가 된 워크북 셀. 사용자가 엑셀에서 바로 찾아갈 수 있게 한다.
     * @param code 화면이 분류에 쓰는 기계용 이름.
     */
    data class ImportWarning(
        val cell: String?,
        val slot: String?,
        val code: String,
        val message: String,
    )
}
