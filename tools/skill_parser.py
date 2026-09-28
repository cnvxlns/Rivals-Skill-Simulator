from __future__ import annotations

import re
from dataclasses import dataclass


X_LEVELS = list(range(1, 10))
STATS = ["파워", "정확", "선구", "인내", "구위", "변화", "제구", "구속", "주루", "수비", "지구력"]
BATTER_STATS = ["파워", "정확", "선구", "인내", "주루", "수비"]
PITCHER_STATS = ["구속", "구위", "변화", "제구", "지구력", "수비"]

SPECIAL_EFFECT_CONDITIONS = {
    ("M_029", "구위", "5"): "등판후9타자",
    ("M_029", "변화", "5"): "등판후9타자",
    ("M_029", "제구", "5"): "등판후9타자",
    ("M_032", "파워", "5"): "두번째타석까지",
    ("M_032", "정확", "5"): "두번째타석까지",
    ("M_032", "선구", "5"): "두번째타석까지",
    ("M_008", "파워", "6"): "두번째타석까지",
    ("M_008", "정확", "6"): "두번째타석까지",
    ("M_008", "선구", "6"): "두번째타석까지",
    ("M_008", "인내", "6"): "두번째타석까지",
    ("M_023", "구위", "6"): "등판후7타자",
    ("M_023", "변화", "6"): "등판후7타자",
    ("M_023", "구속", "6"): "등판후7타자",
    ("M_023", "제구", "6"): "등판후7타자",
    ("M_041", "파워", "10"): "인내<구속",
    ("M_041", "정확", "10"): "인내<구속",
    ("M_041", "선구", "10"): "인내<구속",
    ("G_028", "정확", "4/5/6/7/8/9/10/11/12"): "구위>파워",
    ("G_028", "파워", "4"): "구위>파워",
    ("HOF_018", "구위", "1/2/3/4/5/6"): "선구>제구",
    ("HOF_018", "변화", "1/2/3/4/5/6"): "선구>제구",
    ("HOF_036", "파워", "1/2/3/4/5/6"): "제구>선구",
    ("HOF_036", "정확", "1/2/3/4/5/6"): "제구>선구",
}

CARD_TYPES = {
    "normal": "NORMAL",
    "moment": "MOMENT",
    "WBC": "WBC",
    "black": "BLACK",
    "HOF": "HOF",
}


VALUE_RE = re.compile(
    r"\{?\s*(?:"
    r"(?:\d+(?:\.\d+)?)\s*\*\s*\(?\s*x\s*(?:\+\s*\d+)?\s*\)?"
    r"|(?:x|y|z|a|b|c)\s*(?:\+\s*\d+|\*\s*\d+)?"
    r"|\d+(?:\.\d+)?"
    r")\s*\}?"
)


@dataclass(frozen=True)
class SkillRow:
    skill_id: str
    card_type: str
    position: str
    name: str
    description: str
    variables: dict[str, str]


@dataclass(frozen=True)
class EffectRow:
    skill_id: str
    stat: str
    condition: str
    values: str
    base_stat: str = ""


@dataclass(frozen=True)
class ReviewNote:
    skill_id: str
    reason: str
    text: str


def expand_value(expr: str, variables: dict[str, str]) -> str:
    token = expr.strip().strip("{}").replace(" ", "").replace("%", "")
    if token in variables and variables[token]:
        return variables[token]
    if re.fullmatch(r"\d+(?:\.\d+)?", token):
        return token

    def expand_for_x(level: int) -> float:
        if token == "x":
            return float(level)
        match = re.fullmatch(r"x\+(\d+)", token)
        if match:
            return float(level + int(match.group(1)))
        match = re.fullmatch(r"(\d+(?:\.\d+)?)\*x", token)
        if match:
            return float(match.group(1)) * level
        match = re.fullmatch(r"(\d+(?:\.\d+)?)\*\(x\+(\d+)\)", token)
        if match:
            return float(match.group(1)) * (level + int(match.group(2)))
        match = re.fullmatch(r"x\*(\d+(?:\.\d+)?)", token)
        if match:
            return level * float(match.group(1))
        raise ValueError(f"Unsupported value expression: {expr}")

    variable_offset = re.fullmatch(r"([yzabc])\+(\d+)", token)
    if variable_offset:
        base = variables.get(variable_offset.group(1), "")
        if not base:
            raise ValueError(f"Missing variable {variable_offset.group(1)} for {expr}")
        offset = int(variable_offset.group(2))
        return "/".join(_format_number(float(value) + offset) for value in base.split("/") if value != "")

    if "x" in token:
        return "/".join(_format_number(expand_for_x(level)) for level in X_LEVELS)

    raise ValueError(f"Unsupported value expression: {expr}")


