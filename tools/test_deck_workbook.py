"""워크북 추출기와 덱 데이터 검증기의 회귀 테스트.

원천 워크북은 추적하지 않으므로(`.gitignore`의 `docs/*`) 대부분의 테스트는 **여기서 최소
xlsx를 직접 만들어** 돌린다. 바이너리를 커밋하지 않고도 리더가 무엇을 기대하는지가 코드에
그대로 드러난다. 워크북이 실제로 있으면(`--workbook` 환경변수) 전수 대조까지 한 번 더 한다.

    cd tools && python3 -m unittest discover -p 'test_*.py'
"""
from __future__ import annotations

import io
import os
import unittest
import zipfile
from pathlib import Path

import deck_workbook as D
import validate_deck_data as V

RESOURCES = Path(__file__).resolve().parent.parent / "backend" / "src" / "main" / "resources"


# --- 최소 xlsx 만들기 --------------------------------------------------------


def make_workbook(sheets: dict[str, str], shared: list[str] | None = None) -> Path:
    """시트 이름 -> `<sheetData>` 안쪽 XML. 임시 파일 경로를 돌려준다."""
    import tempfile

    fd, name = tempfile.mkstemp(suffix=".xlsx")
    os.close(fd)
    path = Path(name)
    with zipfile.ZipFile(path, "w") as zf:
        entries = "".join(
            f'<sheet name="{n}" sheetId="{i}" r:id="rId{i}"/>'
            for i, n in enumerate(sheets, start=1)
        )
        zf.writestr(
            "xl/workbook.xml",
            '<?xml version="1.0"?><workbook '
            'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            f"<sheets>{entries}</sheets></workbook>",
        )
        rels = "".join(
            f'<Relationship Id="rId{i}" Target="worksheets/sheet{i}.xml" '
            'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>'
            for i in range(1, len(sheets) + 1)
        )
        zf.writestr(
            "xl/_rels/workbook.xml.rels",
            '<?xml version="1.0"?><Relationships '
            f'xmlns="http://schemas.openxmlformats.org/package/2006/relationships">{rels}'
            "</Relationships>",
        )
        if shared:
            items = "".join(f"<si>{s}</si>" for s in shared)
            zf.writestr(
                "xl/sharedStrings.xml",
                '<?xml version="1.0"?><sst '
                'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
                f'count="{len(shared)}">{items}</sst>',
            )
        for i, body in enumerate(sheets.values(), start=1):
            zf.writestr(
                f"xl/worksheets/sheet{i}.xml",
                '<?xml version="1.0"?><worksheet '
                'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
                f"<sheetData>{body}</sheetData></worksheet>",
            )
    return path


def row(number: int, *cells: str) -> str:
    return f'<row r="{number}">{"".join(cells)}</row>'


def num(ref: str, value: str) -> str:
    return f'<c r="{ref}"><v>{value}</v></c>'


def shared_ref(ref: str, index: int) -> str:
    return f'<c r="{ref}" t="s"><v>{index}</v></c>'


