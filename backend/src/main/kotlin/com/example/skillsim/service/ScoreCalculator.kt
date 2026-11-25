package com.example.skillsim.service

import com.example.skillsim.enums.Handedness
import com.example.skillsim.model.ScoreSkill
import kotlin.math.floor
import kotlin.math.roundToLong
import org.springframework.stereotype.Component

private const val DEFAULT_USER_STAT = 120.0
private const val DEFAULT_DECK_SCORE = 500.0
private const val BATTER_OFFENSE_OVER_DEFENSE_CONDITION = "파워정확합>주루수비합"

private val STATIC_CONDITION_PROBABILITIES = mapOf(
    "ALWAYS" to 1.0,
    // 랭킹대전은 전부 원정으로 치러진다. 홈 조건은 발동하지 않고 원정 조건은 늘 발동한다.
    "홈" to 0.0,
    "원정" to 1.0,
    "주자있음" to 0.4,
    "주자없음" to 0.6,
    "주자1명" to 0.25,
    "주자2명이상" to 0.25,
    "주자2루이상" to 0.25,
    // 어느 주자든 3루에 있을 때(3루만·1,3루·2,3루·만루). MLB 2010~15 타석 비율 0.099(Tango RE24).
    "주자3루" to 0.10,
    // "주자 1명당", "주자가 더해질수록"처럼 주자 수만큼 곱해지는 효과. 확률이 아니라 타석당
    // 평균 주자 수(MLB 2010~15, Tango RE24)라 주자있음보다 크다.
    "주자당" to 0.61,
    "OVR열세" to 0.5,
    "OVR우세" to 0.5,
    "덱스코어열세" to 0.5,
    "홈런3이상" to 0.005,
    "좌투상대" to 0.3,
    "우투상대" to 0.7,
    "좌타상대" to 0.4,
    "우타상대" to 0.6,
    "직구상대" to 0.45,
    "속구선택" to 0.45,
    "변화구상대" to 0.55,
    "변화구선택" to 0.55,
    "스윗스팟" to 0.3,
    // 타구 가운데 당긴 타구 40%, 밀어친 타구 25%(FanGraphs 리그 기준치).
    "당겨치기" to 0.40,
    "밀어치기" to 0.25,
    "높은공" to 0.333,
    "낮은공" to 0.333,
    // 너클볼이 있는 카드만 던질 수 있다. 카드 구종을 받지 않으므로 없다고 본다.
    "너클볼선택" to 0.0,
    // "주자로 나가 있는 동안". 보유자가 루상에 있을 때라 이득은 뒤 타자에게만 간다. 계산기·점수표는
    // 팀 버프처럼 보유자 본인 효과만 채점하므로 0이다.
    "출루중" to 0.0,
    "대타첫타석" to 0.0, // 주전 라인업 기준 대타 출전 없음
    "교체후첫타자" to 0.25, // 계투 상대 타자 중 첫 타자 근사
    "2아웃" to 0.333,
)

/**
 * MLB 2024 리그 비율. "직전 타자"·"직전 타석" 조건이 여기서 나온다([PreviousResultResolver]).
 * 게임 안 랭킹대전의 비율은 알 수 없어 엔진의 다른 확률처럼 MLB 실측을 쓴다.
 *
 * - 출루율: NL .315, AL .309(타석 가중 .312). 워크북과 같은 0.315로 둔다.
 * - 삼진율: 경기당 팀 삼진 8.48 / 팀 타석 37.5 = 22.6%.
 * - 홈런율: 경기당 팀 홈런 1.12 / 팀 타석 37.5 = 0.030.
 */
private const val LEAGUE_ON_BASE_RATE = 0.315
private const val LEAGUE_STRIKEOUT_RATE = 0.226
private const val LEAGUE_HOME_RUN_RATE = 0.030

/**
 * 가중치를 아직 정하지 못한 조건. **값은 잠정이다.**
 *
 * 근거와 후보값, 정하는 방법은 `docs/pending_condition_weights.md`에 모았다. 값을 정하면
 * 여기서 빼 알맞은 표로 옮긴다.
 */
private val PENDING_CONDITION_PROBABILITIES = mapOf(
    // 카드의 평균 발사각(퓨어 히터). 카드마다 정해져 있어 실제로는 0 아니면 1인데, 카드 값을
    // 받지 않으므로 절반으로 둔다.
    "발사각10이상" to 0.50,
    "발사각14이하" to 0.50,
    // 상대 팀 누적 3홈런 이후 경기 종료까지 유지되는 버프의 노출 비율.
    // P(상대팀 경기당 홈런 3개 이상) x 발동 후 잔여 타석 비율로 근사한다. 게임 안 경기당 팀
    // 홈런(lambda)을 몰라 민감도가 크다(±2배): lambda=1.1 -> 0.04, 1.6 -> 0.06, 2.0 -> 0.12.
    // MLB 2024는 1.12라 0.04인데, 직전타자홈런과 달리 게임 쪽 추정(1.6)을 쓰고 있다.
    "상대팀홈런3" to 0.06,
)

/**
 * 볼 카운트별 투구 비율. 한 타석에서 던지는 공 가운데 그 카운트에서 던지는 공의 비율이다.
 *
 * 카운트 조건 스킬(초구공략, 위닝샷, 투 스트라이크 히터, 어그레시브 히터, 풀카운트)의 버프는
 * 그 카운트에서 던지는 공에만 걸리므로 투구 비율로 가중한다. 조건마다 따로 짐작하던 값
 * (초구 0.30, 2스트라이크 0.35)을 버리고 모두 이 표에서 합산한다.
 *
 * 2015 MLB(Retrosheet) 카운트 전이확률(Jim Albert, "Count Effects")로 타석당 카운트별 방문
 * 횟수의 기댓값을 구해 전체 투구 수(타석당 3.83구)로 나눴다.
 * https://bayesball.github.io/BLOG/Count_Effects.html
 */
private val PITCH_SHARE_BY_COUNT = mapOf(
    "0-0" to 0.261, "0-1" to 0.131, "1-0" to 0.102,
    "0-2" to 0.066, "1-1" to 0.102, "2-0" to 0.035,
    "1-2" to 0.095, "2-1" to 0.052, "3-0" to 0.011,
    "2-2" to 0.078, "3-1" to 0.021, "3-2" to 0.046,
)