def build_effect_rows(row: SkillRow) -> tuple[list[EffectRow], list[ReviewNote]]:
    manual_effects = _manual_effect_rows(row)
    if manual_effects is not None:
        return manual_effects, []

    effects: list[EffectRow] = []
    notes: list[ReviewNote] = []
    seen = set()

    for sentence in _split_sentences(row.description):
        condition = _condition_for(sentence)

        tier_effects = _parse_tier_effects(sentence, row)
        if tier_effects is not None:
            for effect in tier_effects:
                key = (effect.skill_id, effect.stat, effect.condition, effect.values)
                if key not in seen:
                    seen.add(key)
                    effects.append(effect)
            continue

        if _is_tier_context_sentence(sentence):
            continue
        
        prop_effects = _parse_proportional_effect(sentence, row)
        if prop_effects:
            for pe in prop_effects:
                key = (pe.skill_id, pe.stat, pe.condition, pe.values)
                if key not in seen:
                    seen.add(key)
                    effects.append(pe)
            
            clean_sentence = _remove_proportional_clause(sentence)
            if clean_sentence.strip():
                sentence_effects = _extract_effects(clean_sentence, row)
                for stat, value_expr, own_decrease in sentence_effects:
                    try:
                        values = expand_value(value_expr, row.variables)
                    except ValueError as exc:
                        notes.append(ReviewNote(row.skill_id, "값전개필요", f"{clean_sentence} ({exc})"))
                        continue
                    effect_condition, effect_values = _effect_condition_and_values(
                        row, stat, values, condition, own_decrease
                    )
                    key = (row.skill_id, stat, effect_condition, effect_values)
                    if key not in seen:
                        seen.add(key)
                        effects.append(EffectRow(row.skill_id, stat, effect_condition, effect_values))
            else:
                sentence_effects = []
                
            if clean_sentence.strip() and _needs_review(clean_sentence, sentence_effects):
                notes.append(ReviewNote(row.skill_id, _review_reason(clean_sentence), clean_sentence))
        else:
            sentence_effects = _extract_effects(sentence, row)
            for stat, value_expr, own_decrease in sentence_effects:
                try:
                    values = expand_value(value_expr, row.variables)
                except ValueError as exc:
                    notes.append(ReviewNote(row.skill_id, "값전개필요", f"{sentence} ({exc})"))
                    continue
                effect_condition, effect_values = _effect_condition_and_values(
                    row, stat, values, condition, own_decrease
                )
                key = (row.skill_id, stat, effect_condition, effect_values)
                if key not in seen:
                    seen.add(key)
                    effects.append(EffectRow(row.skill_id, stat, effect_condition, effect_values))

            if _needs_review(sentence, sentence_effects):
                notes.append(ReviewNote(row.skill_id, _review_reason(sentence), sentence))

    return effects, notes


def _manual_effect_rows(row: SkillRow) -> list[EffectRow] | None:
    if row.skill_id == "M_042":
        return [
            *(EffectRow(row.skill_id, stat, "ALWAYS", "11") for stat in BATTER_STATS),
            EffectRow(row.skill_id, "구위", "마에스트로누적", "12"),
            EffectRow(row.skill_id, "변화", "마에스트로누적", "12"),
        ]
    return None


def _parse_tier_effects(sentence: str, row: SkillRow) -> list[EffectRow] | None:
    for parser in (
        _parse_inning_tier_effects,
        _parse_plate_decay_effects,
        _parse_plate_accumulation_effects,
    ):
        effects = parser(sentence, row)
        if effects is not None:
            return effects
    return None