class WorkbookReaderTest(unittest.TestCase):
    def setUp(self) -> None:
        self.paths: list[Path] = []

    def tearDown(self) -> None:
        for path in self.paths:
            path.unlink(missing_ok=True)

    def build(self, sheets, shared=None) -> D.Workbook:
        path = make_workbook(sheets, shared)
        self.paths.append(path)
        return D.Workbook(path)

    def test_shared_string_joins_runs(self):
        """<si>가 서식 때문에 <r><t>로 쪼개져 있어도 한 문장으로 읽어야 한다."""
        book = self.build(
            {"S": row(1, shared_ref("A1", 0))},
            shared=["<r><t>스피드</t></r><r><t>&amp;컨택</t></r>"],
        )
        self.assertEqual(book.sheet("S").text("A1"[0], 1), "스피드&컨택")

    def test_error_cell_has_no_value(self):
        """#N/A(t=e)는 값이 없는 것으로 본다. 워크북에 90칸 있다."""
        book = self.build({"S": row(1, '<c r="A1" t="e"><v>#N/A</v></c>')})
        self.assertIsNone(book.sheet("S").value("A", 1))

    def test_two_letter_columns(self):
        book = self.build({"S": row(1, num("Z1", "1"), num("AA1", "2"), num("AB1", "3"))})
        sheet = book.sheet("S")
        self.assertEqual(sheet.text("AA", 1), "2")
        self.assertEqual(sheet.text("AB", 1), "3")

    def test_shared_formula_expands_over_range(self):
        """마스터 한 칸이 범위 전체를 덮는다. 펼치지 않으면 규칙이 통째로 빠진다."""
        book = self.build(
            {
                "S": row(
                    1,
                    '<c r="A1"><f t="shared" ref="A1:B2" si="0">IF($Z$1="O",3,0)+A2</f><v>3</v></c>',
                    '<c r="B1"><f t="shared" si="0"/><v>3</v></c>',
                )
                + row(
                    2,
                    '<c r="A2"><f t="shared" si="0"/><v>3</v></c>',
                    '<c r="B2"><f t="shared" si="0"/><v>3</v></c>',
                )
            }
        )
        sheet = book.sheet("S")
        # 열이 한 칸 오른쪽이면 상대참조도 한 칸 옮겨지고, $가 붙은 쪽은 그대로다.
        self.assertEqual(sheet.formulas[("B", 1)], 'IF($Z$1="O",3,0)+B2')
        self.assertEqual(sheet.formulas[("A", 2)], 'IF($Z$1="O",3,0)+A3')

    def test_sheet_lookup_by_name(self):
        book = self.build({"라인업": row(1, num("A1", "1")), "초월": row(1, num("A1", "2"))})
        self.assertEqual(book.sheet("초월").text("A", 1), "2")
        with self.assertRaises(D.WorkbookError):
            book.sheet("없는시트")


class FormulaTest(unittest.TestCase):
    def parse(self, text: str):
        return D.parse_formula(text)

    def test_shapes_seen_in_the_workbook(self):
        """워크북에 실제로 있는 열세 가지 모양. 전부 읽혀야 한다."""
        for text in [
            'IF($AU$10="o",3,0)',
            'IF(AND($AU$21="O",$AF11>9),1,0)',
            'IF(AND($AU$20="O",$C11<3),1,0)',
            'IF(AND($AU$32="O",$C11>2,$C11<6),2,0)',
            'IF(AND($AU$32="O",2<$C11,$C11<6),2,0)',
            'IF(AND($AY$29="o",$C11>5),1,0)',
            'IF(OR($AY$26="o",$AZ$26="o"),1,0)',
            'IF(AND($AU$13="O",$D11="라이브/시즌"),2,IF(AND($AV$13="O",$D11<>"라이브/시즌"),1,0))',
            'IF($AY$33>1880,IF(AND($F11-$AY$33<10,$F11-$AY$33>-1),1,0),0)',
        ]:
            with self.subTest(text=text):
                self.assertIsNotNone(self.parse(text))

    def test_unary_minus(self):
        """`>-1`의 마이너스. 여기서 한 번 막혔다."""
        node = self.parse("IF($F11-$AY$33>-1,1,0)")
        self.assertEqual(node[0], "fn")

    def test_unknown_function_is_refused(self):
        with self.assertRaises(D.WorkbookError):
            D.evaluate(self.parse("SUM(A1,A2)"), D.Sheet(name="S"))

    def test_garbage_is_refused(self):
        with self.assertRaises(D.WorkbookError):
            self.parse("IF($AU$10=,3,0)")

    def test_evaluate_uses_cached_values(self):
        sheet = D.Sheet(name="S", values={("AU", 10): "O", ("C", 11): "2"})
        self.assertEqual(D.evaluate(self.parse('IF($AU$10="o",3,0)'), sheet), 3.0)
        self.assertEqual(D.evaluate(self.parse('IF(AND($AU$10="O",$C11<3),1,0)'), sheet), 1.0)
        self.assertEqual(D.evaluate(self.parse('IF(AND($AU$10="O",$C11>5),1,0)'), sheet), 0.0)

    def test_empty_cell_compares_as_blank(self):
        """토글을 비워 두면 조건이 거짓이어야 한다."""
        sheet = D.Sheet(name="S")
        self.assertEqual(D.evaluate(self.parse('IF($AU$10="o",3,0)'), sheet), 0.0)


