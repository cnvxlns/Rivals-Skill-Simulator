from __future__ import annotations

import csv
import posixpath
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from zipfile import ZipFile


X_LEVELS = list(range(1, 10))
STATS = ["파워", "정확", "선구", "인내", "구위", "변화", "제구", "구속", "주루", "수비", "지구력"]
BATTER_STATS = ["파워", "정확", "선구", "인내", "주루", "수비"]
PITCHER_STATS = ["구속", "구위", "변화", "제구", "지구력", "수비"]
STAT_WEIGHTS = {
    "파워": "1.10",
    "정확": "0.90",
    "선구": "0.40",
    "구위": "1.20",
    "변화": "1.15",
    "제구": "0.00",
    "인내": "0.00",
    "구속": "0.00",
    "주루": "0.00",
    "수비": "0.00",
    "지구력": "0.00",
}

SPECIAL_EFFECT_CONDITIONS = {
    ("M_029", "구위", "5"): "등판후9타자",
    ("M_029", "변화", "5"): "등판후9타자",
    ("M_029", "제구", "5"): "등판후9타자",
    ("M_032", "파워", "5"): "두번째타석까지",
    ("M_032", "정확", "5"): "두번째타석까지",
    ("M_032", "선구", "5"): "두번째타석까지",
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

SPECIAL_EFFECT_VALUES = {
    ("G_028", "파워", "4"): "-4",
}

CARD_TYPES = {
    "normal": "NORMAL",
    "moment": "MOMENT",
    "WBC": "WBC",
    "black": "BLACK",
    "HOF": "HOF",
}

NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "rel": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
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
                for stat, value_expr in sentence_effects:
                    try:
                        values = expand_value(value_expr, row.variables)
                    except ValueError as exc:
                        notes.append(ReviewNote(row.skill_id, "값전개필요", f"{clean_sentence} ({exc})"))
                        continue
                    effect_condition, effect_values = _effect_condition_and_values(row, stat, values, condition)
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
            for stat, value_expr in sentence_effects:
                try:
                    values = expand_value(value_expr, row.variables)
                except ValueError as exc:
                    notes.append(ReviewNote(row.skill_id, "값전개필요", f"{sentence} ({exc})"))
                    continue
                effect_condition, effect_values = _effect_condition_and_values(row, stat, values, condition)
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


def _effect_condition_and_values(row: SkillRow, stat: str, values: str, default_condition: str) -> tuple[str, str]:
    key = (row.skill_id, stat, values)
    return SPECIAL_EFFECT_CONDITIONS.get(key, default_condition), SPECIAL_EFFECT_VALUES.get(key, values)


def read_skill_rows(xlsx_path: Path) -> list[SkillRow]:
    rows: list[SkillRow] = []
    for sheet_name, data in _iter_xlsx_rows(xlsx_path):
        skill_id = data.get("id", "").strip()
        if not skill_id:
            continue
        card_type = CARD_TYPES.get(sheet_name, sheet_name.upper())
        name = (data.get("name_kor") or data.get("이름") or "").strip()
        variables = {key: data.get(key, "").strip() for key in ["y", "z", "a", "b", "c"]}
        rows.append(
            SkillRow(
                skill_id=skill_id,
                card_type=card_type,
                position=data.get("exclusive", "").strip(),
                name=name,
                description=data.get("explanation_kor", "").strip(),
                variables=variables,
            )
        )
    return rows


def write_outputs(
    rows: list[SkillRow],
    resources_dir: Path,
    review_path: Path,
) -> tuple[int, int, int]:
    resources_dir.mkdir(parents=True, exist_ok=True)
    review_path.parent.mkdir(parents=True, exist_ok=True)

    skills_path = resources_dir / "score_skills.csv"
    effects_path = resources_dir / "score_effects.csv"
    weights_path = resources_dir / "stat_weights.csv"

    with skills_path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(["skill_id", "card_type", "position", "name", "description"])
        for row in rows:
            writer.writerow([row.skill_id, row.card_type, row.position, row.name, row.description])

    effect_rows: list[EffectRow] = []
    review_notes: list[ReviewNote] = []
    for row in rows:
        effects, notes = build_effect_rows(row)
        effect_rows.extend(effects)
        review_notes.extend(notes)

    with effects_path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(["skill_id", "stat", "condition", "values", "base_stat"])
        for effect in effect_rows:
            writer.writerow([effect.skill_id, effect.stat, effect.condition, effect.values, effect.base_stat])

    with weights_path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(["stat", "weight"])
        for stat, weight in STAT_WEIGHTS.items():
            writer.writerow([stat, weight])

    with review_path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(["skill_id", "reason", "text"])
        for note in review_notes:
            writer.writerow([note.skill_id, note.reason, note.text])

    return len(rows), len(effect_rows), len(review_notes)


def main() -> None:
    docs_dir = Path(__file__).resolve().parent
    project_dir = docs_dir.parent
    rows = read_skill_rows(docs_dir / "rivals_skills.xlsx")
    skill_count, effect_count, review_count = write_outputs(
        rows=rows,
        resources_dir=project_dir / "backend" / "src" / "main" / "resources",
        review_path=docs_dir / "score_effects_manual_review.csv",
    )
    print(f"score_skills.csv rows: {skill_count}")
    print(f"score_effects.csv rows: {effect_count}")
    print(f"score_effects_manual_review.csv rows: {review_count}")


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
        ("1스트라이크", "1스트라이크"),
        ("2스트라이크", "2스트라이크"),
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

    for inning_until in re.findall(r"(\d+)\s*회\s*까지", modified_sentence):
        condition = f"{inning_until}회까지"
        if condition not in conditions:
            conditions.append(condition)

    order_patterns = [
        (r"1\s*,\s*2번|1,\s*2번|1\s*,\s*2\s*타순|상위타선", "타순1_2"),
        (r"1번 타순", "타순1"),
        (r"2\s*,\s*3", "타순2_3"),
        (r"3\s*,\s*4\s*,\s*5|클린업", "타순3_4_5"),
        (r"4\s*,\s*5", "타순4_5"),
        (r"6\s*,\s*7\s*,\s*8\s*,\s*9|6/7/8/9|하위타선", "타순6_9"),
        (r"8\s*,\s*9", "타순8_9"),
    ]
    for pattern, condition in order_patterns:
        if re.search(pattern, modified_sentence) and condition not in conditions:
            conditions.append(condition)

    return "+".join(conditions) if conditions else "ALWAYS"


def _extract_effects(sentence: str, row: SkillRow) -> list[tuple[str, str]]:
    effects: list[tuple[str, str]] = []
    for match in VALUE_RE.finditer(sentence):
        value_expr = match.group(0)
        if not _looks_like_effect_value(sentence, match):
            continue
        stats = _stats_before_value(sentence[: match.start()], row)
        for stat in stats:
            effects.append((stat, value_expr))
    return effects


def _looks_like_effect_value(sentence: str, match: re.Match[str]) -> bool:
    before = sentence[max(0, match.start() - 40) : match.start()]
    after = sentence[match.end() : min(len(sentence), match.end() + 35)]
    if re.match(r"\s*(타자|타석|이닝|회)\s*(동안|까지|까지만)", after):
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
    if "상대 투수" in text:
        return PITCHER_STATS
    if "상대 타자" in text or "타자의" in text:
        return BATTER_STATS
    if row.position in {"PITCHER", "SP", "RP", "CP", "RP, CP", "SP. RP"}:
        return PITCHER_STATS
    return BATTER_STATS


def _needs_review(sentence: str, sentence_effects: list[tuple[str, str]]) -> bool:
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


def _load_shared_strings(archive: ZipFile) -> list[str]:
    # 엑셀은 셀 텍스트를 xl/sharedStrings.xml 에 모아두고 셀에서는 t="s" + 인덱스로 참조한다.
    # (인라인 문자열 전용 export 파일에는 이 파트가 없을 수 있으므로 없으면 빈 목록.)
    try:
        data = archive.read("xl/sharedStrings.xml")
    except KeyError:
        return []
    root = ET.fromstring(data)
    return [
        "".join(text.text or "" for text in si.findall(".//main:t", NS))
        for si in root.findall("main:si", NS)
    ]


def _iter_xlsx_rows(xlsx_path: Path) -> Iterable[tuple[str, dict[str, str]]]:
    with ZipFile(xlsx_path) as archive:
        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        rels_root = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        shared_strings = _load_shared_strings(archive)

        def _resolve_target(target: str) -> str:
            # rels 의 Target 은 소유 파트(xl/workbook.xml)가 있는 'xl/' 폴더 기준 상대경로다.
            # 절대(/로 시작)면 앞 슬래시만 제거, 아니면 'xl/'과 정규 결합한다(엑셀 저장 툴마다
            # '/xl/worksheets/sheet1.xml' 또는 'worksheets/sheet1.xml' 둘 다 나옴).
            if target.startswith("/"):
                return target.lstrip("/")
            return posixpath.normpath(posixpath.join("xl", target))

        rels = {rel.attrib["Id"]: _resolve_target(rel.attrib["Target"]) for rel in rels_root}
        for sheet in workbook.findall("main:sheets/main:sheet", NS):
            sheet_name = sheet.attrib["name"]
            rel_id = sheet.attrib[f"{{{NS['rel']}}}id"]
            worksheet = ET.fromstring(archive.read(rels[rel_id]))
            rows = [_read_xlsx_row(row, shared_strings) for row in worksheet.findall("main:sheetData/main:row", NS)]
            if not rows:
                continue
            headers = rows[0]
            for values in rows[1:]:
                values = values + [""] * (len(headers) - len(values))
                yield sheet_name, dict(zip(headers, values))


def _read_xlsx_row(row: ET.Element, shared_strings: list[str]) -> list[str]:
    values: list[str] = []
    for cell in row.findall("main:c", NS):
        idx = _column_index(cell.attrib.get("r", "A1"))
        while len(values) <= idx:
            values.append("")
        values[idx] = _cell_value(cell, shared_strings)
    return values


def _column_index(cell_ref: str) -> int:
    number = 0
    for char in "".join(char for char in cell_ref if char.isalpha()):
        number = number * 26 + (ord(char.upper()) - 64)
    return number - 1


def _cell_value(cell: ET.Element, shared_strings: list[str]) -> str:
    cell_type = cell.attrib.get("t")
    if cell_type == "inlineStr":
        return "".join(text.text or "" for text in cell.findall(".//main:t", NS))
    value = cell.find("main:v", NS)
    if value is None:
        return ""
    raw = value.text or ""
    if cell_type == "s":
        # 공유 문자열: <v> 는 sharedStrings 인덱스다. 실제 텍스트로 치환.
        try:
            return shared_strings[int(raw)]
        except (ValueError, IndexError):
            return ""
    return raw


if __name__ == "__main__":
    main()