def _parse_inning_tier_effects(sentence: str, row: SkillRow) -> list[EffectRow] | None:
    range_matches = list(re.finditer(r"(\d+)\s*~\s*(\d+)\s*회\s*(\d+)\s*(?:증가|상승)", sentence))
    if not range_matches:
        return None

    stats = _inning_tier_stats(row.description)
    if not stats:
        return None

    effects: list[EffectRow] = []
    for match in range_matches:
        start, end, value = match.groups()
        condition = f"{start}_{end}회"
        for stat in stats:
            effects.append(EffectRow(row.skill_id, stat, condition, value))
    return effects


def _inning_tier_stats(description: str) -> list[str]:
    match = re.search(r"현재\s*이닝에\s*따라\s*(.*?)\s*능력치", description)
    if not match:
        return []
    return [stat for stat in STATS if stat in match.group(1)]


def _parse_plate_decay_effects(sentence: str, row: SkillRow) -> list[EffectRow] | None:
    if "첫 타석" not in sentence or "이후 타석마다" not in sentence:
        return None

    first_value_match = re.search(r"첫\s*타석.*?(\d+(?:\.\d+)?)\s*(?:증가|상승)", sentence)
    decrement_match = re.search(r"이후\s*타석마다\s*(\d+(?:\.\d+)?)\s*씩\s*감소", sentence)
    range_match = re.search(
        r"(\d+)\s*~\s*(\d+)(?:번째\s*)?타석.*?(\d+(?:\.\d+)?)\s*(?:증가|상승)",
        row.description,
    )
    if not first_value_match or not decrement_match or not range_match:
        return None

    stats = _stats_before_value(sentence[: first_value_match.start(1)], row)
    if not stats:
        return None

    first_value = float(first_value_match.group(1))
    decrement = float(decrement_match.group(1))
    range_start = int(range_match.group(1))
    range_end = int(range_match.group(2))
    range_value = range_match.group(3)

    effects: list[EffectRow] = []
    for plate in range(1, range_start):
        value = first_value - (plate - 1) * decrement
        for stat in stats:
            effects.append(EffectRow(row.skill_id, stat, f"타석{plate}", _format_number(value)))
    for stat in stats:
        effects.append(EffectRow(row.skill_id, stat, f"타석{range_start}_{range_end}", range_value))
    return effects


def _parse_plate_accumulation_effects(sentence: str, row: SkillRow) -> list[EffectRow] | None:
    if "타석에 들어설 때마다" not in sentence:
        return None

    value_match = re.search(r"타석에\s*들어설\s*때마다\s*(.*?)\s*능력치가\s*([yzabc]|\d+(?:\.\d+)?)\s*(?:증가|상승)", sentence)
    max_match = re.search(r"최대\s*(\d+(?:\.\d+)?)\s*까지만", row.description)
    plate_limit_match = re.search(r"(\d+)\s*타석\s*까지만", row.description)
    if not value_match or not max_match or not plate_limit_match:
        return None

    stats = [stat for stat in STATS if stat in value_match.group(1)]
    if not stats:
        return None

    try:
        values = [float(value) for value in expand_value(value_match.group(2), row.variables).split("/") if value != ""]
    except ValueError:
        return None

    max_value = float(max_match.group(1))
    plate_limit = int(plate_limit_match.group(1))
    effects: list[EffectRow] = []
    for plate in range(1, plate_limit + 1):
        plate_values = [min(value * plate, max_value) for value in values]
        condition = f"타석{plate}"
        if all(value == max_value for value in plate_values):
            condition = f"타석{plate}_{plate_limit}"
            value_text = _format_number(max_value)
            for stat in stats:
                effects.append(EffectRow(row.skill_id, stat, condition, value_text))
            break

        value_text = _format_values(plate_values)
        for stat in stats:
            effects.append(EffectRow(row.skill_id, stat, condition, value_text))
    return effects


def _format_values(values: list[float]) -> str:
    return "/".join(_format_number(value) for value in values)