class CardNameTest(unittest.TestCase):
    def test_spacing_difference_is_absorbed(self):
        """드롭다운은 `FA 시그니처`, 표 키는 `FA시그니처`. 엑셀 안에서는 #N/A가 난다."""
        self.assertEqual(D.card_key("FA 시그니처"), ("SIGNATURE", "FA"))
        self.assertEqual(D.card_key("FA시그니처"), ("SIGNATURE", "FA"))

    def test_variant_is_a_separate_axis(self):
        self.assertEqual(D.card_key("WBC 시그니처 블랙"), ("SIGNATURE_BLACK", "WBC"))
        self.assertEqual(D.card_key("시그니처 블랙"), ("SIGNATURE_BLACK", "NONE"))

    def test_unknown_name_is_refused(self):
        with self.assertRaises(D.WorkbookError):
            D.card_key("듣보 카드")


class SkillLabelTest(unittest.TestCase):
    def test_six_grammars(self):
        cases = {
            "[S0] 강한 어깨 (O)": ("S", "강한 어깨", "O"),
            "[S2] WBC 에이스": ("S2", "WBC 에이스", None),
            "[S0]5툴 유격수": ("S", "5툴 유격수", None),
            "[S0] 라이징 스타 (선발 (O))": ("S", "라이징 스타", "선발 (O)"),
            "[S0] 파워 피쳐(구속>인내) O": ("S", "파워 피쳐", "구속>인내 O"),
            "[S0] 타자 케미스트리 ((팀버프 X))": ("S", "타자 케미스트리", "(팀버프 X)"),
        }
        for text, expected in cases.items():
            with self.subTest(text=text):
                self.assertEqual(D.split_skill_label(text), expected)

    def test_s0_means_s(self):
        """워크북은 S를 S0으로 적는다. 우리 사다리에는 S0이 없다."""
        self.assertEqual(D.split_skill_label("[S0] 리더십 (-)")[0], "S")

    def test_normalization(self):
        self.assertEqual(
            D.normalize_skill_name("WORLD BASEBALL CLASSIC 에이스"),
            D.normalize_skill_name("WBC 에이스"),
        )
        self.assertEqual(D.normalize_skill_name("배팅 머신"), D.normalize_skill_name("배팅머신"))
        self.assertEqual(D.normalize_skill_name("파워 피쳐"), D.normalize_skill_name("파워 피처"))

    def test_power_hitter_is_not_slugger(self):
        """워크북에 둘 다 있고 점수가 다르다. 별칭으로 묶으면 M_003에 두 점수가 붙는다."""
        self.assertNotIn("파워히터", D.SKILL_ALIASES)


class GateTest(unittest.TestCase):
    def test_single_gate(self):
        self.assertEqual(D.combine_gates("타순1_2"), "ORDER=1..2")

    def test_same_axis_intersects(self):
        """`타순3_4_5+타순4_5`는 ScoreCalculator가 확률을 곱하는 것과 같은 뜻이다."""
        self.assertEqual(D.combine_gates("타순3_4_5+타순4_5"), "ORDER=4..5")
        self.assertEqual(D.combine_gates("타순6_9+타순8_9"), "ORDER=8..9")

    def test_contradiction_is_refused(self):
        """`포지션_OF+포지션_SS`는 동시에 참일 수 없다. 추측하지 않고 손을 든다."""
        self.assertIsNone(D.combine_gates("포지션_OF+포지션_SS"))

    def test_non_gate_is_refused(self):
        """확률형 조건은 선수 상황으로 판정할 수 없다."""
        self.assertIsNone(D.combine_gates("선발1_2+6회까지"))
        self.assertIsNone(D.combine_gates("발사각조건+타순1_2"))