/** [PITCH_SHARE_BY_COUNT]에서 (볼, 스트라이크)가 [matches]인 카운트의 투구 비율 합. */
private fun pitchShare(matches: (balls: Int, strikes: Int) -> Boolean): Double {
    val share = PITCH_SHARE_BY_COUNT.entries.sumOf { (count, share) ->
        val (balls, strikes) = count.split("-").map(String::toInt)
        if (matches(balls, strikes)) share else 0.0
    }
    return (share * 1000).roundToLong() / 1000.0
}

private val PLATE_SITUATION_PROBABILITIES = mapOf(
    "스트라이크타격" to 0.55,
    "초구" to pitchShare { balls, strikes -> balls == 0 && strikes == 0 },
    // 어그레시브 히터의 "볼 카운트 0-0, 0-1, 1-0".
    "빠른카운트" to pitchShare { balls, strikes -> balls + strikes <= 1 },
    // "1스트라이크"는 딱 1스트라이크, "1스트라이크이후"는 2스트라이크까지 포함한다.
    "1스트라이크" to pitchShare { _, strikes -> strikes == 1 },
    "1스트라이크이후" to pitchShare { _, strikes -> strikes >= 1 },
    "2스트라이크" to pitchShare { _, strikes -> strikes == 2 },
    "풀카운트" to pitchShare { balls, strikes -> balls == 3 && strikes == 2 },
)

/**
 * 타순이 지정되지 않았을 때 가정하는 타순.
 *
 * 예전에는 9개 타순의 평균(타순1 = 1/9 = 0.111)으로 채점했는데, 그러면 어떤 타순
 * 조건도 완전히 발동하지 않아 타순 조건이 붙은 스킬이 일률적으로 눌렸다.
 * 하나의 타순을 전제로 두는 편이 읽기도 쉽고 조건 스킬의 값도 제대로 드러난다.
 */
internal const val DEFAULT_BATTING_ORDER = 1

/**
 * 경기 상황. 리드 0.37, 동점 0.26, 열세 0.37로 나눈 칸의 합집합이다.
 * `열세`는 "지고 있을 때"만이다. 동점을 포함하는 "리드가 아닐 때"(`리드아님`, `비김또는열세`)와 다르다.
 */
private val GAME_STATE_PROBABILITIES = mapOf(
    "리드" to 0.37,
    "리드아님" to 0.63,
    "비김또는리드" to 0.63,
    "비김또는열세" to 0.63,
    "열세" to 0.37,
)

/**
 * 원정 중계 투수가 9회에 올라왔을 때 9회 말이 실제로 열릴 확률.
 *
 * 원정 팀은 9회 말을 수비한다. 9회 초가 끝났을 때 홈 팀이 앞서 있으면 9회 말은 없다.
 * 앞선 9회는 마무리가 맡으므로 중계가 9회에 오르는 경우는 동점이거나 뒤질 때이고,
 * 그중 동점일 때만 9회 말이 열린다. 경기 상황 확률로 P(동점 | 동점 또는 열세)를 쓴다.
 */
private val AWAY_RELIEVER_BOTTOM_NINTH_PLAYED = GAME_STATE_PROBABILITIES.let {
    (it.getValue("비김또는리드") - it.getValue("리드")) / it.getValue("비김또는열세")
}

/** 9회 비중에 [factor]를 곱하고 합이 1이 되게 다시 나눈다. */
private fun scaleNinthInning(weights: DoubleArray, factor: Double): DoubleArray {
    val scaled = weights.copyOf().also { it[8] *= factor }
    val total = scaled.sum()
    return DoubleArray(scaled.size) { scaled[it] / total }
}

/**
 * 역할별 회별 노출 비중. 합이 1이다. 랭킹대전은 전부 원정이라는 전제로 정한다.
 *
 * - 타자: 앞 회로 갈수록 무거운 기존 값을 그대로 쓴다. 원정이라면 회마다 고르게(1/9)
 *   돌아가야 할 것 같지만, 앞쪽에 몰린 까닭(조기 종료 등)이 확인되지 않아 손대지 않았다.
 * - 선발·마무리: 9회에 던지는 건 대개 앞설 때라 9회 말이 늘 열린다. 원정이어도 그대로다.
 * - 중계: 9회 등판은 동점·열세 상황이라 열세면 9회 말이 사라진다
 *   ([AWAY_RELIEVER_BOTTOM_NINTH_PLAYED]). 사라진 몫만큼 앞 회의 비중이 커진다.
 */
private val INNING_WEIGHTS_BY_ROLE = mapOf(
    "SP" to doubleArrayOf(0.190, 0.180, 0.170, 0.130, 0.110, 0.100, 0.060, 0.040, 0.020),
    "BATTER" to doubleArrayOf(0.150, 0.140, 0.130, 0.120, 0.110, 0.100, 0.090, 0.080, 0.080),
    "RP" to scaleNinthInning(
        doubleArrayOf(0.000, 0.000, 0.000, 0.000, 0.030, 0.180, 0.340, 0.350, 0.100),
        AWAY_RELIEVER_BOTTOM_NINTH_PLAYED,
    ),
    "CP" to doubleArrayOf(0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.100, 0.900),
)

private val MODE_PROBABILITIES = mapOf(
    "모드_랭킹대전" to 1.0,
    "모드_랭킹토너먼트" to 0.0,
    "모드_라이브매치" to 0.0,
    "모드_리그" to 0.0,
    "모드_클럽" to 0.0,
    "모드_타점배틀" to 0.0,
    "모드_랭킹슬러거" to 0.0,
)

/**
 * 역할별로 한 번 등판해 잡는 아웃 수. 마에스트로 누적과 "등판 후 N타자" 비율이 함께 쓴다.
 * 선발 17(약 5⅔이닝), 중계 4(1⅓이닝), 마무리 3(1이닝).
 */