def _is_tier_context_sentence(sentence: str) -> bool:
    normalized = sentence.strip(" ()")
    if normalized.startswith("현재 이닝에 따라") and "능력치" in normalized:
        return True
    if re.fullmatch(r"최대\s*\d+(?:\.\d+)?\s*까지만,\s*\d+\s*타석\s*까지만\s*적용", normalized):
        return True
    if re.fullmatch(r"\d+\s*~\s*\d+(?:번째\s*)?타석.*?\d+(?:\.\d+)?\s*(?:증가|상승).*", normalized):
        return True
    return False


def _remove_proportional_clause(sentence: str) -> str:
    pattern = r"(.*?(?:능력치|덱 스코어)의\s*[yzabc\d]+\s*%\s*만큼\s+.*?(?:증가합니다|증가하고|증가|상승합니다|상승하고|상승))"
    match = re.search(pattern, sentence)
    if match:
        return sentence.replace(match.group(1), "")
    return sentence


def _parse_proportional_effect(sentence: str, row: SkillRow) -> list[EffectRow] | None:
    if "만큼" not in sentence:
        return None
    
    makum_idx = sentence.find("만큼")
    before_makum = sentence[:makum_idx]
    after_makum = sentence[makum_idx:]
    
    if "%" not in before_makum:
        return None
        
    pct_idx = before_makum.rfind("%")
    var_match = re.search(r"([yzabc\d]+)\s*$", before_makum[:pct_idx])
    if not var_match:
        return None
    var_str = var_match.group(1)
    
    stat_part = before_makum[:pct_idx]
    
    if "스페셜 덱" in stat_part or "스페셜덱" in stat_part:
        base_stat = "스페셜덱"
    elif "팀 덱" in stat_part or "팀덱" in stat_part:
        base_stat = "팀덱"
    else:
        nl_idx = stat_part.rfind("능력치")
        if nl_idx != -1:
            base_part = stat_part[:nl_idx].strip()
        else:
            base_part = stat_part
            
        found_stats = []
        for stat in STATS:
            idx = base_part.find(stat)
            if idx != -1:
                found_stats.append((idx, stat))
        found_stats.sort()
        base_stat = "+".join(s[1] for s in found_stats)
        
    if not base_stat:
        return None
        
    increase_match = re.search(r"(?:증가|상승)", after_makum)
    if increase_match:
        target_part = after_makum[:increase_match.start()]
    else:
        target_part = after_makum
        
    target_stats = []
    for stat in STATS:
        if stat in target_part:
            target_stats.append(stat)
            
    if not target_stats:
        return None
        
    try:
        raw_values = expand_value(var_str, row.variables)
    except ValueError:
        return None
        
    divided_values_list = []
    for val in raw_values.split("/"):
        if val:
            try:
                divided_val = float(val) / 100.0
                divided_values_list.append(_format_number(divided_val))
            except ValueError:
                return None
    divided_values = "/".join(divided_values_list)
    
    condition = _condition_for(sentence)
    
    effects = []
    for t_stat in target_stats:
        effect_condition = _condition_for_effect(row, t_stat, divided_values, condition)
        effects.append(EffectRow(
            skill_id=row.skill_id,
            stat=t_stat,
            condition=effect_condition,
            values=divided_values,
            base_stat=base_stat
        ))
        
    return effects


def _condition_for_effect(row: SkillRow, stat: str, values: str, default_condition: str) -> str:
    return SPECIAL_EFFECT_CONDITIONS.get((row.skill_id, stat, values), default_condition)


def _effect_condition_and_values(
    row: SkillRow, stat: str, values: str, default_condition: str, own_decrease: bool = False
) -> tuple[str, str]:
    # 특수 조건표는 설명문에 적힌 양의 크기로 찾는다. 부호는 그 다음에 붙인다.
    condition = SPECIAL_EFFECT_CONDITIONS.get((row.skill_id, stat, values), default_condition)
    return condition, _negate(values) if own_decrease else values


def _negate(values: str) -> str:
    return "/".join(_format_number(-float(value)) if float(value) else value for value in values.split("/"))




