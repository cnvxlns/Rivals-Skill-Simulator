#!/usr/bin/env python3
"""덱 관리 워크북에서 계산 데이터를 뽑아 CSV로 옮긴다.

`docs/MLB라이벌_덱관리프로그램_260910.xlsx`는 커뮤니티에서 만든 덱 계산기다. 그 안에는
우리가 갖고 있지 않던 표가 넷 들어 있다 — 카드별 초월·강화 능력치, 덱 스코어 보상,
스킬 점수표, 그리고 팀 버프 수치다.

워크북 자체는 추적하지 않는다(`.gitignore`의 `docs/*`). 그래서 **커밋된 CSV가 원천이고**
이 도구는 재현용 보조다. 워크북을 다시 받았을 때 `--check`로 대조해 보거나, 개정판이
나왔을 때 새로 뽑는 용도다.

    python3 tools/deck_workbook.py --workbook <경로> --out backend/src/main/resources
    python3 tools/deck_workbook.py --workbook <경로> --check   # 커밋된 것과 다르면 exit 1

읽어 내기 까다로운 지점이 셋 있다. 셋 다 실제로 한 번씩 틀렸던 것들이다.

1. **공유 수식**(`<f t="shared">`). 마스터 셀에만 수식 문자열이 있고 나머지는 `si`로
   가리키기만 한다. 펼치지 않으면 1496개 규칙 중 102개만 보인다.
2. **행마다 다른 수식**. 같은 열이라도 타자 9행·투수 9행의 수식이 다를 수 있다.
   팀 435 티어는 좌가 내야, 우가 외야다. 열 하나를 대표로 읽으면 절반을 잃는다.
3. **정규식으로는 안 된다**. 중첩 `IF(AND(...),n,IF(AND(...),m,0))`의 좌/우 분기를
   정규식으로 집으려다 실패했다. 작은 재귀 하강 파서를 쓴다.

의존성 없이 stdlib만 쓴다. tools/ 아래 도구는 전부 같은 방침이다.
"""
from __future__ import annotations

import argparse
import csv
import io
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "backend" / "src" / "main" / "resources"

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
REL_NS = "{http://schemas.openxmlformats.org/package/2006/relationships}"
DOC_REL_NS = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"


class WorkbookError(Exception):
    """워크북이 우리가 아는 모양이 아니다. 조용히 넘기지 않고 멈춘다."""


# --- 1. OOXML 읽기 ----------------------------------------------------------


def col_index(col: str) -> int:
    """열 이름을 1부터 세는 번호로. `A`=1, `Z`=26, `AA`=27."""
    n = 0
    for ch in col:
        n = n * 26 + ord(ch) - 64
    return n


def col_name(index: int) -> str:
    """[col_index]의 역."""
    out = ""
    while index:
        index, rem = divmod(index - 1, 26)
        out = chr(65 + rem) + out
    return out


CELL_REF = re.compile(r"([A-Z]{1,3})(\d+)")


def split_ref(ref: str) -> tuple[str, int]:
    m = CELL_REF.fullmatch(ref)
    if not m:
        raise WorkbookError(f"셀 주소를 읽을 수 없다: {ref}")
    return m.group(1), int(m.group(2))


# 상대참조를 옮길 때 쓴다. `$`가 붙은 쪽은 고정이다.
REF_PARTS = re.compile(r"(\$?)([A-Z]{1,3})(\$?)(\d+)")


def shift_formula(text: str, dcol: int, drow: int) -> str:
    """공유 수식 마스터를 다른 칸으로 옮긴다."""

    def move(m: re.Match[str]) -> str:
        col_fixed, col, row_fixed, row = m.groups()
        new_col = col if col_fixed else col_name(col_index(col) + dcol)
        new_row = row if row_fixed else str(int(row) + drow)
        return f"{col_fixed}{new_col}{row_fixed}{new_row}"

    return REF_PARTS.sub(move, text)


@dataclass
class Sheet:
    """시트 한 장. 값과 수식을 셀 주소로 찾는다."""

    name: str
    values: dict[tuple[str, int], str] = field(default_factory=dict)
    formulas: dict[tuple[str, int], str] = field(default_factory=dict)
    merges: list[str] = field(default_factory=list)

    def value(self, col: str, row: int) -> str | None:
        return self.values.get((col, row))

    def text(self, col: str, row: int) -> str:
        return (self.value(col, row) or "").strip()

    def number(self, col: str, row: int) -> float | None:
        raw = self.value(col, row)
        if raw is None:
            return None
        try:
            return float(raw)
        except ValueError:
            return None

    def max_row(self) -> int:
        return max((row for _, row in self.values), default=0)


class Workbook:
    """워크북 한 권. 시트를 이름으로 찾는다."""

    def __init__(self, path: Path) -> None:
        self.path = path
        with zipfile.ZipFile(path) as zf:
            self._shared = self._read_shared_strings(zf)
            self._sheets = {
                name: self._read_sheet(zf, name, part)
                for name, part in self._sheet_parts(zf).items()
            }

    def sheet(self, name: str) -> Sheet:
        try:
            return self._sheets[name]
        except KeyError:
            raise WorkbookError(
                f"시트 '{name}'이 없다. 있는 시트: {', '.join(self._sheets)}"
            ) from None

    def sheet_names(self) -> list[str]:
        return list(self._sheets)

    # -- 내부 --

    @staticmethod
    def _read_shared_strings(zf: zipfile.ZipFile) -> list[str]:
        try:
            raw = zf.read("xl/sharedStrings.xml")
        except KeyError:
            return []
        root = ET.fromstring(raw)
        # <si>는 서식이 바뀌는 지점마다 <r><t>로 쪼개진다. 전부 이어 붙여야 원문이 된다.
        return ["".join(t.text or "" for t in si.iter(NS + "t")) for si in root.findall(NS + "si")]

    @staticmethod
    def _sheet_parts(zf: zipfile.ZipFile) -> dict[str, str]:
        """시트 이름 -> 파트 경로. `workbook.xml`의 r:id를 rels로 푼다."""
        book = ET.fromstring(zf.read("xl/workbook.xml"))
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        target_by_id = {
            rel.get("Id"): rel.get("Target", "") for rel in rels.findall(REL_NS + "Relationship")
        }
        parts: dict[str, str] = {}
        for sheet in book.iter(NS + "sheet"):
            name = sheet.get("name") or ""
            target = target_by_id.get(sheet.get(DOC_REL_NS + "id", ""), "")
            if not target:
                continue
            parts[name] = target if target.startswith("xl/") else "xl/" + target.lstrip("/")
        return parts

    def _read_sheet(self, zf: zipfile.ZipFile, name: str, part: str) -> Sheet:
        root = ET.fromstring(zf.read(part))
        sheet = Sheet(name=name)
        masters: dict[str, tuple[str, str]] = {}
        pending: list[tuple[str, int, str]] = []

        for cell in root.iter(NS + "c"):
            ref = cell.get("r")
            if not ref:
                continue
            col, row = split_ref(ref)
            kind = cell.get("t")
            node = cell.find(NS + "v")
            inline = cell.find(NS + "is")
            if kind == "s" and node is not None:
                sheet.values[(col, row)] = self._shared[int(node.text or "0")]
            elif inline is not None:
                sheet.values[(col, row)] = "".join(t.text or "" for t in inline.iter(NS + "t"))
            elif kind == "e":
                # #N/A 같은 오류 셀. 값이 없는 것으로 본다.
                pass
            elif node is not None:
                sheet.values[(col, row)] = node.text or ""

            formula = cell.find(NS + "f")
            if formula is None:
                continue
            shared_id = formula.get("si")
            if formula.get("t") == "shared" and shared_id is not None:
                if formula.text:
                    masters[shared_id] = (formula.text, formula.get("ref") or ref)
                else:
                    pending.append((col, row, shared_id))
                    continue
            if formula.text:
                sheet.formulas[(col, row)] = formula.text

        for col, row, shared_id in pending:
            master = masters.get(shared_id)
            if master is None:
                continue
            text, anchor = master
            base_col, base_row = split_ref(anchor.split(":")[0])
            sheet.formulas[(col, row)] = shift_formula(
                text, col_index(col) - col_index(base_col), row - base_row
            )

        sheet.merges = [m.get("ref", "") for m in root.iter(NS + "mergeCell")]
        return sheet


