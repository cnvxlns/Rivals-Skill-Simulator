package com.example.skillsim.model

/**
 * 구단의 포지션 훈련(포훈) 현황. 슬롯 하나하나가 얼마나 자랐는가.
 *
 * 게임에서 포훈은 **구단 단위**다. 선수가 아니라 자리에 붙고 모든 라인업에 공통으로 걸린다.
 * 그래서 덱마다 두지 않고 계정에 한 벌만 둔다.
 *
 * 레벨이 아니라 **증가치를 그대로** 받는다. 레벨별 수치표는 게임 안 '포지션 능력치' 탭에만
 * 있고 공지가 공개한 적이 없어, 우리가 표를 흉내 내면 틀린 값을 조용히 퍼뜨리게 된다.
 * 사용자가 화면에서 읽은 값을 적는 편이 정확하고, 레벨 3·9·15·18에서 붙는 랜덤 능력치까지
 * 자연히 포함된다.
 *
 * @param slots 자리 이름([com.example.skillsim.service.DeckRules])에서 그 자리의 훈련 결과로.
 *   적지 않은 자리는 훈련이 없는 것으로 본다.
 */
data class PositionTraining(
    val slots: Map<String, SlotTraining> = emptyMap(),
) {
    fun statsFor(slot: String?): Map<String, Double> =
        slot?.let { slots[it]?.stats }.orEmpty()

    /** 이 자리에 붙은 스킬 레벨 보너스. `스킬 -> 오르는 폭`이다. */
    fun skillBonusesFor(slot: String?): Map<String, Int> =
        slot?.let { slots[it] }?.skills?.associate { it.skillId to it.bonus }.orEmpty()

    /**
     * 자리를 옮겼을 때 생기는 능력치 차이.
     *
     * 선수의 보유 능력치에는 **적을 당시 자리의 포훈이 이미 들어 있다.** 그 선수를 다른
     * 자리에 세우면 그 자리의 포훈으로 갈아타므로, 두 자리의 차이만 더하면 맞는다.
     *
     * @param slot 지금 서 있는 자리. @param recordedSlot 능력치를 적을 때 서 있던 자리.
     */
    fun statDelta(slot: String, recordedSlot: String?): Map<String, Double> {
        val from = statsFor(recordedSlot ?: slot)
        val to = statsFor(slot)
        if (from == to) {
            return emptyMap()
        }
        return (from.keys + to.keys).associateWith { stat ->
            (to[stat] ?: 0.0) - (from[stat] ?: 0.0)
        }.filterValues { it != 0.0 }
    }

    companion object {
        val EMPTY = PositionTraining()

        /** 저장 본문의 스키마 버전. 모양을 바꿀 때 읽는 쪽이 판별할 수 있어야 한다. */
        const val BODY_VERSION = 1
    }
}

/**
 * 자리 한 칸의 훈련 결과.
 *
 * @param stats 포훈으로 오른 능력치. 키는 `stat_weights.csv`의 이름만 허용한다.
 * @param skills 레벨 6·12·20에서 붙은 스킬 레벨 보너스. 최대 세 개다.
 */
data class SlotTraining(
    val stats: Map<String, Double> = emptyMap(),
    val skills: List<SkillBonus> = emptyList(),
)

/** 스킬 하나에 붙은 레벨 보너스. */
data class SkillBonus(
    val skillId: String,
    val bonus: Int,
)