def _format_number(value: float) -> str:
    if value.is_integer():
        return str(int(value))
    return f"{value:g}"


def _split_sentences(description: str) -> list[str]:
    normalized = description.replace("\n", " ").replace("．", ".")
    parts = re.split(r"(?<!\d)\.(?!\d)\s*", normalized)
    return [part.strip(" ,") for part in parts if part.strip(" ,")]


def _condition_for(sentence: str) -> str:
    conditions: list[str] = []

    modified_sentence = sentence

    # Parse SP slot constraints (e.g. 1,2선발, 3,4,5선발)
    sp_matches = re.findall(r"(\d+(?:[,\s·.]+?\d+)*)\s*선발", sentence)
    for m in sp_matches:
        numbers = re.findall(r"\d+", m)
        if numbers:
            conditions.append(f"선발{'_'.join(numbers)}")

    # Parse RP slot constraints (e.g. 3,4,5중계)
    rp_matches = re.findall(r"(\d+(?:[,\s·.]+?\d+)*)\s*중계", sentence)
    for m in rp_matches:
        numbers = re.findall(r"\d+", m)
        if numbers:
            conditions.append(f"중계{'_'.join(numbers)}")

    # Mask to prevent these numbers/words from matching position_SP or order_patterns
    modified_sentence = re.sub(r"\d+(?:[,\s·.]+?\d+)*\s*선발", "SP_SLOT_MASK", modified_sentence)
    modified_sentence = re.sub(r"\d+(?:[,\s·.]+?\d+)*\s*중계", "RP_SLOT_MASK", modified_sentence)

    checks = [
        ("좌투수", "좌투상대"),
        ("우투수", "우투상대"),
        ("좌타자", "좌타상대"),
        ("좌타자인", "좌타상대"),
        ("우타자", "우타상대"),
        ("주자가 2루 이상", "주자2루이상"),
        ("주자가 있을", "주자있음"),
        ("주자가 있는", "주자있음"),
        ("주자가 1명이라도", "주자있음"),
        ("주자가 없", "주자없음"),
        ("홈 경기", "홈"),
        ("원정", "원정"),
        ("OVR이 더 높", "OVR열세"),
        ("ovr이 더 높", "OVR열세"),
        ("OVR보다 높", "OVR열세"),
        ("ovr보다 높", "OVR열세"),
        ("OVR이 더 낮", "OVR우세"),
        ("ovr이 더 낮", "OVR우세"),
        ("덱 스코어가 더 높", "덱스코어열세"),
        ("스페셜 덱 스코어가 더 높", "덱스코어열세"),
        ("나보다 높은 등급", "상대등급우세"),
        ("홈런이 3개 이상", "홈런3이상"),
        ("평균 발사각", "발사각조건"),
        ("좋은 타격 타이밍", "스윗스팟"),
        ("풀카운트", "풀카운트"),
        ("초구", "초구"),
        ("2아웃", "2아웃"),
        ("상대 팀이 홈런 3개", "상대팀홈런3"),
        ("출루시킨 타자가 2인 이상", "이닝출루2인이상"),
        ("스트라이크 타격", "스트라이크타격"),
        ("직구 상대", "직구상대"),
        ("속구 선택", "속구선택"),
        ("변화구 상대", "변화구상대"),
        ("변화구 선택", "변화구선택"),
        ("랭킹 대전", "모드_랭킹대전"),
        ("리그 모드", "모드_리그"),
        ("리그모드", "모드_리그"),
        ("클럽 대전", "모드_클럽"),
        ("라이브 매치", "모드_라이브매치"),
        ("타점 배틀", "모드_타점배틀"),
        ("랭킹 슬러거", "모드_랭킹슬러거"),
        ("5회 이후", "5회이후"),
        ("6회 이후", "6회이후"),
        ("7회 이후", "7회이후"),
        ("8회 이후", "8회이후"),
        ("이기고 있을", "리드"),
        ("지고 있을", "리드아님"),
        ("비기거나 리드", "비김또는리드"),
        ("비기거나 지고", "비김또는열세"),
        ("DH", "포지션_DH"),
        ("포수", "포지션_C"),
        ("LF", "포지션_OF"),
        ("CF", "포지션_OF"),
        ("RF", "포지션_OF"),
        ("SS", "포지션_SS"),
        ("선발", "포지션_SP"),
        ("중계", "포지션_RP_CP"),
        ("마무리", "포지션_RP_CP"),
    ]
    for needle, condition in checks:
        if needle in modified_sentence and condition not in conditions:
            conditions.append(condition)

    for condition in _pitching_window_conditions(modified_sentence):
        if condition not in conditions:
            conditions.append(condition)

    for condition in _count_conditions(modified_sentence):
        if condition not in conditions:
            conditions.append(condition)

    for inning_until in re.findall(r"(\d+)\s*회\s*까지", modified_sentence):
        condition = f"{inning_until}회까지"
        if condition not in conditions:
            conditions.append(condition)

    for condition in _batting_order_conditions(modified_sentence):
        if condition not in conditions:
            conditions.append(condition)

    return "+".join(conditions) if conditions else "ALWAYS"