# --- 2. 수식 파서 -----------------------------------------------------------
#
# 워크북이 쓰는 문법은 좁다. IF / AND / OR / 비교 / 뺄셈 / 셀 참조 / 숫자 / 문자열이 전부다.
# 그래도 파서를 두는 이유는 중첩 IF의 else 가지를 정규식으로는 못 집기 때문이다.

TOKEN = re.compile(
    r"""\s*(?:
        (<>|<=|>=|[(),=<>\-])        # 연산자
      | "((?:[^"]*))"                 # 문자열
      | (\$?[A-Z]{1,3}\$?\d+)         # 셀 참조
      | (\d+(?:\.\d+)?)               # 숫자
      | ([A-Za-z_.]+)                 # 함수 이름
    )""",
    re.X,
)


def tokenize(text: str) -> list[tuple[str, object]]:
    out: list[tuple[str, object]] = []
    pos = 0
    while pos < len(text):
        m = TOKEN.match(text, pos)
        if not m:
            raise WorkbookError(f"수식을 읽을 수 없다: {text!r} (…{text[pos:pos + 20]!r})")
        pos = m.end()
        op, string, ref, number, name = m.groups()
        if op:
            out.append(("op", op))
        elif string is not None:
            out.append(("str", string))
        elif ref:
            out.append(("ref", ref.replace("$", "")))
        elif number:
            out.append(("num", float(number)))
        else:
            out.append(("fn", name.upper()))
    return out


# 파스 트리는 튜플이다. ('num', v) ('str', s) ('ref', 'AU10')
# ('sub', a, b) ('cmp', op, a, b) ('fn', 'IF', [args])
Node = tuple


class Parser:
    def __init__(self, tokens: list[tuple[str, object]]) -> None:
        self.tokens = tokens
        self.pos = 0

    def peek(self) -> tuple[str, object]:
        return self.tokens[self.pos] if self.pos < len(self.tokens) else ("", "")

    def take(self) -> tuple[str, object]:
        token = self.tokens[self.pos]
        self.pos += 1
        return token

    def parse(self) -> Node:
        node = self.expr()
        if self.pos != len(self.tokens):
            raise WorkbookError(f"수식 끝에 남는 토큰이 있다: {self.tokens[self.pos:]}")
        return node

    def expr(self) -> Node:
        left = self.arith()
        kind, value = self.peek()
        if kind == "op" and value in ("=", "<>", "<", ">", "<=", ">="):
            self.take()
            return ("cmp", value, left, self.arith())
        return left

    def arith(self) -> Node:
        left = self.atom()
        while self.peek() == ("op", "-"):
            self.take()
            left = ("sub", left, self.atom())
        return left

    def atom(self) -> Node:
        kind, value = self.take()
        if kind == "op" and value == "-":
            return ("sub", ("num", 0.0), self.atom())
        if kind in ("num", "str", "ref"):
            return (kind, value)
        if kind == "fn":
            if self.take() != ("op", "("):
                raise WorkbookError(f"함수 {value} 뒤에 괄호가 없다")
            args = [self.expr()]
            while self.peek() == ("op", ","):
                self.take()
                args.append(self.expr())
            if self.take() != ("op", ")"):
                raise WorkbookError(f"함수 {value}의 괄호가 닫히지 않았다")
            return ("fn", value, args)
        raise WorkbookError(f"뜻밖의 토큰: {(kind, value)}")


def parse_formula(text: str) -> Node:
    return Parser(tokenize(text)).parse()


def evaluate(node: Node, sheet: Sheet) -> object:
    """수식을 실제 값으로 계산한다. 추출 결과를 캐시값과 대조할 때 쓴다."""
    kind = node[0]
    if kind in ("num", "str"):
        return node[1]
    if kind == "ref":
        col, row = split_ref(str(node[1]))
        raw = sheet.value(col, row)
        if raw is None:
            return None
        try:
            return float(raw)
        except ValueError:
            return raw
    if kind == "sub":
        left = evaluate(node[1], sheet) or 0.0
        right = evaluate(node[2], sheet) or 0.0
        return float(left) - float(right)
    if kind == "cmp":
        return _compare(str(node[1]), evaluate(node[2], sheet), evaluate(node[3], sheet))
    if kind == "fn":
        name, args = node[1], node[2]
        if name == "IF":
            if evaluate(args[0], sheet):
                return evaluate(args[1], sheet)
            return evaluate(args[2], sheet) if len(args) > 2 else 0.0
        if name == "AND":
            return all(evaluate(a, sheet) for a in args)
        if name == "OR":
            return any(evaluate(a, sheet) for a in args)
        raise WorkbookError(f"모르는 함수: {name}")
    raise WorkbookError(f"모르는 노드: {node}")


def _compare(op: str, left: object, right: object) -> bool:
    if isinstance(left, str) or isinstance(right, str):
        if op in ("=", "<>"):
            # 엑셀의 문자열 비교는 대소문자를 가리지 않는다. 빈 셀은 ""로 본다.
            l = left.lower() if isinstance(left, str) else ""
            r = right.lower() if isinstance(right, str) else ""
            return (l == r) if op == "=" else (l != r)
        # 엑셀의 정렬 규칙에서 숫자는 어떤 문자열보다 작다.
        if isinstance(left, str) and not isinstance(right, str):
            return op in (">", ">=")
        if isinstance(right, str) and not isinstance(left, str):
            return op in ("<", "<=")
        return {"<": left < right, ">": left > right, "<=": left <= right, ">=": left >= right}[op]
    a = 0.0 if left is None else float(left)
    b = 0.0 if right is None else float(right)
    return {
        "=": a == b,
        "<>": a != b,
        "<": a < b,
        ">": a > b,
        "<=": a <= b,
        ">=": a >= b,
    }[op]


