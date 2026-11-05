"""공지 감시의 게시판 누락과 조회 실패 은폐 회귀 테스트 (네트워크 미사용)."""
from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.parse import parse_qs

import check_notices as notices


# 2026-10-21 v4.07.00 공지의 표 구조. 표시용 스타일과 선수 명단은 생략한다.
# https://community.withhive.com/MLB9IRIVALS/ko/board/9/17979
UPDATE_HTML = """
<table class="ck-table-resized"><colgroup><col><col></colgroup><tbody>
<tr><td><p><span>스킬명</span></p></td><td><p><span>설명</span></p></td></tr>
<tr><td><p><span>2026 퍼포머트리오</span></p></td><td>
<p><span>모든 능력치가 10 증가합니다.</span></p>
<p><span>리그 모드, 라이브 매치에서 추가로 모든 능력치가 10 증가합니다.</span></p>
</td></tr></tbody></table>
<table><tbody><tr><td>스킬명</td><td>설명</td></tr>
<tr><td><p><span>WAR 리더 (타자)</span></p></td>
<td><p>파워, 정확, 선구 능력치가 9 증가합니다.</p>
<p>상대 투수의 변화, 구위 능력치를 6 감소시킵니다.</p></td></tr>
<tr><td><p><span>WAR 리더 (투수)</span></p></td>
<td><p>변화, 구위 능력치가 9 증가합니다.</p>
<p>상대 타자의 파워, 정확, 선구 능력치를 6 감소시킵니다.</p></td></tr>
</tbody></table>
<table><tbody><tr><td><p><span><strong>스킬명</strong></span></p></td>
<td><p><span><strong>설명</strong></span></p></td></tr>
<tr><td>신속한 처리</td><td>중계 투수로 등판 시 구위, 변화 능력치가 증가합니다.</td></tr>
</tbody></table>
<table><tr><td>구분</td><td>초월 레벨 3</td></tr>
<tr><td>추가 잠재력 (타자)</td><td>풀스윙 +1</td></tr></table>
"""
POST = {
    "title": "[안내] v4.07.00 업데이트 안내",
    "date": "2026-10-21 10:00:00",
    "url": f"{notices.BASE}/MLB9IRIVALS/ko/board/9/17979",
}
API_ITEM = {"title": POST["title"], "regdate": POST["date"], "idx": 17979}
# 2026-06-24 버전 업데이트 공지(9/16259)의 이름 표기.
OLD_UPDATE_HTML = """
<table><tr><td>스킬명</td><td>설명</td></tr>
<tr><td>(타자) 키플레이어</td><td>타자 효과</td></tr>
<tr><td>(투수) 키플레이어</td><td>투수 효과</td></tr>
<tr><td>파워 피쳐</td><td>기존 파워 피처와 동일한 효과</td></tr>
</table>
"""


class FetchBoardTests(unittest.TestCase):
    def test_version_update_board_is_monitored(self):
        self.assertIn(9, notices.BOARDS)
        self.assertIn(10, notices.BOARDS)
        self.assertIn(2, notices.BOARDS)

    def test_korean_request_and_post_limit(self):
        raw = json.dumps({"list": {"list": [API_ITEM] * 10}})
        with patch.object(notices, "_get", return_value=raw) as get:
            posts = notices.fetch_board(9)
        self.assertEqual(posts, [POST] * notices.POSTS_PER_BOARD)
        request = get.call_args
        self.assertEqual(request.args[0],
                         f"{notices.BASE}/MLB9IRIVALS/board/list/getGeneralPostList")
        self.assertEqual(parse_qs(request.kwargs["data"].decode()), {
            "page": ["1"], "board_idx": ["9"], "board_comment": ["1"],
            "boardtype1_preview_image": ["1"], "is_mobile": ["0"],
            "sort_type": ["1"], "header": ["0"],
        })
        self.assertEqual(request.kwargs["referer"], f"{notices.BASE}/MLB9IRIVALS/ko/board/9")

    def test_get_sends_korean_language_and_user_agent(self):
        with patch.object(notices.urllib.request, "urlopen") as urlopen:
            urlopen.return_value.__enter__.return_value.read.return_value = "한국어".encode()
            result = notices._get("https://example.test", referer="https://example.test/ko")
        request = urlopen.call_args.args[0]
        headers = {key.lower(): value for key, value in request.header_items()}
        self.assertEqual(result, "한국어")
        self.assertEqual(headers["accept-language"], "ko-KR,ko;q=0.9")
        self.assertEqual(headers["user-agent"], notices.UA)
        self.assertEqual(headers["referer"], "https://example.test/ko")

    def test_invalid_or_empty_lists_are_errors(self):
        for response in [None, [], {}, {"list": None},
                         {"list": {}}, {"list": {"list": None}},
                         {"list": {"list": {}}},
                         {"list": {"list": []}},
                         {"contents_list": {"list": [API_ITEM]}}]:
            with self.subTest(response=response):
                with patch.object(notices, "_get", return_value=json.dumps(response)):
                    with self.assertRaises(ValueError):
                        notices.fetch_board(9)

    def test_invalid_entries_are_errors(self):
        for item in [None, {}, {**API_ITEM, "idx": None}, {**API_ITEM, "idx": -1},
                     {**API_ITEM, "title": " "}, {**API_ITEM, "regdate": None}]:
            with self.subTest(item=item):
                raw = json.dumps({"list": {"list": [item]}})
                with patch.object(notices, "_get", return_value=raw):
                    with self.assertRaises(ValueError):
                        notices.fetch_board(9)