# 타순 목록 하나가 토큰 하나다. 예전에는 패턴을 하나씩 따로 찾아서 "3, 4, 5"가 `타순3_4_5`와
# `타순4_5`로, "6, 7, 8, 9"가 `타순6_9`와 `타순8_9`로 동시에 잡혔다. 둘은 `+`(그리고)로 묶이므로
# 3번 타자·6~7번 타자에서는 발동하지 않는 것으로 계산됐다.
BATTING_ORDER_TOKENS = {
    (1,): "타순1",
    (2,): "타순2",
    (3,): "타순3",
    (1, 2): "타순1_2",
    (2, 3): "타순2_3",
    (3, 4, 5): "타순3_4_5",
    (4, 5): "타순4_5",
    (6, 7, 8, 9): "타순6_9",
    (8, 9): "타순8_9",
    (1, 2, 6, 7, 8, 9): "타순1_2_6_7_8_9",
}
# 투수 스킬의 "상대 3, 4, 5 타순 상대 시"는 내 타순이 아니라 마주한 타자의 타순이다.
OPPONENT_BATTING_ORDER_TOKENS = {
    (3, 4, 5): "상대타순3_4_5",
    (6, 7, 8, 9): "상대타순6_9",
}
# "3, 4, 5 타순", "6/7/8/9 번 타순", "클린업(3, 4, 5번)". 괄호는 `번)`일 때만 본다 — 공지의
# 레벨 사다리 `(1/2/3/4/5/6)`를 타순으로 읽지 않게.
BATTING_ORDER_LIST_RE = re.compile(r"(\d(?:\s*[,/]\s*\d)*)\s*(?:번\s*)?(?:타순|타선)|(\d(?:\s*[,/]\s*\d)*)\s*번\)")
BATTING_ORDER_WORDS = {"상위타선": (1, 2), "클린업": (3, 4, 5), "하위타선": (6, 7, 8, 9)}


def _batting_order_conditions(sentence: str) -> list[str]:
    """문장의 타순 목록을 통째로 읽어 토큰으로 바꾼다. 모르는 목록은 건너뛴다."""
    found: list[tuple[tuple[int, ...], bool]] = []
    for match in BATTING_ORDER_LIST_RE.finditer(sentence):
        orders = tuple(sorted({int(n) for n in re.findall(r"\d", match.group(1) or match.group(2))}))
        opponent = "상대" in sentence[max(0, match.start() - 4) : match.start()]
        found.append((orders, opponent))
    if not found:
        found = [(orders, False) for word, orders in BATTING_ORDER_WORDS.items() if word in sentence]
    conditions: list[str] = []
    for orders, opponent in found:
        token = (OPPONENT_BATTING_ORDER_TOKENS if opponent else BATTING_ORDER_TOKENS).get(orders)
        if token and token not in conditions:
            conditions.append(token)
    return conditions