# --- 3. 카드 이름 ------------------------------------------------------------
#
# 한글 이름은 여기서 한 번만 우리 enum으로 옮긴다. 백엔드가 워크북 어휘를 들고 다니지
# 않게 하려는 것이다.
#
# 워크북 자체에 띄어쓰기 결함이 있다. 드롭다운은 `FA 시그니처`인데 표 키는 `FA시그니처`라
# 엑셀 안에서는 VLOOKUP이 #N/A로 떨어진다. 공백을 지우고 맞추면 둘 다 잡힌다.

CARD_NAMES: dict[str, tuple[str, str]] = {
    "명예의전당": ("HOF", "NONE"),
    "시그니처블랙": ("SIGNATURE_BLACK", "NONE"),
    "WBC시그니처블랙": ("SIGNATURE_BLACK", "WBC"),
    "FA시그니처블랙": ("SIGNATURE_BLACK", "FA"),
    "시그니처": ("SIGNATURE", "NONE"),
    "WBC시그니처": ("SIGNATURE", "WBC"),
    "FA시그니처": ("SIGNATURE", "FA"),
    "슈프림모먼트": ("SUPREME_MOMENT", "NONE"),
    "모먼트": ("MOMENT", "NONE"),
    "프라임": ("PRIME", "NONE"),
    "WBC프라임": ("PRIME", "WBC"),
    "FA프라임": ("PRIME", "FA"),
    # 덱 스코어 조건이 참조하지만 드롭다운에는 없는 값. 수식 문자 그대로 살려 둔다.
    "라이브/시즌": ("LIVE", "NONE"),
}


def card_key(name: str) -> tuple[str, str]:
    key = re.sub(r"\s+", "", name)
    try:
        return CARD_NAMES[key]
    except KeyError:
        raise WorkbookError(f"모르는 카드 이름: {name!r}") from None


STAT_NAMES = ("파워", "정확", "선구", "변화", "구위")


# --- 4. 초월·강화 표 ---------------------------------------------------------


@dataclass
class GrowthRow:
    card_grade: str
    card_variant: str
    stat: str
    values: list[str]

    def as_csv(self) -> list[str]:
        return [self.card_grade, self.card_variant, self.stat, "/".join(self.values)]


def read_growth(sheet: Sheet, first_row: int, first_col: str, last_col: str) -> list[GrowthRow]:
    """카드별 누적 능력치 표 한 장. 레벨 칸이 비면 거기서 사다리가 끝난다.

    사다리 길이가 카드마다 다르다. 강화는 블랙 계열만 10에서 멈추고, 초월은 시그니처·
    프라임 계열이 9에서 멈춘다. 실제 게임 상한이라 억지로 채우지 않는다.
    """
    rows: list[GrowthRow] = []
    lo, hi = col_index(first_col), col_index(last_col)
    for row in range(first_row, sheet.max_row() + 1):
        name = sheet.text("A", row)
        stat = sheet.text("B", row)
        if not name or stat not in STAT_NAMES:
            continue
        grade, variant = card_key(name)
        values: list[str] = []
        for index in range(lo, hi + 1):
            raw = sheet.text(col_name(index), row)
            if raw == "":
                break
            values.append(_trim_number(raw))
        if not values:
            continue
        rows.append(GrowthRow(grade, variant, stat, values))
    rows.sort(key=lambda r: (r.card_grade, r.card_variant, STAT_NAMES.index(r.stat)))
    return rows


def _trim_number(raw: str) -> str:
    """`3.0` 같은 표기를 `3`으로. CSV가 사람 눈에 읽히게 한다."""
    try:
        value = float(raw)
    except ValueError:
        return raw
    return str(int(value)) if value.is_integer() else repr(value)


# --- 5. 덱 스코어 보상 -------------------------------------------------------
#
# 워크북의 `팀덱코`(BF~EB)·`스덱코`(ED~HL) 블록이다. 임계값마다 좌·우 중 하나를 고르고,
# 고른 쪽이 어떤 선수에게 얼마를 주는지가 이 1496개 셀에 적혀 있다.

BATTER_ROWS = range(11, 20)
PITCHER_ROWS = range(22, 31)
REWARD_FIRST_COL = "BF"
REWARD_LAST_COL = "HL"
SPECIAL_FIRST_COL = "ED"

# 좌/우 토글 칸이 있는 열. 팀은 AU/AV, 스페셜은 AY/AZ다.
SIDE_BY_COLUMN = {"AU": "LEFT", "AV": "RIGHT", "AY": "LEFT", "AZ": "RIGHT"}

BATTER_SLOTS = ("C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH")
PITCHER_SLOTS = ("SP1", "SP2", "SP3", "SP4", "SP5", "RP1", "RP2", "RP3", "CP1")
SLOT_ORDER = BATTER_SLOTS + PITCHER_SLOTS

# 자리 집합에 이름을 준다. 워크북은 SP1~5·RP1~3·CP1까지만 알지만 우리 덱은 선발 6·중계 7·
# 마무리 2까지 갈 수 있다. 역할 이름으로 옮겨 두면 그 자리들도 같은 보상을 받는다.
SLOT_GROUPS: list[tuple[str, tuple[str, ...]]] = [
    ("BATTER_ALL", BATTER_SLOTS),
    ("PITCHER_ALL", PITCHER_SLOTS),
    ("SP", ("SP1", "SP2", "SP3", "SP4", "SP5")),
    ("RP_CP", ("RP1", "RP2", "RP3", "CP1")),
    ("RP", ("RP1", "RP2", "RP3")),
    ("CP", ("CP1",)),
]


@dataclass
class RewardRow:
    ladder: str
    threshold: int
    side: str
    target: str
    stat: str
    amount: int
    condition: str

    def as_csv(self) -> list[str]:
        return [
            self.ladder,
            str(self.threshold),
            self.side,
            self.target,
            self.stat,
            str(self.amount),
            self.condition,
        ]


def _tier_columns(sheet: Sheet) -> list[tuple[str, str, int]]:
    """(시작 열, 사다리, 임계값). 9행 헤더가 티어의 시작을 알린다.

    티어마다 열 폭이 같다고 **가정하면 안 된다.** 팀 600 묶음은 네 칸이라 그 뒤가 한 칸씩
    밀린다. 산술로 나누면 열 대응이 조용히 어긋난다.
    """
    lo, hi = col_index(REWARD_FIRST_COL), col_index(REWARD_LAST_COL)
    special = col_index(SPECIAL_FIRST_COL)
    tiers: list[tuple[str, str, int]] = []
    for index in range(lo, hi + 1):
        col = col_name(index)
        raw = sheet.text(col, 9)
        if not raw:
            continue
        tiers.append((col, "TEAM" if index < special else "SPECIAL", int(float(raw))))
    return tiers


def _tier_of(tiers: list[tuple[str, str, int]], col: str) -> tuple[str, int]:
    found = None
    for start, ladder, threshold in tiers:
        if col_index(start) <= col_index(col):
            found = (ladder, threshold)
    if found is None:
        raise WorkbookError(f"{col}열이 어느 티어에도 속하지 않는다")
    return found