private val EXPECTED_OUTS_BY_ROLE = mapOf(
    "BATTER" to 0,
    "SP" to 17,
    "RP" to 4,
    "CP" to 3,
)

/** 아웃 하나를 잡는 동안 상대하는 타자 수. MLB 팀 경기당 타석 약 38 / 27아웃. */
private const val BATTERS_FACED_PER_OUT = 38.0 / 27.0

/**
 * "등판 후 N타자 동안" 버프가 켜져 있는 비율. 한 번 등판에서 상대하는 타자 가운데 처음 N명의 몫이다.
 *
 * 상대 타자 수는 [EXPECTED_OUTS_BY_ROLE] × [BATTERS_FACED_PER_OUT]이다(선발 약 24명, 중계 약 5.6명,
 * 마무리 약 4.2명). N명을 채우기 전에 내려가면 등판 내내 켜져 있으므로 1이다.
 * 예전에는 3·4·9타자 값을 따로 짐작해 적어 마에스트로가 쓰는 아웃 수 가정과 어긋났다.
 */
private fun firstBattersShareByRole(batters: Int): Map<String, Double> =
    EXPECTED_OUTS_BY_ROLE.mapValues { (_, outs) ->
        val faced = outs * BATTERS_FACED_PER_OUT
        if (faced <= 0.0) 0.0 else roundToCents(minOf(batters.toDouble(), faced) / faced)
    }

private val NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE = firstBattersShareByRole(9)
private val ONE_BATTER_DURATION_PROBABILITIES_BY_ROLE = firstBattersShareByRole(1)
private val THREE_BATTER_DURATION_PROBABILITIES_BY_ROLE = firstBattersShareByRole(3)
private val FOUR_BATTER_DURATION_PROBABILITIES_BY_ROLE = firstBattersShareByRole(4)
private val SIX_BATTER_DURATION_PROBABILITIES_BY_ROLE = firstBattersShareByRole(6)
private val SEVEN_BATTER_DURATION_PROBABILITIES_BY_ROLE = firstBattersShareByRole(7)

private val GUTS_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.20,
    "SP" to 0.80,
    "RP" to 0.95,
    "CP" to 0.90,
)

// "인내<구속"(투수 구속 > 상대 타자 인내)은 GUTS_PROBABILITIES_BY_ROLE(패기: 상대가 우세할 확률)의
// 반대 사건이므로 1 - 패기확률로 산출.
private val PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.80,
    "SP" to 0.20,
    "RP" to 0.05,
    "CP" to 0.10,
)

private val MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE =
    EXPECTED_OUTS_BY_ROLE.mapValues { (_, outs) -> maestroAverageActiveStack(outs) / 12.0 }

// 도전정신(상대등급우세): 자기 카드 등급 기준 P(상대 선수 등급 > 내 등급).
// 상대 라인업이 프라임/시그니처/모먼트/HOF 위주라는 메타 가정에서 산출한 값 (agy×3+codex×3 2라운드 토론 합의).
// WBC 계열은 일반 계열의 리스킨(동일 등급)이므로 WBC=NORMAL(프라임/시그니처), WBC_BLACK=BLACK과 같은 값을 사용한다.
// 키는 카드 **등급**이다. 변형(FA·WBC)은 서열을 바꾸지 않으므로 여기에 등장하지 않는다.
//
// 서열 근거: season = live < impact < prime < moment < signature < signature black < hof.
// 등급이 오를수록 값이 단조 감소해야 하며, CardRulesTest가 그 단조성을 검사한다.
//
// 상위 네 값(HOF·시그니처블랙·시그니처·모먼트)은 기존 추정치를 그대로 유지했고,
// 그 아래 세 단계는 증분이 점점 작아지며 1.0에 수렴하는 모양으로 이어 붙였다.
// SUPREME_MOMENT는 실제 위치가 확인되지 않아 모먼트와 시그니처 사이에 둔다.
//
// 이 표에서 등급이 빠지면 getValue가 NoSuchElementException을 던져 채점이 500으로 죽는다.
private const val DEFAULT_CARD_TYPE = "SIGNATURE_BLACK"
private val OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE = mapOf(
    "SEASON" to 0.85,
    "LIVE" to 0.85,
    "IMPACT" to 0.75,
    "PRIME" to 0.60,
    "MOMENT" to 0.40,
    "SUPREME_MOMENT" to 0.30,
    "SIGNATURE" to 0.20,
    "SIGNATURE_BLACK" to 0.05,
    "HOF" to 0.00,
)

/**
 * 카드 고유 능력치 임계 조건의 정의.
 *
 * `기본 주루 능력치와 기본 수비 능력치 합이 155 이상인 경우`처럼 카드가 타고난 값에
 * 걸리는 조건이다. 여기서 말하는 기본 능력치는 육성·구단 관리를 반영하지 않은 값이라
 * 계산기의 `userStats`(육성 후 수치)와 다르다. 그래서 요청의 `baseStats`를 따로 받는다.
 */
private data class BaseStatThreshold(
    val stats: List<String>,
    val threshold: Double,
    /** 점수표가 구간별 줄을 나눌 때 스킬 이름 옆에 붙이는 능력치 묶음의 짧은 이름. */
    val label: String,
)

private val BASE_STAT_THRESHOLDS = mapOf(
    "기본주루수비합155이상" to BaseStatThreshold(listOf("주루", "수비"), 155.0, "주+수합"),
    "기본주루수비합165이상" to BaseStatThreshold(listOf("주루", "수비"), 165.0, "주+수합"),
)

