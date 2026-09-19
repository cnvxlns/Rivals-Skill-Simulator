#!/usr/bin/env python3
"""게임사 공지에 우리 CSV가 모르는 스킬이 있는지 본다.

2026-09-10 13차 Live 업데이트로 들어온 `엘 그란데`·`오펜시브 리더`가 나흘 뒤까지
CSV에 없었다. 사람이 공지를 놓치면 데이터가 조용히 밀린다. 그 감지를 자동화한다.

**상태를 저장하지 않는다.** "이미 본 글" 목록을 두면 확인만 하고 반영을 안 한 글이
영영 조용해진다. 매번 최근 공지를 다시 훑고 CSV에 없는 스킬을 계속 보고하므로,
실제로 CSV에 들어가야만 조용해진다.

이 스크립트는 **감지만** 한다. 설명문을 효과행으로 옮기는 일은 하지 않는다.
파서(`skill_parser.py`)가 처음 보는 조건 문구에서 조용히 틀린 행을 만들기 때문에
(예: "합이 155 이상인 경우" -> `주루 ALWAYS 155`), 그 단계는 사람 손을 거쳐야 한다.

    python3 tools/check_notices.py            # 보고만, 항상 exit 0
    python3 tools/check_notices.py --strict   # 누락이 있으면 exit 1 (CI용)

의존성 없이 stdlib만 쓴다.
"""
from __future__ import annotations

import argparse
import csv
import html as ihtml
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKILLS_CSV = ROOT / "backend" / "src" / "main" / "resources" / "score_skills.csv"

BASE = "https://community.withhive.com"
# 2=공지사항, 10=Live 업데이트, 11=개발자 노트. 신규 스킬은 주로 10으로 온다.
BOARDS = (10, 2)
POSTS_PER_BOARD = 8
DELAY = 1.2
UA = "Mozilla/5.0 rivals-skill-simulator/0.1 (+notice check)"


class _Tables(HTMLParser):
    """본문의 <table>을 행 리스트로 뽑는다. 목록 페이지만 JS고 본문은 서버렌더다."""

    def __init__(self) -> None:
        super().__init__()
        self.tables: list[list[list[str]]] = []
        self._t: list[list[str]] | None = None
        self._r: list[str] | None = None
        self._c: str | None = None

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self._t = []
        elif tag == "tr" and self._t is not None:
            self._r = []
        elif tag in ("td", "th") and self._r is not None:
            self._c = ""

    def handle_data(self, data):
        if self._c is not None:
            self._c += data

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self._c is not None and self._r is not None:
            self._r.append(re.sub(r"\s+", " ", ihtml.unescape(self._c)).strip())
            self._c = None
        elif tag == "tr" and self._r is not None:
            if any(self._r):
                assert self._t is not None
                self._t.append(self._r)
            self._r = None
        elif tag == "table" and self._t is not None:
            if self._t:
                self.tables.append(self._t)
            self._t = None


def _get(url: str, data: bytes | None = None, referer: str | None = None) -> str:
    headers = {"User-Agent": UA, "Accept-Language": "ko-KR,ko;q=0.9"}
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8", "ignore")


def fetch_board(board: int) -> list[dict[str, str]]:
    """목록 API. 한국어 글을 받으려면 ko Referer가 필요하다(영문 글은 idx가 다르다)."""
    payload = urllib.parse.urlencode(
        {
            "page": 1, "board_idx": board, "board_type": 1, "board_comment": 0,
            "is_mobile": 0, "select_type": 1, "view_type": "list",
        }
    ).encode()
    raw = _get(
        f"{BASE}/MLB9IRIVALS/board/list/getBoardList",
        data=payload,
        referer=f"{BASE}/MLB9IRIVALS/ko/board/{board}",
    )
    items = json.loads(raw).get("contents_list", {}).get("list", [])
    return [
        {
            "title": (o.get("title") or "").strip(),
            "date": o.get("regdate") or "",
            "url": f"{BASE}/MLB9IRIVALS/ko/board/{board}/{o.get('idx')}",
        }
        for o in items[:POSTS_PER_BOARD]
    ]


def extract_skills(html: str) -> list[tuple[str, str]]:
    """`스킬명 | 설명` 표만 골라 (이름, 설명)으로 돌려준다.

    같은 글에 신규 카드표·초월 보너스표·트레이드표가 섞여 있으므로 헤더로 가른다.
    """
    parser = _Tables()
    parser.feed(html)
    out: list[tuple[str, str]] = []
    for table in parser.tables:
        header = [c.replace(" ", "") for c in table[0]]
        if "스킬명" not in header:
            continue
        name_at = header.index("스킬명")
        desc_at = header.index("설명") if "설명" in header else None
        for row in table[1:]:
            if len(row) <= name_at:
                continue
            name = row[name_at].strip()
            # "빅 허트 (DH 전용 스킬)"처럼 이름 뒤에 붙는 괄호 주석을 떼어낸다.
            name = re.sub(r"\s*\([^)]*\)\s*$", "", name).strip()
            if not name or len(name) > 40:
                continue
            desc = row[desc_at].strip() if desc_at is not None and len(row) > desc_at else ""
            out.append((name, desc))
    return out


def known_names() -> set[str]:
    with SKILLS_CSV.open(encoding="utf-8-sig", newline="") as fp:
        return {_key(r["name"]) for r in csv.DictReader(fp) if r.get("name")}


def _key(name: str) -> str:
    """띄어쓰기 표기가 공지마다 흔들려서(빅 허트/빅허트) 공백을 지우고 맞춘다."""
    return re.sub(r"\s+", "", name)


def main() -> int:
    ap = argparse.ArgumentParser(description="공지에 있는데 CSV에 없는 스킬 찾기")
    ap.add_argument("--strict", action="store_true", help="누락이 있으면 exit 1")
    args = ap.parse_args()

    have = known_names()
    missing: dict[str, dict[str, str]] = {}
    scanned = 0
    errors: list[str] = []

    for board in BOARDS:
        try:
            posts = fetch_board(board)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"board {board} 목록 실패: {exc}")
            continue
        time.sleep(DELAY)

        for post in posts:
            try:
                body = _get(post["url"])
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{post['url']} 실패: {exc}")
                continue
            scanned += 1
            for name, desc in extract_skills(body):
                if _key(name) in have or _key(name) in missing:
                    continue
                missing[_key(name)] = {
                    "name": name,
                    "desc": desc,
                    "post": post["title"],
                    "date": post["date"][:10],
                    "url": post["url"],
                }
            time.sleep(DELAY)

    for line in errors:
        print(f"warn: {line}", file=sys.stderr)

    if not missing:
        print(f"공지 {scanned}건 확인. CSV에 없는 스킬 없음.")
        return 0

    print(f"공지 {scanned}건 확인. CSV에 없는 스킬 {len(missing)}건:\n")
    for item in sorted(missing.values(), key=lambda x: x["date"], reverse=True):
        print(f"● {item['name']}")
        print(f"   출처 {item['date']}  {item['post']}")
        print(f"   {item['url']}")
        if item["desc"]:
            print(f"   설명 {item['desc'][:200]}")
        print()

    print("설명문을 효과행으로 옮길 때는 tools/skill_parser.py를 초안으로만 쓰고")
    print("반드시 사람이 확인한다. 처음 보는 조건 문구에서 조용히 틀린 행이 나온다.")
    return 1 if args.strict else 0


if __name__ == "__main__":
    raise SystemExit(main())