def _condition_of(node: Node, sheet: Sheet) -> tuple[list[str], str]:
    """조건식에서 (토글 셀들, 조건 이름)을 뽑는다.

    조건은 여덟 가지뿐이고 전부 워크북에서 실제로 확인한 모양이다. 모르는 모양이 나오면
    예외를 던진다. 워크북이 개정돼도 규칙이 조용히 빠지지 않게 하려는 것이다.
    """
    toggles: list[str] = []
    facts: list[tuple[str, str, float]] = []
    decade = False

    def walk(n: Node) -> None:
        nonlocal decade
        if n[0] == "fn" and n[1] in ("AND", "OR"):
            for arg in n[2]:
                walk(arg)
            return
        if n[0] != "cmp":
            raise WorkbookError(f"조건에 뜻밖의 노드: {n}")
        op, left, right = str(n[1]), n[2], n[3]
        if left[0] == "sub" or right[0] == "sub":
            decade = True  # 연도 - 기준연도 < 10 꼴
            return
        ref = left if left[0] == "ref" else right if right[0] == "ref" else None
        if ref is None:
            raise WorkbookError(f"조건에 셀 참조가 없다: {n}")
        other = right if ref is left else left
        col, _row = split_ref(str(ref[1]))
        if col in SIDE_BY_COLUMN:
            toggles.append(str(ref[1]))
            if other[0] == "num" and float(other[1]) == 1880:
                decade = True  # 토글 칸에 연도를 적었는지 보는 분기
            return
        if ref is not left:  # `2<$C11` 처럼 뒤집혀 있으면 부등호를 돌린다
            op = {"<": ">", ">": "<", "<=": ">=", ">=": "<="}.get(op, op)
        facts.append((col, op, float(other[1]) if other[0] == "num" else 0.0))
        if other[0] == "str":
            facts[-1] = (col, op, 0.0) if other[1] != "라이브/시즌" else ("CARD", op, 0.0)

    walk(node)

    if decade:
        return toggles, "DECADE"
    if not facts:
        return toggles, "ALWAYS"
    if any(col == "CARD" for col, _, _ in facts):
        op = next(o for c, o, _ in facts if c == "CARD")
        return toggles, "CARD_LIVE_SEASON" if op == "=" else "CARD_NOT_LIVE_SEASON"
    if any(col == "AF" for col, _, _ in facts):
        return toggles, "ENHANCE_GTE_10"
    orders = sorted((op, value) for col, op, value in facts if col == "C")
    if orders == [("<", 3.0)]:
        return toggles, "ORDER_1_2"
    if orders == [(">", 5.0)]:
        return toggles, "ORDER_6_9"
    if orders == [("<", 6.0), (">", 2.0)]:
        return toggles, "ORDER_3_5"
    raise WorkbookError(f"모르는 조건 모양: {facts}")


def _branches(node: Node, sheet: Sheet) -> list[tuple[list[str], str, int]]:
    """중첩 IF를 (토글들, 조건, 값) 목록으로 편다."""
    out: list[tuple[list[str], str, int]] = []

    def walk(n: Node) -> None:
        if n[0] != "fn" or n[1] != "IF":
            return
        test, then = n[2][0], n[2][1]
        otherwise = n[2][2] if len(n[2]) > 2 else ("num", 0.0)
        toggles, condition = _condition_of(test, sheet)
        if then[0] == "fn":
            # IF(토글>1880, IF(연도범위, n, 0), 0) — 안쪽에서 값을 꺼낸다
            inner_toggles, inner_condition = _condition_of(then[2][0], sheet)
            amount = then[2][1]
            out.append((toggles + inner_toggles, inner_condition if inner_condition != "ALWAYS" else condition, int(float(amount[1]))))
        else:
            out.append((toggles, condition, int(float(then[1]))))
        if otherwise[0] == "fn":
            walk(otherwise)

    walk(node)
    return out


def read_rewards(sheet: Sheet) -> list[RewardRow]:
    tiers = _tier_columns(sheet)
    if not tiers:
        raise WorkbookError("9행에서 덱 스코어 티어 머리글을 찾지 못했다")

    # (사다리, 임계값, 좌우, 스탯, 값, 조건) -> 그 규칙이 걸리는 자리들
    collected: dict[tuple[str, int, str, str, int, str], set[str]] = {}
    lo, hi = col_index(REWARD_FIRST_COL), col_index(REWARD_LAST_COL)

    for rows, header_row, slots in (
        (BATTER_ROWS, 10, BATTER_SLOTS),
        (PITCHER_ROWS, 21, PITCHER_SLOTS),
    ):
        for row in rows:
            slot = sheet.text("B", row)
            if slot not in slots:
                raise WorkbookError(f"{row}행의 자리 이름이 뜻밖이다: {slot!r}")
            for index in range(lo, hi + 1):
                col = col_name(index)
                formula = sheet.formulas.get((col, row))
                if not formula:
                    continue
                stat = sheet.text(col, header_row)
                if stat not in STAT_NAMES:
                    raise WorkbookError(f"{col}{header_row}의 스탯 이름이 뜻밖이다: {stat!r}")
                ladder, threshold = _tier_of(tiers, col)
                for toggles, condition, amount in _branches(parse_formula(formula), sheet):
                    if amount == 0:
                        continue
                    for side in sorted({SIDE_BY_COLUMN[split_ref(t)[0]] for t in toggles}):
                        key = (ladder, threshold, side, stat, amount, condition)
                        collected.setdefault(key, set()).add(slot)

    out: list[RewardRow] = []
    for (ladder, threshold, side, stat, amount, condition), slots in collected.items():
        out.append(
            RewardRow(ladder, threshold, side, _target_name(slots), stat, amount, condition)
        )
    out.sort(
        key=lambda r: (
            r.ladder != "TEAM",
            r.threshold,
            r.side,
            r.target,
            STAT_NAMES.index(r.stat),
            r.condition,
        )
    )
    return out


def _target_name(slots: set[str]) -> str:
    for name, members in SLOT_GROUPS:
        if slots == set(members):
            return name
    return "|".join(s for s in SLOT_ORDER if s in slots)


def verify_rewards(sheet: Sheet) -> tuple[int, list[str]]:
    """수식을 그대로 계산해 워크북이 캐시해 둔 값과 맞는지 본다.

    파서가 수식을 잘못 읽으면 여기서 어긋난다. 추출 결과를 믿을 근거가 이것 하나다.
    """
    lo, hi = col_index(REWARD_FIRST_COL), col_index(REWARD_LAST_COL)
    checked = 0
    problems: list[str] = []
    for row in list(BATTER_ROWS) + list(PITCHER_ROWS):
        for index in range(lo, hi + 1):
            col = col_name(index)
            formula = sheet.formulas.get((col, row))
            if not formula:
                continue
            cached = sheet.number(col, row)
            if cached is None:
                continue
            got = float(evaluate(parse_formula(formula), sheet) or 0.0)
            checked += 1
            if abs(got - cached) > 1e-9:
                problems.append(f"{col}{row}: 계산 {got} != 캐시 {cached} ({formula})")
    return checked, problems