/**
 * 기본 능력치를 모를 때 쓰는 값. HOF 타자 카드 중 임계를 넘는 카드의 비율이다.
 *
 * 엘 그란데는 스킬 변경으로 아무 HOF 타자 카드에나 붙을 수 있어서, 모집단은 "이 스킬을
 * 위해 고른 카드"가 아니라 HOF 타자 전체다. 그래서 1.0으로 두면 안 된다.
 *
 * 실측 앵커 두 개(fmkorea 야구게임판, 2026-09):
 * - 로베르토 클레멘테: 기본 주루 75 + 수비 90 = 165. 임계를 정확히 맞춘다.
 * - 베이브 루스: 기본 주수합 147. 최고 OVR인데도 155에 못 미친다.
 *
 * 165는 "클레멘테밖에 없다"는 증언이 독립적으로 둘 있어 0.05로 둔다. 155는 앵커가 위
 * 둘뿐이라 근거가 약하다 — 주수합이 OVR이 아니라 선수 유형을 따라가고 대략 145~165에
 * 몰린다는 관찰에서 발/수비형만 넘는다고 보고 0.20으로 잡았다. **이 값은 추정이다.**
 *
 * 정확히 채점하려면 요청에 `baseStats`를 넣는다. 그러면 이 값은 쓰이지 않고 0/1로
 * 확정된다. 계산기 화면의 '기본 능력치' 칸이 그 입력이다.
 *
 * 값을 실측으로 바꾸려면 게임 내 도감에서 HOF 타자 10~15장의 주루·수비만 적어 오면 된다.
 */
private val BASE_STAT_THRESHOLD_FALLBACK = mapOf(
    "기본주루수비합155이상" to 0.20,
    "기본주루수비합165이상" to 0.05,
)

private val TOP_ORDER_PLATE_APPEARANCE_REACH = doubleArrayOf(1.0, 1.0, 0.95, 0.70, 0.25, 0.05, 0.01)
private val MIDDLE_ORDER_PLATE_APPEARANCE_REACH = doubleArrayOf(1.0, 1.0, 0.90, 0.55, 0.12, 0.02, 0.005)
private val LOWER_ORDER_PLATE_APPEARANCE_REACH = doubleArrayOf(1.0, 0.95, 0.80, 0.40, 0.06, 0.01, 0.00)

/**
 * 한 이닝에 출루 2명을 허용한 뒤 이닝 종료까지 유지되는 버프의 노출 비율.
 * 이닝을 음이항(3아웃 도달까지)으로 두고 버프 활성 타자 수 / 이닝당 총 상대 타자 수로 산정.
 */
private const val INNING_TWO_BASERUNNERS_EXPOSURE = 0.25

/**
 * 타순 조건 확률. 타순은 경기 중 고정이므로 값은 0 아니면 1이다.
 *
 * 채점과 산정 방식 화면이 같은 함수를 쓰게 해서 두 값이 어긋나지 않게 한다.
 */
internal fun battingOrderProbabilities(battingOrder: Int): Map<String, Double> = mapOf(
    "타순1" to gate(battingOrder == 1),
    "타순2" to gate(battingOrder == 2),
    "타순3" to gate(battingOrder == 3),
    "타순1_2" to gate(battingOrder in 1..2),
    "타순2_3" to gate(battingOrder in 2..3),
    "타순3_4_5" to gate(battingOrder in 3..5),
    "타순4_5" to gate(battingOrder in 4..5),
    "타순6_9" to gate(battingOrder in 6..9),
    "타순8_9" to gate(battingOrder in 8..9),
    // "1, 2, 6, 7, 8, 9 타순" — 클린업(3~5번)만 빠진다.
    "타순1_2_6_7_8_9" to gate(battingOrder !in 3..5),
)

/**
 * 투수 스킬의 "상대 3, 4, 5 타순 상대 시" 같은 조건. 내 타순이 아니라 마주하는 타자의 타순이라
 * 0/1이 아니라 확률이다.
 *
 * 타순별 타석 수는 [plateAppearanceReach]의 도달 확률 합으로 잡는다. 앞 타순일수록 타석이
 * 많으므로 그 비중만큼 가중해 "마주한 타자 중 해당 타순의 비율"을 구한다.
 */
private val OPPONENT_BATTING_ORDER_PROBABILITIES: Map<String, Double> = run {
    val plateAppearances = (1..9).associateWith { plateAppearanceReach(it).sum() }
    val total = plateAppearances.values.sum()
    fun share(orders: IntRange) = orders.sumOf { plateAppearances.getValue(it) } / total
    mapOf(
        "상대타순3_4_5" to share(3..5),
        "상대타순6_9" to share(6..9),
    )
}

/** 조건이 확률이 아니라 참/거짓일 때 쓰는 게이트 값. */
private fun gate(condition: Boolean): Double = if (condition) 1.0 else 0.0

private fun maestroAverageActiveStack(expectedOuts: Int): Double = when {
    expectedOuts <= 0 -> 0.0
    expectedOuts <= 12 -> (expectedOuts - 1) / 2.0
    else -> (66.0 + 12.0 * (expectedOuts - 12)) / expectedOuts
}

private fun roundToCents(value: Double): Double = (value * 100.0).roundToLong() / 100.0

private data class ConditionContext(
    val normalizedPosition: String,
    val role: String,
    val battingOrder: Int?,
    val pitcherSlot: Int?,
    val cardType: String?,
    val throwHand: Handedness,
    val batHand: Handedness,
    /**
     * 카드 고유 능력치. 육성·구단 관리를 뺀 값이라 [ScoreRequest.userStats]와 다르다.
     *
     * 일부 스킬은 "기본 주루+수비 합이 155 이상인 경우"처럼 카드 고유 능력치에 임계를
     * 건다. 그 조건은 경기 중에 확률적으로 발동하는 게 아니라 카드마다 켜지거나 꺼져
     * 있으므로, 값이 들어오면 확률이 아니라 0/1로 확정된다. 비어 있으면 표본 기반
     * 기본 확률로 떨어진다.
     */
    val baseStats: Map<String, Double>,
)

private fun interface ConditionResolver {
    fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext)
}

private class StaticProbabilityResolver(
    private val source: Map<String, Double>,
) : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities.putAll(source)
    }
}

/** 선수 본인의 투/타 방향 게이트. 확률이 아니라 참/거짓이다. */
private object HandednessGateResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val thrown = context.throwHand
        val bats = context.batHand
        probabilities["좌완"] = gate(thrown == Handedness.LEFT)
        probabilities["우완"] = gate(thrown == Handedness.RIGHT)
        // 스위치 타자는 좌/우 양쪽 상황을 모두 만족한다.
        probabilities["좌타"] = gate(bats == Handedness.LEFT || bats == Handedness.SWITCH)
        probabilities["우타"] = gate(bats == Handedness.RIGHT || bats == Handedness.SWITCH)
        probabilities["스위치타"] = gate(bats == Handedness.SWITCH)
    }
}

