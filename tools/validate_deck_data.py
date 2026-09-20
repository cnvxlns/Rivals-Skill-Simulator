#!/usr/bin/env python3
"""덱 계산 CSV 5종의 불변식을 검사한다.

`validate_skill_csv.py`와 같은 이유로 있다 — 백엔드 로더는 깨진 행을 경고만 남기고 넘어가서
잘못된 데이터가 "조용히 틀린 점수"로만 드러난다. 그 판정을 CI로 앞당긴다.

스킬 CSV 검사기와 합치지 않고 따로 둔 이유는 어휘가 겹치지 않아서다. 저쪽은 스킬·효과·조건
토큰을, 이쪽은 카드 등급·덱 스코어 티어·판정식을 본다.

    python3 tools/validate_deck_data.py          # 오류가 있으면 exit 1
    python3 tools/validate_deck_data.py --warn   # 경고까지 표시

원천인 워크북은 추적하지 않으므로 CI에서 `deck_workbook.py --check`를 돌릴 수 없다.
커밋된 CSV를 손으로 고쳤을 때 막아 주는 것은 여기가 유일하다.

의존성 없이 stdlib만 쓴다. tools/ 아래 도구는 전부 같은 방침이다.
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "backend" / "src" / "main" / "resources"

# --- 어휘 -------------------------------------------------------------------
#
# "지금 데이터에 있는 것"이 아니라 "허용하기로 한 것"이다. 새 값이 생기면 여기에 손으로
# 더해야 통과하는데, 그 추가가 곧 검토 시점이다.

CARD_GRADES = frozenset(
    [
        "SEASON",
        "LIVE",
        "IMPACT",
        "PRIME",
        "MOMENT",
        "SUPREME_MOMENT",
        "SIGNATURE",
        "SIGNATURE_BLACK",
        "HOF",
    ]
)
CARD_VARIANTS = frozenset(["NONE", "FA", "WBC"])
GROWTH_STATS = frozenset(["파워", "정확", "선구", "변화", "구위"])

# 사다리 상한. 게임의 실제 상한이라 표가 짧은 것은 오류가 아니다.
TRANSCENDENCE_MAX = 16  # 레벨 0~15
ENHANCEMENT_MAX = 20  # 레벨 1~20

LADDERS = {
    "NORMAL": 9,
    "HOF": 6,
    "BLACK": 7,
    "WBC": 3,
    "MOMENT": 1,
}

TEAM_THRESHOLDS = frozenset(
    [200, 240, 260, 280, 300, 315, 330, 345, 360, 375, 390, 405, 420, 435,
     450, 460, 470, 480, 490, 500, 520, 540, 560, 580, 600]
)
SPECIAL_THRESHOLDS = frozenset(
    [100, 150, 200, 220, 240, 260, 280, 300, 320, 340, 360, 380, 400, 420, 440,
     460, 480, 500, 520, 540, 560, 580, 600, 615, 630, 645, 660, 680, 700]
)
# 연대를 고르는 티어. 워크북은 680(AY37)을 "O"로 검증하지만 수식은 연도를 본다.
# 수식을 따른다 — "O"를 넣으면 엑셀에서 #VALUE!가 나기 때문이다.
DECADE_THRESHOLDS = frozenset([615, 645, 680])

REWARD_CONDITIONS = frozenset(
    [
        "ALWAYS",
        "CARD_LIVE_SEASON",
        "CARD_NOT_LIVE_SEASON",
        "ORDER_1_2",
        "ORDER_3_5",
        "ORDER_6_9",
        "ENHANCE_GTE_10",
        "DECADE",
    ]
)

REWARD_TARGET_GROUPS = frozenset(["BATTER_ALL", "PITCHER_ALL", "SP", "RP", "RP_CP", "CP"])
REWARD_TARGET_SLOTS = frozenset(
    ["C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH",
     "SP1", "SP2", "SP3", "SP4", "SP5", "RP1", "RP2", "RP3", "CP1"]
)

TEAM_BUFF_SCOPES = frozenset(["BATTER", "PITCHER"])

POSITIONS = frozenset(
    ["C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH", "IF", "OF"]
)
ROLES = frozenset(["SP", "RP", "CP"])
RELIEVER_ROLES = frozenset(["WIN", "CHASE", "LONG"])
HANDS = frozenset(["LEFT", "RIGHT", "SWITCH"])
# 판정식이 볼 수 있는 스탯. 합산 스탯(`주루+수비`)과 덱 스코어 스탯도 온다.
SELECTOR_STATS = frozenset(
    ["파워", "정확", "선구", "인내", "주루", "수비", "구위", "변화", "제구", "구속", "지구력",
     "주루+수비", "정확+주루", "변화+제구", "스페셜덱", "팀덱"]
)


@dataclass
class Problem:
    level: str  # ERROR / WARN
    code: str
    where: str
    message: str

    def render(self) -> str:
        return f"{self.level} [{self.code}] {self.where} {self.message}"


# --- 판정식 문법 -------------------------------------------------------------
#
# 백엔드가 읽는 것과 같은 문법이다. 여기서 막지 않으면 모르는 판정식이 런타임에
# 조용히 "안 맞음"으로 떨어져 점수가 틀린다.

RANGE = r"\d+(?:\.\.\d+)?"
ATOM_PATTERNS = [
    re.compile(rf"ORDER={RANGE}$"),
    re.compile(rf"PSLOT={RANGE}$"),
    re.compile(r"POS=(?P<pos>[A-Z0-9]+)$"),
    re.compile(r"ROLE=(?P<roles>[A-Z]+(?:\|[A-Z]+)*)$"),
    re.compile(r"RELIEVER=(?P<rel>[A-Z]+)$"),
    re.compile(r"BAT=(?P<bat>[A-Z]+)$"),
    re.compile(r"THROW=(?P<throw>[A-Z]+)$"),
    re.compile(r"GRADE=(?P<grade>[A-Z_]+)$"),
    re.compile(rf"(?:BASE)?STAT\((?P<stat>[^)]+)\)(?:={RANGE}|>=\d+|<\d+)$"),
]


def check_selector(selector: str, where: str, problems: list[Problem]) -> None:
    if selector in ("ALWAYS", "MANUAL"):
        return
    if not selector:
        problems.append(Problem("ERROR", "EMPTY_SELECTOR", where, "판정식이 비었다"))
        return
    for term in selector.split("&"):
        atom = term[1:] if term.startswith("!") else term
        for pattern in ATOM_PATTERNS:
            m = pattern.fullmatch(atom)
            if not m:
                continue
            groups = m.groupdict()
            if groups.get("pos") and groups["pos"] not in POSITIONS:
                problems.append(Problem("ERROR", "BAD_SELECTOR", where, f"모르는 포지션: {atom}"))
            if groups.get("roles") and not set(groups["roles"].split("|")) <= ROLES:
                problems.append(Problem("ERROR", "BAD_SELECTOR", where, f"모르는 역할: {atom}"))
            if groups.get("rel") and groups["rel"] not in RELIEVER_ROLES:
                problems.append(Problem("ERROR", "BAD_SELECTOR", where, f"모르는 중계 역할: {atom}"))
            for key in ("bat", "throw"):
                if groups.get(key) and groups[key] not in HANDS:
                    problems.append(Problem("ERROR", "BAD_SELECTOR", where, f"모르는 투타: {atom}"))
            if groups.get("grade") and groups["grade"] not in CARD_GRADES:
                problems.append(Problem("ERROR", "BAD_SELECTOR", where, f"모르는 등급: {atom}"))
            if groups.get("stat") and groups["stat"] not in SELECTOR_STATS:
                problems.append(Problem("ERROR", "BAD_SELECTOR", where, f"모르는 스탯: {atom}"))
            break
        else:
            problems.append(Problem("ERROR", "BAD_SELECTOR", where, f"모르는 판정식: {atom}"))


# --- 검사 -------------------------------------------------------------------


def _rows(path: Path) -> list[tuple[int, dict[str, str]]]:
    with path.open(encoding="utf-8") as fh:
        return list(enumerate(csv.DictReader(fh), start=2))


def _ladder(raw: str) -> list[float] | None:
    try:
        return [float(v) for v in raw.split("/")]
    except ValueError:
        return None


def check_growth(path: Path, maximum: int, problems: list[Problem]) -> None:
    if not path.exists():
        problems.append(Problem("ERROR", "MISSING_FILE", path.name, "파일이 없다"))
        return
    seen: dict[tuple[str, str], set[str]] = {}
    for line, row in _rows(path):
        where = f"{path.name}:{line}"
        grade, variant, stat = row["card_grade"], row["card_variant"], row["stat"]
        if grade not in CARD_GRADES:
            problems.append(Problem("ERROR", "BAD_GRADE", where, f"모르는 등급: {grade}"))
        if variant not in CARD_VARIANTS:
            problems.append(Problem("ERROR", "BAD_VARIANT", where, f"모르는 변형: {variant}"))
        if stat not in GROWTH_STATS:
            problems.append(Problem("ERROR", "BAD_STAT", where, f"모르는 스탯: {stat}"))
        values = _ladder(row["values"])
        if values is None:
            problems.append(Problem("ERROR", "BAD_VALUES", where, f"숫자가 아니다: {row['values']}"))
            continue
        if not 1 <= len(values) <= maximum:
            problems.append(
                Problem("ERROR", "BAD_LADDER_LEN", where, f"사다리 길이 {len(values)} (최대 {maximum})")
            )
        if any(b < a for a, b in zip(values, values[1:])):
            # 누적 표다. 레벨을 올렸는데 능력치가 줄 수는 없다.
            problems.append(Problem("ERROR", "NOT_CUMULATIVE", where, f"값이 줄어든다: {row['values']}"))
        key = (grade, variant)
        if stat in seen.get(key, set()):
            problems.append(Problem("ERROR", "DUP_ROW", where, f"{grade}/{variant}/{stat}이 두 번"))
        seen.setdefault(key, set()).add(stat)
    for key, stats in seen.items():
        missing = GROWTH_STATS - stats
        if missing:
            problems.append(
                Problem("ERROR", "MISSING_STAT", path.name, f"{key[0]}/{key[1]}에 {sorted(missing)}가 없다")
            )


def check_rewards(path: Path, problems: list[Problem]) -> None:
    if not path.exists():
        problems.append(Problem("ERROR", "MISSING_FILE", path.name, "파일이 없다"))
        return
    seen: set[tuple[str, ...]] = set()
    decade_tiers: set[tuple[str, int]] = set()
    for line, row in _rows(path):
        where = f"{path.name}:{line}"
        ladder, side = row["ladder"], row["side"]
        if ladder not in ("TEAM", "SPECIAL"):
            problems.append(Problem("ERROR", "BAD_LADDER", where, f"모르는 사다리: {ladder}"))
            continue
        if side not in ("LEFT", "RIGHT"):
            problems.append(Problem("ERROR", "BAD_SIDE", where, f"모르는 방향: {side}"))
        try:
            threshold = int(row["threshold"])
        except ValueError:
            problems.append(Problem("ERROR", "BAD_THRESHOLD", where, row["threshold"]))
            continue
        allowed = TEAM_THRESHOLDS if ladder == "TEAM" else SPECIAL_THRESHOLDS
        if threshold not in allowed:
            problems.append(Problem("ERROR", "BAD_THRESHOLD", where, f"{ladder}에 없는 임계값 {threshold}"))
        target = row["target"]
        if target not in REWARD_TARGET_GROUPS and not set(target.split("|")) <= REWARD_TARGET_SLOTS:
            problems.append(Problem("ERROR", "BAD_TARGET", where, f"모르는 대상: {target}"))
        if row["stat"] not in GROWTH_STATS:
            problems.append(Problem("ERROR", "BAD_STAT", where, f"모르는 스탯: {row['stat']}"))
        try:
            amount = int(row["amount"])
        except ValueError:
            problems.append(Problem("ERROR", "BAD_AMOUNT", where, row["amount"]))
            continue
        if not 1 <= amount <= 5:
            problems.append(Problem("ERROR", "BAD_AMOUNT", where, f"보정치가 {amount}"))
        condition = row["condition"]
        if condition not in REWARD_CONDITIONS:
            problems.append(Problem("ERROR", "BAD_CONDITION", where, f"모르는 조건: {condition}"))
        if condition == "DECADE":
            if ladder != "SPECIAL" or threshold not in DECADE_THRESHOLDS:
                problems.append(
                    Problem("ERROR", "BAD_DECADE", where, f"연대 조건은 스페셜 {sorted(DECADE_THRESHOLDS)}에만 붙는다")
                )
            decade_tiers.add((ladder, threshold))
        key = (ladder, str(threshold), side, target, row["stat"], condition)
        if key in seen:
            problems.append(Problem("ERROR", "DUP_ROW", where, f"같은 규칙이 두 번: {key}"))
        seen.add(key)
    # 연대 티어 셋이 모두 살아 있어야 한다. 하나라도 빠지면 워크북 결함 판단이 뒤집힌 것이다.
    for threshold in sorted(DECADE_THRESHOLDS):
        if ("SPECIAL", threshold) not in decade_tiers:
            problems.append(
                Problem("ERROR", "MISSING_DECADE", path.name, f"스페셜 {threshold} 연대 규칙이 없다")
            )


def check_skill_scores(path: Path, resources: Path, problems: list[Problem]) -> None:
    if not path.exists():
        problems.append(Problem("ERROR", "MISSING_FILE", path.name, "파일이 없다"))
        return
    pools: dict[str, str] = {}
    with (resources / "score_skills.csv").open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            pools[row["skill_id"]] = row["card_type"]

    groups: dict[tuple[str, str], list[tuple[int, str]]] = {}
    for line, row in _rows(path):
        where = f"{path.name}:{line}"
        skill_id = row["skill_id"]
        if skill_id not in pools:
            problems.append(Problem("ERROR", "ORPHAN_SKILL", where, f"없는 스킬: {skill_id}"))
            continue
        try:
            level = int(row["level"])
        except ValueError:
            problems.append(Problem("ERROR", "BAD_LEVEL", where, row["level"]))
            continue
        limit = LADDERS[pools[skill_id]]
        if not 1 <= level <= limit:
            problems.append(Problem("ERROR", "BAD_LEVEL", where, f"{skill_id}의 레벨 {level} (최대 {limit})"))
        try:
            score = float(row["score"])
        except ValueError:
            problems.append(Problem("ERROR", "BAD_SCORE", where, row["score"]))
            continue
        if score < 0:
            problems.append(Problem("ERROR", "BAD_SCORE", where, f"점수가 음수다: {score}"))
        check_selector(row["selector"], where, problems)
        groups.setdefault((skill_id, row["level"]), []).append((line, row["selector"]))

    for (skill_id, level), entries in groups.items():
        always = [line for line, selector in entries if selector == "ALWAYS"]
        if len(always) > 1:
            problems.append(
                Problem("ERROR", "DUP_DEFAULT", path.name, f"{skill_id} L{level}에 기본값이 둘: {always}")
            )
        if always and always[0] != max(line for line, _ in entries):
            # 기본값이 먼저 나오면 뒤의 좁은 판정식이 영영 안 걸린다.
            problems.append(
                Problem("ERROR", "DEFAULT_NOT_LAST", path.name, f"{skill_id} L{level}의 기본값이 맨 뒤가 아니다")
            )
        if len(entries) > 1 and all(selector == "MANUAL" for _, selector in entries):
            problems.append(
                Problem("WARN", "ALL_MANUAL", path.name, f"{skill_id} L{level}은 전부 손으로 골라야 한다")
            )


def check_team_buffs(path: Path, resources: Path, problems: list[Problem]) -> None:
    if not path.exists():
        problems.append(Problem("ERROR", "MISSING_FILE", path.name, "파일이 없다"))
        return
    known = set()
    with (resources / "score_skills.csv").open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            known.add(row["skill_id"])
    seen: set[tuple[str, str]] = set()
    for line, row in _rows(path):
        where = f"{path.name}:{line}"
        if row["skill_id"] not in known:
            problems.append(Problem("ERROR", "ORPHAN_SKILL", where, f"없는 스킬: {row['skill_id']}"))
        if row["scope"] not in TEAM_BUFF_SCOPES:
            problems.append(Problem("ERROR", "BAD_SCOPE", where, f"모르는 대상: {row['scope']}"))
        if row["requires_slot"] and row["requires_slot"] not in REWARD_TARGET_SLOTS:
            problems.append(Problem("ERROR", "BAD_SLOT", where, f"모르는 자리: {row['requires_slot']}"))
        if row["stat"] not in GROWTH_STATS:
            problems.append(Problem("ERROR", "BAD_STAT", where, f"모르는 스탯: {row['stat']}"))
        values = _ladder(row["values"])
        if values is None:
            problems.append(Problem("ERROR", "BAD_VALUES", where, row["values"]))
        elif any(v < 0 for v in values):
            problems.append(Problem("ERROR", "BAD_VALUES", where, f"음수가 있다: {row['values']}"))
        key = (row["skill_id"], row["stat"])
        if key in seen:
            problems.append(Problem("ERROR", "DUP_ROW", where, f"{key}가 두 번"))
        seen.add(key)


def check(resources: Path = RESOURCES) -> list[Problem]:
    problems: list[Problem] = []
    check_growth(resources / "stat_growth_transcendence.csv", TRANSCENDENCE_MAX, problems)
    check_growth(resources / "stat_growth_enhancement.csv", ENHANCEMENT_MAX, problems)
    check_rewards(resources / "deck_score_rewards.csv", problems)
    check_skill_scores(resources / "excel_skill_scores.csv", resources, problems)
    check_team_buffs(resources / "team_buff_skills.csv", resources, problems)
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=RESOURCES)
    parser.add_argument("--warn", action="store_true", help="경고도 표시한다")
    args = parser.parse_args(argv)

    problems = check(args.resources)
    errors = [p for p in problems if p.level == "ERROR"]
    warnings = [p for p in problems if p.level == "WARN"]

    for problem in errors:
        print(problem.render())
    if args.warn:
        for problem in warnings:
            print(problem.render())

    print(f"\n오류 {len(errors)}건, 경고 {len(warnings)}건")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