# --- 6. 스킬 점수표 ----------------------------------------------------------
#
# 워크북의 `스킬점수` 시트는 "[S1] 스피드&컨택 (O, 정 200-233)" 같은 문자열 하나에 점수
# 하나를 붙여 둔 표다. 등급·조건 구간까지 이름에 박혀 있어서 같은 스킬·레벨이라도 옵션이
# 다르면 다른 행이 된다.
#
# 우리 CSV는 (스탯 × 조건 × 레벨사다리)로 정규화돼 있으므로, 여기서 할 일은 문자열을
# `skill_id` + 레벨 + **판정식**으로 옮기는 것이다. 판정식이 있으면 앱이 선수 상황을 보고
# 자동으로 고르고, 없으면(`MANUAL`) 사용자가 고른다.

SKILL_SCORE_SHEET = "스킬점수"
SKILL_SCORE_COLUMNS = (("A", "B", "BATTER"), ("D", "E", "PITCHER"))

PITCHER_POSITIONS = frozenset(["PITCHER", "SP", "RP", "CP", "SP, RP", "RP, CP"])

# 스킬 풀별 레벨 사다리. 백엔드 `SkillRules.gradeLadder`와 같아야 한다.
LADDERS = {
    "NORMAL": ["D", "C", "B", "A", "S", "S1", "S2", "S3", "S4"],
    "HOF": ["D", "C", "B", "A", "S", "S1"],
    "BLACK": ["D", "C", "B", "A", "S", "S1", "S2"],
    "WBC": ["S", "S1", "S2"],
    "MOMENT": ["S"],
}

# 워크북이 쓰는 이름 중 우리 CSV와 어긋나는 것. 공백·약칭 정규화로도 안 붙는 하나뿐이다.
#
# `파워 히터`는 여기 넣지 않는다. b15a559의 커밋 메시지를 보고 `슬러거`의 옛 이름인 줄
# 알았는데, 워크북에 **둘 다** 있고 점수가 다르다(슬러거 12.66 / 파워 히터 17.7).
# 슬러거 쪽이 우리 M_003과 수치까지 맞으므로(5×2.4 + 2×1.1×0.3 = 12.66) 둘은 다른 스킬이다.
# 우리 CSV에 없는 스킬이라 메모로 남기고 건너뛴다.
SKILL_ALIASES = {
    "리그주도자": "리그의주도자",
}


def normalize_skill_name(name: str) -> str:
    """워크북 표기와 우리 CSV 표기를 같은 자리에 놓는다.

    차이는 셋뿐이다 — 공백(`배팅 머신` / `배팅머신`), WBC 약칭, `피쳐`/`피처`.
    """
    folded = name.replace("WORLD BASEBALL CLASSIC", "WBC").replace("피쳐", "피처")
    folded = re.sub(r"\s+", "", folded).upper()
    return SKILL_ALIASES.get(folded, folded)


SKILL_LABEL = re.compile(r"^\[(S\d?)\]\s*(.*)$")


def split_skill_label(text: str) -> tuple[str, str, str | None]:
    """`[S1] 이름 (옵션)` -> (레벨 라벨, 이름, 옵션).

    문법이 하나가 아니다. 괄호가 없는 것(`[S2] WBC 에이스`), 괄호 안에 괄호가 있는 것
    (`[S0] 라이징 스타 (선발 (O))`), 괄호 뒤에 깃발이 붙은 것(`파워 피처(구속>인내) O`),
    이름과 괄호가 붙어 있는 것(`[S0]5툴 유격수`)까지 있다.
    """
    m = SKILL_LABEL.match(text)
    if not m:
        raise WorkbookError(f"스킬 문자열을 읽을 수 없다: {text!r}")
    label, rest = m.group(1), m.group(2).strip()

    flag = None
    tail = re.match(r"^(.*\))\s+([OX])$", rest)
    if tail:
        rest, flag = tail.group(1), tail.group(2)

    option = None
    if rest.endswith(")"):
        depth = 0
        for i in range(len(rest) - 1, -1, -1):
            if rest[i] == ")":
                depth += 1
            elif rest[i] == "(":
                depth -= 1
                if depth == 0:
                    option, rest = rest[i + 1 : -1].strip(), rest[:i].strip()
                    break
    if flag:
        option = f"{option} {flag}" if option else flag
    return ("S" if label == "S0" else label), rest, option


# 옵션에 붙는 스탯 약칭. 긴 것부터 본다(`변제`가 `변`보다 먼저).
OPTION_STAT_PREFIXES = [
    ("변제", "변화+제구"),
    ("정주", "정확+주루"),
    ("주수", "주루+수비"),
    ("주+수", "주루+수비"),
    ("스덱코", "스페셜덱"),
    ("구속", "구속"),
    ("변", "변화"),
    ("정", "정확"),
    ("지", "지구력"),
]

# 우리 조건 토큰 중 덱 정보만으로 참·거짓이 갈리는 것. 확률형(`주자있음`·`홈`·`OVR열세`)은
# 선수 상황으로 판정할 수 없어 일부러 뺐다.
CONDITION_SELECTORS = {
    "타순1": "ORDER=1",
    "타순1_2": "ORDER=1..2",
    "타순2": "ORDER=2",
    "타순2_3": "ORDER=2..3",
    "타순3": "ORDER=3",
    "타순3_4_5": "ORDER=3..5",
    "타순4_5": "ORDER=4..5",
    "타순6_9": "ORDER=6..9",
    "타순8_9": "ORDER=8..9",
    "포지션_C": "POS=C",
    "포지션_DH": "POS=DH",
    "포지션_OF": "POS=OF",
    "포지션_SS": "POS=SS",
    "포지션_2B": "POS=2B",
    "포지션_SP": "ROLE=SP",
    "포지션_RP_CP": "ROLE=RP|CP",
    "선발1": "PSLOT=1",
    "선발1_2": "PSLOT=1..2",
    "선발3_4": "PSLOT=3..4",
    "선발4_5": "PSLOT=4..5",
    "좌완": "THROW=LEFT",
    "우완": "THROW=RIGHT",
    "좌타": "BAT=LEFT",
    "우타": "BAT=RIGHT",
    "스위치타": "BAT=SWITCH",
}

ROLE_WORDS = {"선발": "ROLE=SP", "중계": "ROLE=RP", "마무리": "ROLE=CP"}
RELIEVER_WORDS = {"승리조": "RELIEVER=WIN", "추격조": "RELIEVER=CHASE", "롱 릴리프": "RELIEVER=LONG"}
HAND_WORDS = {
    "좌완": "THROW=LEFT",
    "우완": "THROW=RIGHT",
    "좌타": "BAT=LEFT",
    "우타": "BAT=RIGHT",
    "양타": "BAT=SWITCH",
    "스위치": "BAT=SWITCH",
}
GRADE_WORDS = {"명전": "GRADE=HOF", "명예의 전당": "GRADE=HOF"}


def _negate(selector: str) -> str:
    return "&".join(
        part[1:] if part.startswith("!") else f"!{part}" for part in selector.split("&")
    )