/** 배치 자리 게이트. [position]은 정규화된 값이다. */
private fun positionGateValues(position: String): Map<String, Double> = mapOf(
    "포지션_SP" to gate(position == "SP"),
    "포지션_RP" to gate(position == "RP"),
    "포지션_RP_CP" to gate(position in setOf("RP", "CP")),
    "포지션_DH" to gate(position == "DH"),
    "포지션_2B" to gate(position == "2B"),
    "포지션_SS" to gate(position == "SS"),
    "포지션_OF" to gate(position in setOf("OF", "LF", "CF", "RF")),
    // 업 더 미들의 "C, 2B, SS, CF 배치 시"처럼 외야 가운데만 따로 보는 경우가 있다.
    "포지션_CF" to gate(position == "CF"),
    "포지션_C" to gate(position == "C"),
)

private object PositionGateResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities.putAll(positionGateValues(context.normalizedPosition))
    }
}

private object SlotGateResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val slot = context.pitcherSlot
        val isStarter = context.role == "SP"
        val isReliever = context.role == "RP"

        probabilities["선발1"] = gate(isStarter && slot == 1)
        probabilities["선발1_2"] = gate(isStarter && slot in setOf(1, 2))
        probabilities["선발3_4"] = gate(isStarter && slot in setOf(3, 4))
        probabilities["선발3_4_5"] = gate(isStarter && slot in setOf(3, 4, 5))
        probabilities["선발4_5"] = gate(isStarter && slot in setOf(4, 5))
        probabilities["중계3_4_5"] = gate(isReliever && slot in setOf(3, 4, 5))
    }
}

private object BattingOrderResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities.putAll(battingOrderProbabilities(context.battingOrder ?: DEFAULT_BATTING_ORDER))
        probabilities.putAll(OPPONENT_BATTING_ORDER_PROBABILITIES)
    }
}

private object DurationResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities["등판후9타자"] = NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        // 기선제압의 "등판 후 상대하는 첫 타자". 교체후첫타자(계투 전용 근사)와 달리 선발도 받는다.
        probabilities["등판후1타자"] = ONE_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["등판후3타자"] = THREE_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["등판후4타자"] = FOUR_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["등판후6타자"] = SIX_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["등판후7타자"] = SEVEN_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["두번째타석까지"] = secondPlateAppearanceShare(context.battingOrder)
        // 아래는 "발동 확률"이 아니라 "버프가 켜져 있는 기회의 비율"이다.
        // 등판후N타자·마에스트로누적과 같은 시간평균 노출 비율 척도를 따른다.
        // 상대팀홈런3도 같은 척도인데 값이 잠정이라 [PENDING_CONDITION_PROBABILITIES]에 있다.
        probabilities["이닝출루2인이상"] = INNING_TWO_BASERUNNERS_EXPOSURE
    }

    /**
     * 이 타자의 타석 중 첫째·둘째 타석의 비율. `타석1_2`와 같은 값이다.
     *
     * 예전에는 타순 그룹마다 0.50/0.55/0.58로 따로 적어 두어, 같은 분포로 계산하는 `타석N`
     * 조건(빌드업·오버페이스)과 조금씩 어긋났다. 이제 [plateAppearanceReach] 하나에서 뽑는다.
     */
    private fun secondPlateAppearanceShare(order: Int?): Double {
        val reach = plateAppearanceReach(order)
        return (reach[0] + reach[1]) / reach.sum()
    }
}

/**
 * 직전 타자·직전 타석의 결과 조건. [LEAGUE_ON_BASE_RATE] 등 리그 비율에서 나온다.
 *
 * - 직전 타자: 한 이닝은 출루로 끝나지 않으므로 "직전 타석이 출루였던 타석"의 비율이 곧 출루율이다.
 *   아웃·삼진은 이닝을 넘겨도 이어진다고 본다(이닝의 세 번째 아웃도 다음 타석의 직전 결과다).
 *   투수는 등판 첫 타자에게 직전 타자가 없으므로 그 몫(등판후1타자)을 뺀다. 타자는 0이라 그대로다.
 * - 직전 타석: 보유자 본인의 앞 타석이다. 첫 타석에는 앞 타석이 없으므로 첫 타석의 몫을 뺀다.
 */
private object PreviousResultResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val hasPreviousBatter = 1.0 - (ONE_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0)
        probabilities["직전타자출루"] = LEAGUE_ON_BASE_RATE * hasPreviousBatter
        probabilities["직전타자아웃"] = (1.0 - LEAGUE_ON_BASE_RATE) * hasPreviousBatter
        probabilities["직전타자홈런"] = LEAGUE_HOME_RUN_RATE * hasPreviousBatter
        probabilities["직전타자삼진"] = LEAGUE_STRIKEOUT_RATE * hasPreviousBatter

        val reach = plateAppearanceReach(context.battingOrder)
        val hasPreviousPlate = 1.0 - reach[0] / reach.sum()
        probabilities["직전타석출루실패"] = (1.0 - LEAGUE_ON_BASE_RATE) * hasPreviousPlate
        probabilities["직전타석홈런실패"] = (1.0 - LEAGUE_HOME_RUN_RATE) * hasPreviousPlate
    }
}

private object StatComparisonResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val guts = GUTS_PROBABILITIES_BY_ROLE[context.role]
            ?: GUTS_PROBABILITIES_BY_ROLE.getValue("BATTER")
        val patienceBelowVelocity = PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE[context.role]
            ?: PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE.getValue("BATTER")
        probabilities["OVR열세"] = guts
        probabilities["패기"] = guts
        probabilities["인내<구속"] = patienceBelowVelocity
        probabilities["구속>인내"] = patienceBelowVelocity
        probabilities["구위>파워"] = 0.35
        probabilities["선구>제구"] = 0.65
        probabilities[BATTER_OFFENSE_OVER_DEFENSE_CONDITION] = 1.00
        probabilities["제구>선구"] = when (context.role) {
            "RP" -> 0.10
            "CP" -> 0.25
            "SP" -> 0.45
            else -> 0.45
        }
    }
}

