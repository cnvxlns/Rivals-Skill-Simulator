#!/usr/bin/env python3
"""스킬 CSV 3종의 불변식을 검사한다.

백엔드 로더(`ScoreDataLoader`)는 일부러 관대하다. 깨진 행을 만나면 경고만 남기고
넘어간다 — 데이터 한 줄 때문에 부팅이 죽는 것보다 낫다는 판단이고, 운영에서는
그게 맞다. 문제는 그래서 잘못된 데이터가 "조용히 틀린 점수"로만 드러난다는 것이다.

이 스크립트는 그 판정을 CI로 앞당긴다. 부팅은 그대로 관대하게 두고, 커밋 전에
여기서 막는다.

    python3 tools/validate_skill_csv.py          # 오류가 있으면 exit 1
    python3 tools/validate_skill_csv.py --warn   # 경고까지 표시

의존성 없이 stdlib만 쓴다. tools/ 아래 도구는 전부 같은 방침이다.
"""
from __future__ import annotations

import argparse
import csv
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "backend" / "src" / "main" / "resources"

# --- 어휘 -------------------------------------------------------------------
#
# 아래 목록은 "지금 데이터에 있는 것"이 아니라 "허용하기로 한 것"이다. 게임에
# 새 조건이 생기면 목록에 손으로 추가해야 통과하는데, 그 추가가 곧 검토 시점이다.
# 조건 토큰은 특히 중요하다. ScoreCalculator가 토큰마다 발생 확률을 갖고 있어서
# 모르는 토큰이 들어오면 점수가 틀리거나 요청 시점에 예외가 난다.

STATS = frozenset(
    ["파워", "정확", "선구", "인내", "주루", "수비", "구위", "변화", "제구", "구속", "지구력"]
)

CARD_TYPES = frozenset(["NORMAL", "MOMENT", "WBC", "BLACK", "HOF"])

# 포지션은 쉼표로 여러 개를 적을 수 있다("LF, CF, RF").
POSITIONS = frozenset(
    [
        "BATTER", "PITCHER",           # 역할 전체
        "SP", "RP", "CP",              # 투수 보직
        "C", "1B", "2B", "3B", "SS",   # 내야
        "LF", "CF", "RF",              # 외야
        "DH", "IF", "OF",              # 묶음
    ]
)

# 조건은 '+'로 결합될 수 있다("모드_랭킹대전+모드_라이브매치"). 아래는 그 원자들이다.
CONDITIONS = frozenset(
    [
        "ALWAYS",
        # 이닝
        "1_2회", "1_3회", "4_6회", "5회이후", "6회까지", "6회이후",
        "7_9회", "7회까지", "7회이후", "8회이후", "9회까지",
        # 카운트·타격
        "1스트라이크", "2스트라이크", "2아웃", "초구", "풀카운트",
        "높은공", "발사각조건", "스윗스팟", "스트라이크타격",
        "직구상대", "변화구상대", "속구선택", "변화구선택",
        # 주자·타순
        "주자없음", "주자있음", "주자2루이상",
        "타순1", "타순1_2", "타순2", "타순2_3", "타순3",
        "타순3_4_5", "타순4_5", "타순6_9", "타순8_9",
        # 타석
        "타석1", "타석2", "타석3", "타석4", "타석4_7", "타석5_7",
        "두번째타석까지", "대타첫타석", "교체후첫타자",
        # 투수 보직·지속
        "선발1", "선발1_2", "선발3_4", "선발3_4_5", "선발4_5", "중계3_4_5",
        "등판후3타자", "등판후4타자", "등판후9타자",
        "마에스트로누적", "이닝출루2인이상",
        # 좌우
        "좌타", "좌완", "스위치타", "좌타상대", "우타상대", "좌투상대", "우투상대",
        # 경기 상태
        "홈", "원정", "리드", "리드아님", "비김또는리드", "비김또는열세",
        "OVR열세", "OVR우세", "덱스코어열세", "상대등급우세", "상대팀홈런3",
        # 모드
        "모드_랭킹대전", "모드_랭킹토너먼트", "모드_라이브매치",
        "모드_리그", "모드_클럽", "모드_타점배틀", "모드_랭킹슬러거",
        # 스탯 비교
        "구위>파워", "선구>제구", "제구>선구", "인내<구속", "파워정확합>주루수비합",
        # 포지션 배치
        "포지션_C", "포지션_2B", "포지션_SS", "포지션_DH",
        "포지션_OF", "포지션_SP", "포지션_RP_CP",
    ]
)