def _range_selector(stat: str, text: str, base: bool = False) -> str | None:
    """`100-149` / `300미만` / `165 이상` / `155~164` 를 판정식으로."""
    kind = "BASESTAT" if base else "STAT"
    text = text.strip()
    m = re.fullmatch(r"(\d+)\s*[-~]\s*(\d+)", text)
    if m:
        lo, hi = int(m.group(1)), int(m.group(2))
        if lo > hi:  # 워크북 오타(`주수 250-200`). 하한만 믿는다
            return f"{kind}({stat})>={lo}"
        return f"{kind}({stat})={lo}..{hi}"
    m = re.fullmatch(r"(\d+)\s*미만", text)
    if m:
        return f"{kind}({stat})<{m.group(1)}"
    m = re.fullmatch(r"(\d+)\s*이상", text)
    if m:
        return f"{kind}({stat})>={m.group(1)}"
    return None


def option_selector(part: str, skill: "SkillMeta", ox: str | None) -> str | None:
    """옵션 조각 하나를 판정식으로. 모르는 모양이면 None."""
    part = part.strip()
    if part in ("", "-"):
        return SELECTOR_ALWAYS

    # 스탯 구간. 약칭이 붙은 것과 숫자만 있는 것(스킬의 base_stat을 쓴다) 둘 다 온다.
    for prefix, stat in OPTION_STAT_PREFIXES:
        if part.startswith(prefix):
            rest = part[len(prefix) :].strip()
            base = prefix == "주+수"  # 엘 그란데만 카드 고유 능력치를 본다
            got = _range_selector(stat, rest, base)
            if got:
                return got
    if re.fullmatch(r"\d+\s*[-~]\s*\d+|\d+\s*(미만|이상)", part):
        if not skill.base_stat:
            return None
        return _range_selector(skill.base_stat, part)

    if part in ("O", "o"):
        return ox
    if part in ("X", "x"):
        return _negate(ox) if ox else None

    m = re.fullmatch(r"(\d+)번\s*타순\s*([OX])", part) or re.fullmatch(r"(\d+)번타순([OX])", part)
    if m:
        selector = f"ORDER={m.group(1)}"
        return selector if m.group(2) == "O" else _negate(selector)
    if re.fullmatch(r"(\d+)번\s*타자", part):
        return f"ORDER={re.fullmatch(r'(\d+)번\s*타자', part).group(1)}"
    m = re.fullmatch(r"(\d+)번\s*([OX])?", part)
    if m:
        selector = f"ORDER={m.group(1)}"
        return _negate(selector) if m.group(2) == "X" else selector
    if re.fullmatch(r"\d+(,\s*\d+)+", part):
        orders = sorted(int(x) for x in re.findall(r"\d+", part))
        return f"ORDER={orders[0]}..{orders[-1]}"

    for word, selector in RELIEVER_WORDS.items():
        if part == word:
            return selector
    if part in ("중계, 마무리", "중계,마무리"):
        return "ROLE=RP|CP"
    for word, selector in ROLE_WORDS.items():
        if part == word:
            return selector
        if part == f"명전 {word}":
            return f"{selector}&GRADE=HOF"
        if part == f"일반 {word}":
            return f"{selector}&!GRADE=HOF"
    for word, selector in HAND_WORDS.items():
        if part == word:
            return selector
        if part == f"{word}X":
            return _negate(selector)
        if part == f"{word} O":
            return selector
        if part == f"{word} X":
            return _negate(selector)
    if part in GRADE_WORDS:
        return GRADE_WORDS[part]
    if part == "그외":
        return "!GRADE=HOF"
    m = re.fullmatch(r"(C|1B|2B|3B|SS|LF|CF|RF|DH)\s*([OX])?", part)
    if m:
        selector = f"POS={m.group(1)}"
        return _negate(selector) if m.group(2) == "X" else selector
    return None


def build_selector(option: str | None, skill: "SkillMeta", ox: str | None) -> str:
    """옵션 문자열 전체를 판정식으로. 모르는 모양이 하나라도 있으면 MANUAL이다.

    통째로 먼저 맞춰 본다. `2,3`이나 `주+수 155 미만`처럼 구분자가 뜻의 일부인 옵션이
    있어서, 먼저 쪼개면 조각이 뜻을 잃는다.
    """
    if option is None:
        return SELECTOR_ALWAYS
    whole = option_selector(option, skill, ox)
    if whole is not None:
        return whole
    # 쉼표로 나눠 보고, 그래도 안 되면 `+`까지 나눈다(`스위치+3번`, `좌타 O + 2번 O`).
    for pattern in (r",", r"[,+]"):
        selectors: list[str] = []
        for part in re.split(pattern, option):
            if not part.strip():
                continue
            got = option_selector(part, skill, ox)
            if got is None:
                selectors = []
                break
            if got != SELECTOR_ALWAYS:
                selectors.append(got)
        if selectors:
            return "&".join(selectors)
    return SELECTOR_MANUAL


ORDER_RANGE = re.compile(r"ORDER=(\d+)(?:\.\.(\d+))?")


def combine_gates(condition: str) -> str | None:
    """우리 조건 토큰(`+`로 묶인 것)을 하나의 판정식으로.

    `타순3_4_5+타순4_5`처럼 같은 종류가 겹치면 교집합을 쓴다. `ScoreCalculator`가 확률을
    곱하는 것과 같은 뜻이다. 서로 만족할 수 없는 조합(`포지션_OF+포지션_SS`)은 **추측하지
    않고** None을 돌려 MANUAL로 남긴다. 그런 조합은 우리 효과행 쪽을 들여다볼 거리다.
    """
    parts = condition.split("+")
    if not all(p in CONDITION_SELECTORS for p in parts):
        return None
    selectors = [CONDITION_SELECTORS[p] for p in parts]
    if len(selectors) == 1:
        return selectors[0]

    orders: list[tuple[int, int]] = []
    others: dict[str, set[str]] = {}
    for selector in selectors:
        m = ORDER_RANGE.fullmatch(selector)
        if m:
            lo = int(m.group(1))
            orders.append((lo, int(m.group(2)) if m.group(2) else lo))
            continue
        key, _, value = selector.partition("=")
        others.setdefault(key, set()).add(value)
    if any(len(values) > 1 for values in others.values()):
        return None  # 같은 축에 다른 값 — 동시에 참일 수 없다
    out: list[str] = []
    if orders:
        lo = max(o[0] for o in orders)
        hi = min(o[1] for o in orders)
        if lo > hi:
            return None
        out.append(f"ORDER={lo}" if lo == hi else f"ORDER={lo}..{hi}")
    out.extend(f"{key}={next(iter(values))}" for key, values in sorted(others.items()))
    return "&".join(out)


SELECTOR_ALWAYS = "ALWAYS"
SELECTOR_MANUAL = "MANUAL"


@dataclass
class SkillMeta:
    skill_id: str
    pool: str
    position: str
    name: str
    base_stat: str | None
    conditions: set[str]
    # (조건 -> 레벨별 가중 합). O/X 판정식을 수치로 검증할 때 쓴다.
    conditional_score: dict[str, list[float]]
    max_level: int

    @property
    def is_pitcher(self) -> bool:
        return self.position in PITCHER_POSITIONS