class SelectorTest(unittest.TestCase):
    def skill(self, base_stat=None):
        return D.SkillMeta(
            skill_id="X_001",
            pool="NORMAL",
            position="BATTER",
            name="테스트",
            base_stat=base_stat,
            conditions=set(),
            conditional_score={},
            max_level=9,
        )

    def test_stat_ranges(self):
        cases = {
            "변 150-199": "STAT(변화)=150..199",
            "정 200-233": "STAT(정확)=200..233",
            "주수 300-333": "STAT(주루+수비)=300..333",
            "변제 267-299": "STAT(변화+제구)=267..299",
            "지 100-119": "STAT(지구력)=100..119",
            "스덱코 500-599": "STAT(스페셜덱)=500..599",
        }
        for option, expected in cases.items():
            with self.subTest(option=option):
                self.assertEqual(D.build_selector(option, self.skill(), None), expected)

    def test_base_stat_threshold(self):
        """엘 그란데만 카드 고유 능력치를 본다. `+`가 뜻의 일부라 먼저 쪼개면 깨진다."""
        skill = self.skill()
        self.assertEqual(
            D.build_selector("주+수 165 이상", skill, None), "BASESTAT(주루+수비)>=165"
        )
        self.assertEqual(
            D.build_selector("주+수 155 미만", skill, None), "BASESTAT(주루+수비)<155"
        )

    def test_bare_range_uses_the_skills_base_stat(self):
        self.assertEqual(
            D.build_selector("300-349", self.skill("주루+수비"), None),
            "STAT(주루+수비)=300..349",
        )
        self.assertIsNotNone(D.build_selector("300-349", self.skill(), None))

    def test_typo_range_keeps_the_lower_bound(self):
        """워크북 오타 `주수 250-200`. 상한이 하한보다 작다."""
        self.assertEqual(
            D.build_selector("주수 250-200", self.skill(), None), "STAT(주루+수비)>=250"
        )

    def test_orders_and_roles(self):
        skill = self.skill()
        self.assertEqual(D.build_selector("2,3", skill, None), "ORDER=2..3")
        self.assertEqual(D.build_selector("1번타순O", skill, None), "ORDER=1")
        self.assertEqual(D.build_selector("1번타순X", skill, None), "!ORDER=1")
        self.assertEqual(D.build_selector("승리조", skill, None), "RELIEVER=WIN")
        self.assertEqual(D.build_selector("중계, 마무리", skill, None), "ROLE=RP|CP")
        self.assertEqual(D.build_selector("명전 선발", skill, None), "ROLE=SP&GRADE=HOF")
        self.assertEqual(D.build_selector("일반 선발", skill, None), "ROLE=SP&!GRADE=HOF")

    def test_combined_options(self):
        skill = self.skill()
        self.assertEqual(D.build_selector("스위치+3번", skill, None), "BAT=SWITCH&ORDER=3")
        self.assertEqual(D.build_selector("스위치X+3번X", skill, None), "!BAT=SWITCH&!ORDER=3")
        self.assertEqual(
            D.build_selector("좌타 O + 2번 O", skill, None), "BAT=LEFT&ORDER=2"
        )
        self.assertEqual(
            D.build_selector("O, 정 200-233", skill, "ORDER=1..2"),
            "ORDER=1..2&STAT(정확)=200..233",
        )

    def test_unknown_option_falls_back_to_manual(self):
        """모르면 추측하지 않는다. 사용자가 고르거나 엔진 값으로 떨어진다."""
        skill = self.skill()
        self.assertEqual(D.build_selector("14≥LA≥10", skill, None), D.SELECTOR_MANUAL)
        self.assertEqual(D.build_selector("O", skill, None), D.SELECTOR_MANUAL)

    def test_default_when_no_option(self):
        self.assertEqual(D.build_selector(None, self.skill(), None), D.SELECTOR_ALWAYS)
        self.assertEqual(D.build_selector("-", self.skill(), None), D.SELECTOR_ALWAYS)