# 비례 효과의 기준 스탯. 합 표기('주루+수비')와 특수값('스페셜덱')이 있다.
BASE_STATS = frozenset(
    ["구속", "변화", "정확", "지구력", "변화+제구", "정확+주루", "주루+수비", "스페셜덱"]
)

# 사다리 칸 수. 카드 등급마다 레벨 수가 달라 종류가 여럿이다.
# 1 = 레벨과 무관한 고정값.
LADDER_LENGTHS = frozenset([1, 3, 6, 7, 9])

# 값 범위. 실측(7,707개)은 전부 [-4, 17] 안에 있다. 넉넉히 잡아도
# 30을 넘는 능력치 증감은 데이터 오류로 보는 게 맞다 — 설명문의 조건 수치
# (예: "합이 155 이상인 경우")를 효과값으로 잘못 읽으면 여기에 걸린다.
VALUE_ERROR_ABS = 30.0
VALUE_WARN_ABS = 20.0


@dataclass(frozen=True)
class Problem:
    level: str  # "error" | "warn"
    code: str
    where: str
    message: str


def load(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as fp:
        return list(csv.DictReader(fp))


def check(skills: list[dict], effects: list[dict], weights: list[dict]) -> list[Problem]:
    problems: list[Problem] = []

    def err(code, where, msg):
        problems.append(Problem("error", code, where, msg))

    def warn(code, where, msg):
        problems.append(Problem("warn", code, where, msg))

    # --- score_skills.csv ---
    seen_ids: dict[str, int] = {}
    seen_names: dict[tuple[str, str, str], str] = {}
    for i, row in enumerate(skills, start=2):
        sid = (row.get("skill_id") or "").strip()
        at = f"score_skills.csv:{i}"
        if not sid:
            err("EMPTY_ID", at, "skill_id가 비어 있다")
            continue
        if sid in seen_ids:
            err("DUP_ID", at, f"skill_id '{sid}'가 {seen_ids[sid]}행과 중복")
        seen_ids[sid] = i

        card = (row.get("card_type") or "").strip().upper()
        if card not in CARD_TYPES:
            err("BAD_CARD_TYPE", at, f"{sid}: 모르는 card_type '{card}'")

        pos_raw = (row.get("position") or "").strip().upper()
        for atom in (p.strip() for p in pos_raw.split(",")):
            if atom and atom not in POSITIONS:
                err("BAD_POSITION", at, f"{sid}: 모르는 포지션 '{atom}' (position='{pos_raw}')")

        name = (row.get("name") or "").strip()
        if not name:
            err("EMPTY_NAME", at, f"{sid}: 이름이 비어 있다")
        else:
            # 같은 풀·포지션에 같은 이름이 둘 있으면 사용자가 둘 다 골라
            # 실제로는 한 번뿐인 효과를 이중으로 계산한다.
            key = (card, pos_raw, name)
            if key in seen_names:
                err("DUP_NAME", at, f"'{name}' ({card}/{pos_raw})가 {seen_names[key]}와 중복")
            seen_names[key] = sid

    # --- score_effects.csv ---
    with_effects: set[str] = set()
    for i, row in enumerate(effects, start=2):
        sid = (row.get("skill_id") or "").strip()
        at = f"score_effects.csv:{i}"
        if sid not in seen_ids:
            # 로더는 이걸 경고만 남기고 버린다 -> 효과가 조용히 사라진다.
            err("ORPHAN_EFFECT", at, f"모르는 skill_id '{sid}'")
            continue
        with_effects.add(sid)

        stat = (row.get("stat") or "").strip()
        if stat not in STATS:
            err("BAD_STAT", at, f"{sid}: 모르는 스탯 '{stat}'")

        cond = (row.get("condition") or "").strip() or "ALWAYS"
        for atom in (c.strip() for c in cond.split("+")):
            if atom and atom not in CONDITIONS:
                err("BAD_CONDITION", at, f"{sid}: 모르는 조건 '{atom}' (condition='{cond}')")

        base = (row.get("base_stat") or "").strip()
        if base and base not in BASE_STATS:
            err("BAD_BASE_STAT", at, f"{sid}: 모르는 base_stat '{base}'")

        problems.extend(_check_ladder(row.get("values") or "", sid, at))

    for sid, line in seen_ids.items():
        if sid not in with_effects:
            warn("NO_EFFECT", f"score_skills.csv:{line}", f"{sid}: 효과행이 하나도 없다")

    # --- stat_weights.csv ---
    weighted = set()
    for i, row in enumerate(weights, start=2):
        at = f"stat_weights.csv:{i}"
        stat = (row.get("stat") or "").strip()
        if stat not in STATS:
            err("BAD_STAT", at, f"모르는 스탯 '{stat}'")
            continue
        weighted.add(stat)
        try:
            float((row.get("weight") or "").strip())
        except ValueError:
            err("BAD_WEIGHT", at, f"{stat}: 가중치를 숫자로 못 읽는다 '{row.get('weight')}'")
    for stat in sorted(STATS - weighted):
        err("MISSING_WEIGHT", "stat_weights.csv", f"'{stat}'의 가중치가 없다")

    return problems


def _check_ladder(raw: str, sid: str, at: str) -> list[Problem]:
    """values 컬럼이 '4/5/6/7/8/9' 꼴인지, 값이 상식 범위인지 본다."""
    out: list[Problem] = []
    tokens = [t.strip() for t in raw.split("/")]
    if not raw.strip():
        return [Problem("error", "EMPTY_VALUES", at, f"{sid}: values가 비어 있다")]

    nums: list[float] = []
    for t in tokens:
        if t == "":
            out.append(Problem("error", "BAD_VALUES", at, f"{sid}: 빈 칸이 있다 '{raw}'"))
            return out
        try:
            nums.append(float(t))
        except ValueError:
            out.append(Problem("error", "BAD_VALUES", at, f"{sid}: 숫자가 아닌 값 '{t}' ('{raw}')"))
            return out

    if len(nums) not in LADDER_LENGTHS:
        # 칸 수가 어긋나면 ScoreCalculator가 coerceIn으로 잘라서 채점한다.
        # 예외가 아니라 "조금 낮은 점수"로 나오기 때문에 눈에 띄지 않는다.
        out.append(
            Problem("error", "BAD_LADDER_LEN", at,
                    f"{sid}: 사다리 칸 수 {len(nums)}는 허용({sorted(LADDER_LENGTHS)}) 밖 '{raw}'")
        )

    worst = max(nums, key=abs)
    if abs(worst) > VALUE_ERROR_ABS:
        out.append(
            Problem("error", "VALUE_OUT_OF_RANGE", at,
                    f"{sid}: 값 {worst:g}이 범위(±{VALUE_ERROR_ABS:g})를 벗어난다 '{raw}'")
        )
    elif abs(worst) > VALUE_WARN_ABS:
        out.append(
            Problem("warn", "VALUE_UNUSUAL", at,
                    f"{sid}: 값 {worst:g}이 이례적으로 크다 '{raw}'")
        )
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="스킬 CSV 불변식 검사")
    ap.add_argument("--warn", action="store_true", help="경고도 함께 출력한다")
    ap.add_argument("--resources", type=Path, default=RESOURCES, help="CSV 디렉터리")
    args = ap.parse_args()

    try:
        skills = load(args.resources / "score_skills.csv")
        effects = load(args.resources / "score_effects.csv")
        weights = load(args.resources / "stat_weights.csv")
    except FileNotFoundError as exc:
        print(f"CSV를 찾지 못했다: {exc}", file=sys.stderr)
        return 2

    problems = check(skills, effects, weights)
    errors = [p for p in problems if p.level == "error"]
    warns = [p for p in problems if p.level == "warn"]

    for p in errors:
        print(f"ERROR [{p.code}] {p.where}  {p.message}")
    if args.warn:
        for p in warns:
            print(f"warn  [{p.code}] {p.where}  {p.message}")

    print(
        f"\n스킬 {len(skills)}행 / 효과 {len(effects)}행 / 가중치 {len(weights)}행 검사: "
        f"오류 {len(errors)}건, 경고 {len(warns)}건"
        + ("" if args.warn or not warns else " (--warn으로 경고 확인)")
    )
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