def read_skill_meta(resources: Path) -> dict[str, SkillMeta]:
    weights: dict[str, float] = {}
    with (resources / "stat_weights.csv").open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            weights[row["stat"]] = float(row["weight"])

    effects: dict[str, list[dict[str, str]]] = {}
    with (resources / "score_effects.csv").open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            effects.setdefault(row["skill_id"], []).append(row)

    meta: dict[str, SkillMeta] = {}
    with (resources / "score_skills.csv").open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            skill_id = row["skill_id"]
            rows = effects.get(skill_id, [])
            ladder = LADDERS[row["card_type"]]
            max_level = min(
                max((len(r["values"].split("/")) for r in rows), default=1), len(ladder)
            )
            conditional: dict[str, list[float]] = {}
            for effect in rows:
                condition = effect["condition"] or "ALWAYS"
                if condition == "ALWAYS" or effect["base_stat"]:
                    continue
                values = effect["values"].split("/")
                weight = weights.get(effect["stat"], 0.0)
                bucket = conditional.setdefault(condition, [0.0] * max_level)
                for level in range(max_level):
                    bucket[level] += weight * float(values[min(level, len(values) - 1)])
            meta[skill_id] = SkillMeta(
                skill_id=skill_id,
                pool=row["card_type"],
                position=row["position"],
                name=row["name"],
                base_stat=next((r["base_stat"] for r in rows if r["base_stat"]), None),
                conditions={r["condition"] or "ALWAYS" for r in rows} - {"ALWAYS"},
                conditional_score=conditional,
                max_level=max_level,
            )
    return meta


@dataclass
class SkillScoreRow:
    skill_id: str
    level: int
    option: str
    score: float
    selector: str
    source: str

    def as_csv(self) -> list[str]:
        score = f"{self.score:.6f}".rstrip("0").rstrip(".")
        return [self.skill_id, str(self.level), self.option, score, self.selector, self.source]


def read_skill_scores(
    sheet: Sheet, meta: dict[str, SkillMeta]
) -> tuple[list[SkillScoreRow], list[str]]:
    by_name: dict[tuple[bool, str], list[SkillMeta]] = {}
    for skill in meta.values():
        by_name.setdefault((skill.is_pitcher, normalize_skill_name(skill.name)), []).append(skill)

    raw: list[tuple[SkillMeta, str, str | None, float, str]] = []
    notes: list[str] = []
    for name_col, score_col, role in SKILL_SCORE_COLUMNS:
        for row in range(2, sheet.max_row() + 1):
            text = sheet.text(name_col, row)
            if not text.startswith("["):
                continue
            score = sheet.number(score_col, row)
            if score is None:
                notes.append(f"{name_col}{row}: 점수가 비었다 ({text})")
                continue
            label, name, option = split_skill_label(text)
            candidates = by_name.get((role == "PITCHER", normalize_skill_name(name)), [])
            if len(candidates) != 1:
                notes.append(f"{name_col}{row}: 스킬을 찾지 못했다 ({text})")
                continue
            raw.append((candidates[0], label, option, score, text))

    # O/X 판정식은 스킬마다 다르다. 우리 조건 토큰에서 만들고 수치로 검증한 것만 쓴다.
    ox_by_skill = _resolve_ox(raw)

    out: list[SkillScoreRow] = []
    for skill, label, option, score, source in raw:
        ladder = LADDERS[skill.pool]
        if label not in ladder:
            notes.append(f"{skill.skill_id}: 사다리에 없는 레벨 {label} ({source})")
            continue
        level = min(ladder.index(label) + 1, skill.max_level)
        selector = build_selector(option, skill, ox_by_skill.get(skill.skill_id))
        out.append(SkillScoreRow(skill.skill_id, level, option or "", score, selector, source))

    # 효과행이 없는 스킬(`선구안`·`번트전문`)은 레벨이 하나뿐이라 S0~S4가 같은 칸으로 접힌다.
    # 점수도 0으로 같으니 한 줄만 남긴다. 값이 다르면 접지 않고 검증기가 잡게 둔다.
    folded: dict[tuple[str, int, str, str], SkillScoreRow] = {}
    deduped: list[SkillScoreRow] = []
    for row in out:
        key = (row.skill_id, row.level, row.option, row.selector)
        kept = folded.get(key)
        if kept is None:
            folded[key] = row
            deduped.append(row)
        elif abs(kept.score - row.score) > 1e-9:
            notes.append(
                f"{row.skill_id} L{row.level} {row.option!r}: 같은 칸에 점수가 둘 "
                f"({kept.score} / {row.score})"
            )
            deduped.append(row)

    # 판정식이 좁은 것부터. 마지막에 오는 ALWAYS가 자연스러운 기본값이 된다.
    deduped.sort(key=lambda r: (r.skill_id, r.level, r.selector in (SELECTOR_ALWAYS,), r.option))
    return deduped, notes


def _resolve_ox(
    raw: list[tuple[SkillMeta, str, str | None, float, str]]
) -> dict[str, str]:
    """`(O)`/`(X)` 한 쌍이 어떤 조건을 뜻하는지 정한다.

    스킬이 가진 조건 토큰이 딱 하나고, 그것이 덱 정보로 판정 가능하며, **엑셀의 O−X 차이가
    그 조건이 주는 점수와 수치로 맞을 때만** 자동으로 쓴다. 맞지 않으면 손대지 않고
    `MANUAL`로 남겨 사용자가 고르게 한다.
    """
    # 옵션이 `O`/`X` 한 글자인 것과 `O, 정 200-233`처럼 뒤에 구간이 붙은 것이 둘 다 있다.
    # 뒤가 같은 것끼리 짝지어야 O−X 차이가 조건 하나의 값이 된다.
    pairs: dict[str, dict[tuple[int, str], dict[str, float]]] = {}
    for skill, label, option, score, _ in raw:
        if option is None:
            continue
        head, _, rest = option.strip().partition(",")
        flag = head.strip().upper()
        if flag not in ("O", "X"):
            continue
        ladder = LADDERS[skill.pool]
        if label not in ladder:
            continue
        level = min(ladder.index(label) + 1, skill.max_level)
        pairs.setdefault(skill.skill_id, {}).setdefault((level, rest.strip()), {})[flag] = score

    resolved: dict[str, str] = {}
    by_id = {skill.skill_id: skill for skill, *_ in raw}
    for skill_id, levels in pairs.items():
        skill = by_id[skill_id]
        if len(skill.conditions) != 1:
            continue  # 조건이 여럿이면 O가 어느 쪽인지 알 수 없다
        condition = next(iter(skill.conditions))
        selector = combine_gates(condition)
        if selector is None:
            continue
        expected = skill.conditional_score.get(condition)
        if not expected:
            continue
        complete = [s for s in levels.values() if "O" in s and "X" in s]
        if complete and all(
            abs((scores["O"] - scores["X"]) - expected[level - 1]) < 0.01
            for (level, _rest), scores in levels.items()
            if "O" in scores and "X" in scores
        ):
            resolved[skill_id] = selector
    return resolved