PITCHING_WINDOW_NUMBERS = {"세": 3, "네": 4, "일곱": 7, "아홉": 9}
PITCHING_WINDOW_RE = re.compile(r"등판\s*(?:후|시)\s*(?:상대하는\s*)?(\d+|세|네|일곱|아홉)\s*타자\s*동안")


def _pitching_window_conditions(sentence: str) -> list[str]:
    """"등판 후 상대하는 3 타자동안", "등판 시 세타자 동안" 같은 등판 직후 구간.

    구간 표현이 앞 효과 절 뒤에 오면("7 증가하고 등판 후 9타자 동안 추가로 5") 문장 전체에 걸면
    앞 절까지 구간에 묶인다. 그런 문장은 건너뛰고 [SPECIAL_EFFECT_CONDITIONS]로 행마다 정한다.
    """
    conditions: list[str] = []
    for match in PITCHING_WINDOW_RE.finditer(sentence):
        if re.search(r"증가|감소", sentence[: match.start()]):
            continue
        word = match.group(1)
        batters = int(word) if word.isdigit() else PITCHING_WINDOW_NUMBERS[word]
        condition = f"등판후{batters}타자"
        if condition not in conditions:
            conditions.append(condition)
    return conditions


def _count_conditions(sentence: str) -> list[str]:
    """볼 카운트 조건.

    "1스트라이크 이후"는 1스트라이크 이상(2스트라이크 포함)이라 "딱 1스트라이크"와 다른 토큰이다.
    원문은 "2 스트라이크"처럼 띄어 쓰기도 한다. "볼 카운트 0-0, 0-1, 1-0"은 빠른 카운트다.
    """
    conditions: list[str] = []
    if re.search(r"1\s*스트라이크\s*이후", sentence):
        conditions.append("1스트라이크이후")
    elif re.search(r"1\s*스트라이크", sentence):
        conditions.append("1스트라이크")
    if re.search(r"2\s*스트라이크", sentence):
        conditions.append("2스트라이크")
    if re.search(r"0-0\s*,\s*0-1\s*,\s*1-0", sentence):
        conditions.append("빠른카운트")
    return conditions


def _extract_effects(sentence: str, row: SkillRow) -> list[tuple[str, str, bool]]:
    """(스탯, 값 표현, 자기감소 여부)를 뽑는다.

    자기감소는 부호가 뒤집힌다. 상대 능력치를 깎는 것은 내 이득이라 양수로 두지만,
    "파워 능력치가 3 감소합니다"처럼 내 능력치가 깎이는 절은 점수에서 빠져야 한다.
    """
    effects: list[tuple[str, str, bool]] = []
    for match in VALUE_RE.finditer(sentence):
        value_expr = match.group(0)
        if not _looks_like_effect_value(sentence, match):
            continue
        before = sentence[: match.start()]
        decreases = _effect_verb(sentence, match) == "감소"
        stats = _stats_before_value(before, row)
        for stat in stats:
            effects.append((stat, value_expr, decreases and _is_own_decrease(stat, row, before)))
    return effects


def _effect_verb(sentence: str, match: re.Match[str]) -> str | None:
    """값 뒤에 처음 나오는 효과 동사. 한 문장에 증가절과 감소절이 같이 오기 때문이다."""
    found = re.search(r"증가|감소|상승", sentence[match.end() :])
    return found.group(0) if found else None


def _is_own_decrease(stat: str, row: SkillRow, before: str) -> bool:
    """감소 절이 선수 자신의 이 스탯을 깎는지.

    감소 절 안에 스탯이 직접 적혀 있고 '상대'가 없을 때만 그렇다고 본다.
    [_stats_before_value]는 절에 스탯이 없으면 앞 절에서 끌어오는데("정확 능력치가 a
    증가하고 상대 투수의 변화 능력치를 추가로 a 감소"의 정확), 그걸 자기 감소로 읽으면
    멀쩡한 증가를 음수로 뒤집는다.
    """
    own, opponent = _stat_sides(row)
    if stat in opponent and stat not in own:
        # 타자에게 구위는 상대 것이다. 깎이면 이득이다.
        return False
    clause = re.split(r"하고|하며|시키고|경우|때", before)[-1]
    named = stat in clause or "모든 능력치" in clause
    return named and "상대" not in clause