class GrowthTableTest(unittest.TestCase):
    def test_ragged_ladder_stops_at_the_blank(self):
        """강화는 블랙만 10에서, 초월은 시그니처·프라임이 9에서 멈춘다. 채우면 안 된다."""
        sheet = D.Sheet(name="강화")
        sheet.values[("A", 3)] = "시그니처 블랙"
        sheet.values[("B", 3)] = "파워"
        for i, value in enumerate(["0", "0", "1", "1"]):
            sheet.values[(D.col_name(D.col_index("D") + i), 3)] = value
        rows = D.read_growth(sheet, 3, "D", "W")
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0].values, ["0", "0", "1", "1"])
        self.assertEqual(rows[0].card_grade, "SIGNATURE_BLACK")


class CommittedDataTest(unittest.TestCase):
    """커밋된 CSV 자체를 본다. 손으로 고쳤을 때 여기서 걸린다."""

    def test_validator_reports_no_errors(self):
        problems = [p for p in V.check(RESOURCES) if p.level == "ERROR"]
        self.assertEqual(problems, [], "\n".join(p.render() for p in problems))

    def test_reward_targets_cover_every_role(self):
        """자리 그룹 이름이 우리 로스터 규칙과 맞물려야 한다."""
        import csv

        with (RESOURCES / "deck_score_rewards.csv").open(encoding="utf-8") as fh:
            targets = {r["target"] for r in csv.DictReader(fh)}
        self.assertIn("BATTER_ALL", targets)
        self.assertIn("PITCHER_ALL", targets)
        for target in targets:
            self.assertTrue(
                target in V.REWARD_TARGET_GROUPS
                or set(target.split("|")) <= V.REWARD_TARGET_SLOTS,
                target,
            )

    def test_team_buff_values_match_the_effect_rows(self):
        """팀 버프 수치는 우리 효과행에서 온다. WBC_011 구위만 워크북에서 가져왔다."""
        import csv

        effects: set[tuple[str, str, str]] = set()
        with (RESOURCES / "score_effects.csv").open(encoding="utf-8") as fh:
            for row_ in csv.DictReader(fh):
                effects.add((row_["skill_id"], row_["stat"], row_["values"]))
        with (RESOURCES / "team_buff_skills.csv").open(encoding="utf-8") as fh:
            rows = list(csv.DictReader(fh))
        missing = [
            (r["skill_id"], r["stat"])
            for r in rows
            if (r["skill_id"], r["stat"], r["values"]) not in effects
        ]
        self.assertEqual(missing, [("WBC_011", "구위")], missing)


@unittest.skipUnless(
    os.environ.get("DECK_WORKBOOK"), "워크북이 없다. DECK_WORKBOOK=<경로>로 켠다"
)
class RealWorkbookTest(unittest.TestCase):
    """워크북이 손에 있을 때만 도는 전수 대조. CI에는 워크북이 없다."""

    def test_every_formula_matches_its_cached_value(self):
        book = D.Workbook(Path(os.environ["DECK_WORKBOOK"]))
        checked, problems = D.verify_rewards(book.sheet("라인업"))
        self.assertEqual(problems, [], "\n".join(problems[:5]))
        self.assertGreater(checked, 1400)

    def test_extract_matches_the_committed_csvs(self):
        book = D.Workbook(Path(os.environ["DECK_WORKBOOK"]))
        tables, _ = D.build(book, RESOURCES)
        for name, rows in tables.items():
            with self.subTest(name=name):
                self.assertEqual(
                    D.render(name, rows), (RESOURCES / name).read_text(encoding="utf-8")
                )


if __name__ == "__main__":
    unittest.main()