# --- 7. 팀 버프 --------------------------------------------------------------
#
# 게임에는 "라인업에 등록된 모든 타자/투수"를 올려 주는 스킬이 여섯 있다. 우리 CSV는 그
# 수치를 이미 갖고 있지만 **보유자 한 명에게만** 적용하고 있었다.
#
# 워크북은 이 부분을 스킬 점수에서 빼 두고(`(팀버프 X)`·`(투수버프X)` 변형) 팀 설정
# 드롭다운으로 따로 받는다. 검산: 타자 WBC 에이스 S는 8×(1.1+0.9+0.4)=19.2로 워크북 값과
# 같다 — 팀 버프가 빠져 있다는 뜻이다.
#
# 아래 표는 "어느 효과행이 팀 버프인가"를 못 박는다. 채점에서 그 행을 빼고 팀 전체에 더한다.
# (skill_id, stat, condition) -> (적용 대상, 보유자 자리 조건)
TEAM_BUFF_EFFECTS: list[tuple[str, str, str, str, str]] = [
    # skill_id,   stat,   condition,  scope,     requires_slot
    ("HOF_012", "파워", "ALWAYS", "BATTER", ""),
    ("HOF_012", "정확", "ALWAYS", "BATTER", ""),
    ("HOF_031", "구위", "ALWAYS", "PITCHER", ""),
    ("HOF_031", "변화", "ALWAYS", "PITCHER", ""),
    ("WBC_005", "파워", "ALWAYS", "BATTER", ""),
    ("WBC_005", "정확", "ALWAYS", "BATTER", ""),
    ("WBC_011", "변화", "ALWAYS", "PITCHER", ""),
    ("WBC_011", "구위", "ALWAYS", "PITCHER", ""),
    ("M_014", "변화", "포지션_C", "PITCHER", "C"),
    ("M_014", "구위", "포지션_C", "PITCHER", "C"),
    ("G_027", "변화", "포지션_C", "PITCHER", "C"),
    ("G_027", "구위", "포지션_C", "PITCHER", "C"),
]

# 우리 CSV에서 팀 버프 행을 가려내는 열쇠. 같은 스킬·스탯·조건이 자기 효과와 팀 효과로
# 두 번 나오는 경우가 있어(WBC 에이스) 값 사다리까지 봐야 한다.
TEAM_BUFF_VALUES: dict[tuple[str, str], str] = {
    ("HOF_012", "파워"): "1/1/1/1/1/2",
    ("HOF_012", "정확"): "1/1/1/1/1/2",
    ("HOF_031", "구위"): "1/1/1/1/1/2",
    ("HOF_031", "변화"): "1/1/1/1/1/2",
    ("WBC_005", "파워"): "1/2/2",
    ("WBC_005", "정확"): "1/1/2",
    ("WBC_011", "변화"): "1/1/2",
    # 우리 CSV에 빠져 있다. 워크북의 팀 설정 수식에서 가져왔다(S→1, S1→2, S2→2).
    ("WBC_011", "구위"): "1/2/2",
    ("M_014", "변화"): "1",
    ("M_014", "구위"): "2",
    ("G_027", "변화"): "0/1/1/1/1/1/1/2/2",
    ("G_027", "구위"): "0/0/0/0/0/1/1/1/1",
}


def build_team_buffs(meta: dict[str, SkillMeta]) -> list[list[str]]:
    rows: list[list[str]] = []
    for skill_id, stat, condition, scope, requires_slot in TEAM_BUFF_EFFECTS:
        if skill_id not in meta:
            raise WorkbookError(f"팀 버프 스킬이 CSV에 없다: {skill_id}")
        values = TEAM_BUFF_VALUES[(skill_id, stat)]
        rows.append([skill_id, scope, requires_slot, stat, condition, values])
    return rows


# --- 8. CLI ------------------------------------------------------------------

OUTPUTS = {
    "stat_growth_transcendence.csv": ["card_grade", "card_variant", "stat", "values"],
    "stat_growth_enhancement.csv": ["card_grade", "card_variant", "stat", "values"],
    "deck_score_rewards.csv": [
        "ladder",
        "threshold",
        "side",
        "target",
        "stat",
        "amount",
        "condition",
    ],
    "excel_skill_scores.csv": ["skill_id", "level", "option", "score", "selector", "source"],
    "team_buff_skills.csv": [
        "skill_id",
        "scope",
        "requires_slot",
        "stat",
        "condition",
        "values",
    ],
}


def build(workbook: Workbook, resources: Path = RESOURCES) -> tuple[dict[str, list[list[str]]], list[str]]:
    lineup = workbook.sheet("라인업")
    checked, problems = verify_rewards(lineup)
    if problems:
        raise WorkbookError(
            f"수식 {checked}개 중 {len(problems)}개가 캐시값과 다르다:\n  "
            + "\n  ".join(problems[:5])
        )
    meta = read_skill_meta(resources)
    scores, notes = read_skill_scores(workbook.sheet(SKILL_SCORE_SHEET), meta)
    notes.insert(0, f"덱 스코어 수식 {checked}개가 캐시값과 일치한다")
    tables = {
        "stat_growth_transcendence.csv": [
            row.as_csv() for row in read_growth(workbook.sheet("초월"), 2, "D", "S")
        ],
        "stat_growth_enhancement.csv": [
            row.as_csv() for row in read_growth(workbook.sheet("강화"), 3, "D", "W")
        ],
        "deck_score_rewards.csv": [row.as_csv() for row in read_rewards(lineup)],
        "excel_skill_scores.csv": [row.as_csv() for row in scores],
        "team_buff_skills.csv": build_team_buffs(meta),
    }
    return tables, notes


def render(name: str, rows: list[list[str]]) -> str:
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(OUTPUTS[name])
    writer.writerows(rows)
    return buffer.getvalue()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workbook", required=True, type=Path, help="덱 관리 워크북 경로")
    parser.add_argument("--out", type=Path, default=RESOURCES, help="CSV를 쓸 디렉터리")
    parser.add_argument(
        "--check", action="store_true", help="쓰지 않고 커밋된 CSV와 대조한다"
    )
    parser.add_argument("--verbose", action="store_true", help="읽으며 남긴 메모를 전부 보여 준다")
    args = parser.parse_args(argv)

    if not args.workbook.exists():
        print(f"워크북이 없다: {args.workbook}", file=sys.stderr)
        return 2

    try:
        tables, notes = build(Workbook(args.workbook), RESOURCES)
    except WorkbookError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 2

    for note in notes if args.verbose else notes[:1]:
        print(f"note  {note}")
    if not args.verbose and len(notes) > 1:
        print(f"note  메모 {len(notes) - 1}건 더 (--verbose)")

    failed = False
    for name, rows in tables.items():
        text = render(name, rows)
        target = args.out / name
        if args.check:
            current = target.read_text(encoding="utf-8") if target.exists() else ""
            if current != text:
                print(f"ERROR {name}: 커밋된 내용과 다르다 ({len(rows)}행 추출)")
                failed = True
            else:
                print(f"ok    {name} ({len(rows)}행)")
        else:
            target.write_text(text, encoding="utf-8")
            print(f"wrote {name} ({len(rows)}행)")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