def _stat_sides(row: SkillRow) -> tuple[list[str], list[str]]:
    """(자기 쪽 스탯, 상대 쪽 스탯)."""
    if _is_pitcher(row):
        return PITCHER_STATS, BATTER_STATS
    return BATTER_STATS, PITCHER_STATS


def _is_pitcher(row: SkillRow) -> bool:
    # 포지션은 "SP, RP"처럼 여럿이 올 수 있고 "SP. RP" 같은 오타도 있다.
    atoms = {atom.strip() for atom in re.split(r"[,.]", row.position.upper())}
    return bool(atoms & {"PITCHER", "SP", "RP", "CP"})


def _looks_like_effect_value(sentence: str, match: re.Match[str]) -> bool:
    before = sentence[max(0, match.start() - 40) : match.start()]
    after = sentence[match.end() : min(len(sentence), match.end() + 35)]
    if re.match(r"\s*(타자|타석|이닝|회)\s*(동안|까지|까지만)", after):
        return False
    # "모든 구종 등급이 1단계 상승"은 능력치가 아니라 구종 등급이다.
    if re.match(r"\s*단계", after):
        return False
    has_stat_context = any(stat in before for stat in STATS) or "모든 능력치" in before
    has_effect_verb = any(verb in after for verb in ["증가", "감소", "상승"])
    if not has_stat_context or not has_effect_verb:
        return False
    if "발사각" in before[-12:] or "확률" in before[-12:]:
        return False
    return True


def _stats_before_value(before: str, row: SkillRow) -> list[str]:
    window = before
    for separator in ["추가로", "하고", "하며", "경우", "때", "시"]:
        idx = window.rfind(separator)
        if idx != -1:
            window = window[idx + len(separator) :]
    previous_values = list(VALUE_RE.finditer(window))
    if previous_values:
        window = window[previous_values[-1].end() :]
    if "모든 능력치" in window or "모든 능력치" in before[-25:]:
        return _all_stats_for(row, before)
    found = [stat for stat in STATS if stat in window]
    if found:
        return found
    # A comma split can hide earlier stats in grouped phrases such as "파워, 정확 능력치가".
    tail = before[-35:]
    return [stat for stat in STATS if stat in tail]


def _all_stats_for(row: SkillRow, text: str) -> list[str]:
    """'모든 능력치'가 누구의 것인지 바로 앞 주어로 가른다.

    문장 어디엔가 '상대 투수'가 있는지로 보면 안 된다. "상대 투수의 OVR이 더 높은 경우
    모든 능력치가 5 증가"는 제 능력치가 오르는 절인데 상대 스탯으로 뒤집힌다.
    """
    own, opponent = _stat_sides(row)
    subject = text[: text.rfind("모든 능력치")] if "모든 능력치" in text else ""
    if re.search(r"상대\s*투수\s*의?\s*$", subject):
        return PITCHER_STATS
    if re.search(r"상대\s*타자\s*의?\s*$", subject):
        return BATTER_STATS
    if re.search(r"상대\s*의?\s*$", subject):
        return opponent
    return own


def _needs_review(sentence: str, sentence_effects: list[tuple[str, str, bool]]) -> bool:
    if not sentence_effects:
        return True
    if any(token in sentence for token in ["덱 스코어가 더 높", "스페셜 덱 스코어가 더 높", "나보다 높은 등급", "홈런이 3개 이상"]):
        return False
    return any(token in sentence for token in ["%만큼", "확률", "평균 발사각", "타구", "등급", "팀의 스페셜 덱"])


def _review_reason(sentence: str) -> str:
    if any(token in sentence for token in ["체크스윙", "확률", "타구", "등급", "평균 발사각"]):
        return "비스탯효과"
    if "%만큼" in sentence or "1%만큼" in sentence:
        return "상대스탯비례"
    if "감소" in sentence and "상대" not in sentence and "타자의" not in sentence and "투수의" not in sentence:
        return "자기감소"
    return "수동검토"