class SkillExtractionTests(unittest.TestCase):
    def test_actual_update_tables_and_role_suffixes(self):
        skills = notices.extract_skills(UPDATE_HTML)
        self.assertEqual([name for name, _ in skills],
                         ["2026 퍼포머트리오", "WAR 리더", "WAR 리더", "신속한 처리"])
        self.assertIn("라이브 매치", skills[0][1])
        self.assertIn("파워, 정확, 선구", skills[1][1])
        self.assertIn("변화, 구위 능력치가 9", skills[2][1])

    def test_known_names_normalize_csv_whitespace(self):
        with tempfile.TemporaryDirectory() as directory:
            csv_path = Path(directory) / "skills.csv"
            csv_path.write_text("name\nWAR 리더\n2026 퍼포머트리오\n", encoding="utf-8-sig")
            with patch.object(notices, "SKILLS_CSV", csv_path):
                self.assertEqual(notices.known_names(), {"WAR리더", "2026퍼포머트리오"})

    def test_only_leading_batter_and_pitcher_annotations_are_removed(self):
        self.assertEqual([name for name, _ in notices.extract_skills(OLD_UPDATE_HTML)],
                         ["키플레이어", "키플레이어", "파워 피쳐"])
        html = "<table><tr><th>스킬명</th></tr><tr><td>(특수) 새 스킬</td></tr></table>"
        self.assertEqual(notices.extract_skills(html), [("(특수) 새 스킬", "")])

    def test_alias_matches_only_the_confirmed_spelling(self):
        self.assertEqual(notices._key("파워 피쳐"), notices._key("파워 피처"))
        self.assertNotEqual(notices._key("파워 피쳐 II"), notices._key("파워 피처 II"))


class MainTests(unittest.TestCase):
    def run_check(self, *, strict=True, known=(), boards=(9,), fetch=None, get=None):
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(notices.sys, "argv", ["check_notices.py"] +
                                            (["--strict"] if strict else [])))
            stack.enter_context(patch.object(notices, "BOARDS", boards))
            stack.enter_context(patch.object(notices, "known_names", return_value=set(known)))
            stack.enter_context(patch.object(notices.time, "sleep"))
            stack.enter_context(patch.object(notices, "fetch_board", **(fetch or {"return_value": [POST]})))
            stack.enter_context(patch.object(notices, "_get", **(get or {"return_value": UPDATE_HTML})))
            stack.enter_context(contextlib.redirect_stdout(stdout))
            stack.enter_context(contextlib.redirect_stderr(stderr))
            status = notices.main()
        return status, stdout.getvalue(), stderr.getvalue()

    def test_missing_skills_fail_strict_and_deduplicate_roles(self):
        status, output, errors = self.run_check()
        self.assertEqual(status, 1)
        self.assertIn("CSV에 없는 스킬 3건", output)
        self.assertEqual(output.count("● WAR 리더"), 1)
        self.assertIn(POST["url"], output)
        self.assertEqual(errors, "")

    def test_all_known_skills_pass(self):
        status, output, errors = self.run_check(
            known={"2026퍼포머트리오", "WAR리더", "신속한처리"})
        self.assertEqual(status, 0)
        self.assertIn("공지 1건 확인. CSV에 없는 스킬 없음.", output)
        self.assertEqual(errors, "")

    def test_older_announcement_variants_match_existing_skills(self):
        status, output, errors = self.run_check(
            known={"키플레이어", "파워피처"}, get={"return_value": OLD_UPDATE_HTML})
        self.assertEqual(status, 0)
        self.assertIn("CSV에 없는 스킬 없음", output)
        self.assertEqual(errors, "")

    def test_normalization_keeps_genuinely_missing_skills_reported(self):
        body = OLD_UPDATE_HTML + UPDATE_HTML
        status, output, errors = self.run_check(
            known={"키플레이어", "파워피처", "2026퍼포머트리오", "WAR리더"},
            get={"return_value": body})
        self.assertEqual(status, 1)
        self.assertIn("CSV에 없는 스킬 1건", output)
        self.assertIn("● 신속한 처리", output)
        self.assertEqual(errors, "")

    def test_board_failure_fails_strict_without_success_claim(self):
        status, output, errors = self.run_check(fetch={"side_effect": ValueError("빈 목록")})
        self.assertEqual(status, 1)
        self.assertIn("완전히 확인하지 못했습니다", output)
        self.assertNotIn("스킬 없음", output)
        self.assertIn("board 9 목록 실패", errors)

    def test_post_failure_fails_strict_without_success_claim(self):
        status, output, errors = self.run_check(get={"side_effect": OSError("timeout")})
        self.assertEqual(status, 1)
        self.assertNotIn("스킬 없음", output)
        self.assertIn("timeout", errors)

    def test_partial_scan_with_error_fails_strict(self):
        status, output, errors = self.run_check(
            boards=(9, 10), fetch={"side_effect": [[POST], OSError("timeout")]},
            known={"2026퍼포머트리오", "WAR리더", "신속한처리"})
        self.assertEqual(status, 1)
        self.assertIn("공지 1건 확인, 조회 실패 1건", output)
        self.assertIn("board 10 목록 실패", errors)

    def test_no_scans_fail_strict(self):
        status, output, errors = self.run_check(boards=())
        self.assertEqual(status, 1)
        self.assertNotIn("스킬 없음", output)
        self.assertIn("확인한 공지가 없습니다", errors)

    def test_report_mode_preserves_zero_exit_for_missing_skills_and_errors(self):
        for kwargs in [{}, {"fetch": {"side_effect": OSError("timeout")}}]:
            with self.subTest(kwargs=kwargs):
                status, _, _ = self.run_check(strict=False, **kwargs)
                self.assertEqual(status, 0)


if __name__ == "__main__":
    unittest.main()