/**
 * 카드 고유 능력치 임계 조건.
 *
 * 다른 조건과 성격이 다르다. "주자가 있을 때"는 경기 중 확률적으로 발생하지만
 * "기본 주루+수비 합이 155 이상"은 카드를 고르는 순간 이미 정해져 있다. 그래서
 * 기본 능력치가 들어오면 확률이 아니라 0 또는 1로 확정한다.
 *
 * 값이 없을 때만 [BASE_STAT_THRESHOLD_FALLBACK]으로 떨어진다. 점수표(`/api/score/table`)
 * 처럼 특정 카드가 없는 화면이 그 경우다.
 */
private object BaseStatThresholdResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        for ((token, spec) in BASE_STAT_THRESHOLDS) {
            // 구성 스탯이 하나라도 비면 카드를 특정할 수 없으므로 표본 확률로 떨어진다.
            val values = spec.stats.map { context.baseStats[it] }
            probabilities[token] = when {
                values.any { it == null } -> BASE_STAT_THRESHOLD_FALLBACK.getValue(token)
                else -> gate(values.sumOf { it ?: 0.0 } >= spec.threshold)
            }
        }
    }
}

private object CardGradeResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        // context.cardType에는 카드 **등급**이 들어온다. 예전 이름으로 들어와도
        // normalizeGrade가 등급 쪽을 꺼내 주므로 저장된 덱이 계속 동작한다.
        val grade = if (context.cardType.isNullOrBlank()) {
            DEFAULT_CARD_TYPE
        } else {
            CardRules.normalizeGrade(context.cardType)
        }
        probabilities["상대등급우세"] =
            OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE.getValue(grade)
    }
}

private object MaestroCumulativeResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities["마에스트로누적"] = MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
    }
}

/** 신속한 처리: 중계 4아웃을 잡기 전의 0·1·2·3아웃 구간을 같은 비중으로 평균한다. */
private object ProcessedOutResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val outs = EXPECTED_OUTS_BY_ROLE.getValue("RP")
        // CSV는 4아웃 모델의 네 구간을 담는다. 가정을 바꾸면 효과 행도 함께 갱신해야 한다.
        check(outs == 4) { "Processed-out effects require the four-out relief model" }
        for (processed in 0 until outs) {
            probabilities["처리아웃$processed"] = 1.0 / outs
        }
    }
}

private object InningResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val inningWeights = INNING_WEIGHTS_BY_ROLE[context.role]
            ?: INNING_WEIGHTS_BY_ROLE.getValue("BATTER")
        for (inning in 1..inningWeights.size) {
            var probability = 0.0
            for (idx in inning - 1 until inningWeights.size) {
                probability += inningWeights[idx]
            }
            val rounded = roundToCents(probability)
            probabilities["${inning}회이후"] = rounded
            probabilities["${inning}회"] = rounded
        }
    }
}

private object InningRangeResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val inningWeights = INNING_WEIGHTS_BY_ROLE[context.role]
            ?: INNING_WEIGHTS_BY_ROLE.getValue("BATTER")
        for (start in 1..inningWeights.size) {
            var probability = 0.0
            for (end in start..inningWeights.size) {
                probability += inningWeights[end - 1]
                val rounded = roundToCents(probability)
                probabilities["${start}_${end}회"] = rounded
                if (start == 1) {
                    probabilities["${end}회까지"] = rounded
                }
            }
        }
    }
}

private object PlateAppearanceResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val reachProbabilities = plateAppearanceReach(context.battingOrder)
        val totalReachProbability = reachProbabilities.sum()

        for (start in 1..reachProbabilities.size) {
            probabilities["타석$start"] = reachProbabilities[start - 1] / totalReachProbability

            var rangeProbability = 0.0
            for (end in start..reachProbabilities.size) {
                rangeProbability += reachProbabilities[end - 1] / totalReachProbability
                probabilities["타석${start}_$end"] = rangeProbability
            }
        }
    }
}

/** 타순 그룹별 타석 도달 확률(1~7번째 타석). 원본 배열이라 호출자가 고치면 안 된다. */
internal fun plateAppearanceReach(order: Int?): DoubleArray =
    when (order ?: DEFAULT_BATTING_ORDER) {
        in 1..2 -> TOP_ORDER_PLATE_APPEARANCE_REACH
        in 3..5 -> MIDDLE_ORDER_PLATE_APPEARANCE_REACH
        in 6..9 -> LOWER_ORDER_PLATE_APPEARANCE_REACH
        else -> MIDDLE_ORDER_PLATE_APPEARANCE_REACH
    }

private val CONDITION_RESOLVERS = listOf(
    StaticProbabilityResolver(STATIC_CONDITION_PROBABILITIES),
    StaticProbabilityResolver(PENDING_CONDITION_PROBABILITIES),
    StaticProbabilityResolver(PLATE_SITUATION_PROBABILITIES),
    BattingOrderResolver,
    StaticProbabilityResolver(GAME_STATE_PROBABILITIES),
    StaticProbabilityResolver(MODE_PROBABILITIES),
    DurationResolver,
    PreviousResultResolver,
    MaestroCumulativeResolver,
    ProcessedOutResolver,
    StatComparisonResolver,
    BaseStatThresholdResolver,
    CardGradeResolver,
    PositionGateResolver,
    HandednessGateResolver,
    SlotGateResolver,
    InningResolver,
    InningRangeResolver,
    PlateAppearanceResolver,
)

private fun buildConditionProbabilities(
    position: String?,
    battingOrder: Int?,
    pitcherSlot: Int?,
    cardType: String?,
    throwHand: Handedness?,
    batHand: Handedness?,
    baseStats: Map<String, Double>?,
): MutableMap<String, Double> {
    val context = ConditionContext(
        normalizedPosition = SkillRules.normalizePosition(position),
        role = SkillRules.roleForPosition(position),
        battingOrder = battingOrder,
        pitcherSlot = pitcherSlot,
        cardType = cardType,
        throwHand = throwHand ?: Handedness.RIGHT,
        batHand = batHand ?: Handedness.RIGHT,
        baseStats = baseStats ?: emptyMap(),
    )
    val probabilities = HashMap<String, Double>()
    CONDITION_RESOLVERS.forEach { it.apply(probabilities, context) }
    return probabilities
}

private val DEFAULT_CONDITION_PROBABILITIES: Map<String, Double> =
    buildConditionProbabilities("BATTER", null, null, null, null, null, null)

@Component
class ScoreCalculator {

    /**
     * @param statBonus 기준 스탯에 더할 값. 덱의 컬렉션 버프처럼 선수 개인이 아니라 덱 전체에
     *   걸리는 보정을 넘긴다. 합산형 기준 스탯(`주루+수비`)은 구성 스탯마다 더해지는데,
     *   실제로 두 능력치가 각각 오르므로 그게 맞다. 덱 스코어 스탯은 능력치가 아니라 제외된다.
     * @param statDeltas 스탯마다 다른 보정. 포지션 훈련처럼 능력치별로 값이 다른 것을 넘긴다.
     *   [statBonus]와 달리 적힌 스탯에만 걸리고, 음수일 수 있다(자리를 옮겨 포훈이 줄 때).
     */
    fun calculate(
        selections: List<Selection>,
        statWeights: Map<String, Double>,
        conditionProbabilities: Map<String, Double> = DEFAULT_CONDITION_PROBABILITIES,
        userStats: Map<String, Double>? = emptyMap(),
        statBonus: Double = 0.0,
        statDeltas: Map<String, Double> = emptyMap(),
    ): Result {
        val totalPerStat = LinkedHashMap<String, Double>()
        val perSkill = mutableListOf<SkillScore>()
        val safeUserStats = userStats ?: emptyMap()
        var weightedTotal = 0.0

        for (selection in selections) {
            val skill = selection.skill
            val skillPerStat = LinkedHashMap<String, Double>()
            val breakdown = mutableListOf<EffectBreakdown>()
            var skillScore = 0.0

            for (effect in skill.effects) {
                val weight = statWeights[effect.stat] ?: 0.0
                val rawValue = valueAt(effect.values, selection.effectiveLevel)
                var baseStat: String? = null
                var baseValue: Double? = null
                var value = rawValue
                if (!effect.baseStat.isNullOrBlank()) {
                    baseStat = effect.baseStat
                    baseValue = userStatValue(safeUserStats, effect.baseStat, statBonus, statDeltas)
                    value = baseValue * rawValue
                }
                value = floor(value)
                val conditionProbability = conditionProbability(effect.condition, conditionProbabilities)

                val contribution = weight * value * conditionProbability

                // perStat 은 "스킬로 증가한 스탯 절대치"를 표시하므로 weight·조건확률을 제외한
                // 순수 증가량(value)만 누적한다.
                skillPerStat.mergeRounded(effect.stat, value)
                totalPerStat.mergeRounded(effect.stat, value)
                skillScore += contribution
                weightedTotal += contribution
                breakdown += EffectBreakdown(
                    stat = effect.stat,
                    condition = effect.condition,
                    weight = round(weight),
                    value = round(value),
                    conditionProbability = round(conditionProbability),
                    subtotal = round(contribution),
                    baseStat = baseStat,
                    baseValue = baseValue?.let { round(it) },
                    rawValue = round(rawValue),
                )
            }

            perSkill += SkillScore(
                skillKey = skill.skillKey,
                name = skill.name,
                score = round(skillScore),
                perStat = skillPerStat.roundedCopy(),
                breakdown = breakdown,
            )
        }

        return Result(round(weightedTotal), perSkill, totalPerStat.roundedCopy(), weightedTotal)
    }

    internal fun valueAt(values: String?, level: Int): Double {
        val tokens = if (values.isNullOrBlank()) listOf("0") else values.split("/")
        val clampedLevel = level.coerceIn(1, tokens.size)
        return tokens[clampedLevel - 1].trim().toDouble()
    }

    internal fun conditionProbability(condition: String?, overrides: Map<String, Double>?): Double {
        if (condition.isNullOrBlank() || condition.equals("ALWAYS", ignoreCase = true)) {
            return 1.0
        }
        val safeOverrides = overrides ?: emptyMap()

        var nonModeProbability = 1.0
        var modeProbability: Double? = null
        var positionProbability: Double? = null

        for (rawPart in condition.split("+")) {
            val part = rawPart.trim()
            val probability = resolveConditionPart(part, safeOverrides)
            when {
                part.startsWith("모드_") ->
                    modeProbability = maxOf(modeProbability ?: 0.0, probability)

                part.startsWith("포지션_") || part.startsWith("선발") || part.startsWith("중계") ->
                    positionProbability = maxOf(positionProbability ?: 0.0, probability)

                else -> nonModeProbability *= probability
            }
        }

        return nonModeProbability * (modeProbability ?: 1.0) * (positionProbability ?: 1.0)
    }

    private fun resolveConditionPart(part: String, overrides: Map<String, Double>): Double =
        overrides[part]
            ?: DEFAULT_CONDITION_PROBABILITIES[part]
            ?: throw IllegalArgumentException("Unknown condition token: $part")

    private fun userStatValue(
        userStats: Map<String, Double>,
        stat: String?,
        statBonus: Double = 0.0,
        statDeltas: Map<String, Double> = emptyMap(),
    ): Double {
        // 합산형 기준 스탯(예: "변화+제구")은 각 구성 스탯 값을 더해 기준값으로 사용한다.
        if (stat != null && stat.contains("+")) {
            return stat.split("+").sumOf { userStatValue(userStats, it.trim(), statBonus, statDeltas) }
        }
        // 덱 스코어는 선수 능력치가 아니라 덱의 누적 지표다. 능력치 보정을 받지 않는다.
        if (isDeckScoreStat(stat)) {
            return userStats[stat] ?: DEFAULT_DECK_SCORE
        }
        return (userStats[stat] ?: DEFAULT_USER_STAT) + statBonus + (statDeltas[stat] ?: 0.0)
    }

    private fun isDeckScoreStat(stat: String?): Boolean = stat != null && stat.contains("덱")

    private fun MutableMap<String, Double>.mergeRounded(stat: String, contribution: Double) {
        this[stat] = round((this[stat] ?: 0.0) + contribution)
    }

    private fun Map<String, Double>.roundedCopy(): Map<String, Double> =
        mapValuesTo(LinkedHashMap()) { (_, value) -> round(value) }

    private fun round(value: Double): Double = roundToCents(value)

    /**
     * 채점할 스킬 한 칸.
     *
     * @param level 보너스가 붙기 전 **기본 레벨**. 화면에서 고른 값이고,
     *   스킬레벨보호권이 지키는 것도 이 레벨이다.
     * @param levelBonus 포지션특훈과 초월로 슬롯에 붙은 레벨 보너스의 합.
     */
    data class Selection(val skill: ScoreSkill, val level: Int, val levelBonus: Int = 0) {
        /**
         * 실제로 채점에 쓰는 레벨. 사다리 끝을 넘으면 끝에서 멈춘다.
         *
         * 공지는 포지션 훈련으로 S3까지, (FA)시그니처 블랙 효과로 S4까지 오른다고만 적었고
         * S5 위는 어디에도 없다. 수치 사다리도 거기서 끝나므로 넘치는 만큼은 버린다.
         */
        val effectiveLevel: Int
            get() = minOf(level + levelBonus, SkillRules.maxLevel(skill))
    }

    data class Result(
        val total: Double,
        val perSkill: List<SkillScore>,
        val perStat: Map<String, Double>,
        internal val unroundedTotal: Double = total,
    )

    data class SkillScore(
        val skillKey: String,
        val name: String,
        val score: Double,
        val perStat: Map<String, Double>,
        val breakdown: List<EffectBreakdown>,
    )

    data class EffectBreakdown(
        val stat: String,
        val condition: String,
        val weight: Double,
        val value: Double,
        val conditionProbability: Double,
        val subtotal: Double,
        val baseStat: String?,
        val baseValue: Double?,
        val rawValue: Double,
    )

    /** [baseStatThresholdLadders]의 한 단계. [label]은 `주+수합`처럼 능력치 묶음 이름이다. */
    data class BaseStatStep(val token: String, val label: String, val threshold: Double)

    companion object {
        val defaultUserStat: Double get() = DEFAULT_USER_STAT

        val defaultDeckScore: Double get() = DEFAULT_DECK_SCORE

        /** 호출자가 배열을 건드려도 원본이 상하지 않도록 복사해서 준다. */
        val inningWeightsByRole: Map<String, DoubleArray>
            get() = INNING_WEIGHTS_BY_ROLE.mapValues { (_, weights) -> weights.copyOf() }

        /**
         * 카드 고유 능력치 임계 조건의 사다리. 같은 능력치 묶음끼리 모아 임계가 낮은 순으로 준다.
         * 높은 단계를 넘으면 낮은 단계도 넘은 것이다.
         */
        val baseStatThresholdLadders: List<List<BaseStatStep>>
            get() = BASE_STAT_THRESHOLDS.entries
                .groupBy { it.value.stats }
                .values
                .map { ladder ->
                    ladder.sortedBy { it.value.threshold }
                        .map { BaseStatStep(it.key, it.value.label, it.value.threshold) }
                }

        val staticConditionProbabilities: Map<String, Double> get() = STATIC_CONDITION_PROBABILITIES

        /** 가중치 미정 조건의 잠정값([PENDING_CONDITION_PROBABILITIES]). */
        val pendingConditionProbabilities: Map<String, Double> get() = PENDING_CONDITION_PROBABILITIES

        val plateSituationProbabilities: Map<String, Double> get() = PLATE_SITUATION_PROBABILITIES

        val battingOrderDefaultProbabilities: Map<String, Double>
            get() = battingOrderProbabilities(DEFAULT_BATTING_ORDER)

        val gameStateProbabilities: Map<String, Double> get() = GAME_STATE_PROBABILITIES

        val modeProbabilities: Map<String, Double> get() = MODE_PROBABILITIES

        val nineBatterDurationProbabilitiesByRole: Map<String, Double>
            get() = NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE

        val gutsProbabilitiesByRole: Map<String, Double> get() = GUTS_PROBABILITIES_BY_ROLE

        val patienceBelowVelocityProbabilitiesByRole: Map<String, Double>
            get() = PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE

        val maestroCumulativeProbabilitiesByRole: Map<String, Double>
            get() = MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE

        val opponentGradeAdvantageProbabilitiesByCardType: Map<String, Double>
            get() = OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE

        val topOrderPlateAppearanceReach: DoubleArray
            get() = TOP_ORDER_PLATE_APPEARANCE_REACH.copyOf()

        val middleOrderPlateAppearanceReach: DoubleArray
            get() = MIDDLE_ORDER_PLATE_APPEARANCE_REACH.copyOf()

        val lowerOrderPlateAppearanceReach: DoubleArray
            get() = LOWER_ORDER_PLATE_APPEARANCE_REACH.copyOf()

        /** [position]에 배치했을 때의 자리 조건(`포지션_*`) 값. 확률표의 같은 토큰과 같다. */
        fun positionGates(position: String?): Map<String, Double> =
            positionGateValues(SkillRules.normalizePosition(position))

        /**
         * 투/타 방향까지 반영한 조건 확률표.
         *
         * 방향이 주어지지 않으면 우완/우타로 간주한다. 좌완 전용 절(예: 빅 유닛의 "좌완 선발로 등판 시")이
         * 방향 미상일 때 발동하지 않도록 하기 위한 보수적 기본값이다.
         */
        fun conditionProbabilitiesForPosition(
            position: String?,
            battingOrder: Int? = null,
            pitcherSlot: Int? = null,
            cardType: String? = null,
            throwHand: Handedness? = null,
            batHand: Handedness? = null,
            baseStats: Map<String, Double>? = null,
        ): MutableMap<String, Double> =
            buildConditionProbabilities(
                position, battingOrder, pitcherSlot, cardType, throwHand, batHand, baseStats,
            )
    }
}
