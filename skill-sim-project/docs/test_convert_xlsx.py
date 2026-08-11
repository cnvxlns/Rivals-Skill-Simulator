from pathlib import Path
import unittest

from convert_xlsx import SkillRow, build_effect_rows, expand_value, read_skill_rows, _condition_for


class ConvertXlsxTest(unittest.TestCase):
    def test_expand_x_expressions_to_nine_levels(self):
        self.assertEqual(expand_value("x+1", {}), "2/3/4/5/6/7/8/9/10")
        self.assertEqual(expand_value("3*x", {}), "3/6/9/12/15/18/21/24/27")
        self.assertEqual(expand_value("{x+5}", {}), "6/7/8/9/10/11/12/13/14")

    def test_expand_row_variables_and_offsets(self):
        variables = {"y": "5/8/11", "z": "4/6/7"}

        self.assertEqual(expand_value("y", variables), "5/8/11")
        self.assertEqual(expand_value("{y+1}", variables), "6/9/12")
        self.assertEqual(expand_value("z", variables), "4/6/7")

    def test_builds_conditioned_effect_rows_for_standard_stats(self):
        row = SkillRow(
            skill_id="S_003",
            card_type="NORMAL",
            position="BATTER",
            name="타점기계",
            description="주자가 2루 이상일 때 파워, 정확 능력치가 x+1 증가합니다",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.skill_id, effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("S_003", "파워", "주자2루이상", "2/3/4/5/6/7/8/9/10"),
                ("S_003", "정확", "주자2루이상", "2/3/4/5/6/7/8/9/10"),
            ],
        )

    def test_builds_multiple_sentences_and_home_condition(self):
        row = SkillRow(
            skill_id="M_001",
            card_type="MOMENT",
            position="BATTER",
            name="프렌차이즈",
            description="파워, 정확, 선구, 인내 능력치가 5 증가합니다. 홈 경기인 경우 추가로 파워, 정확, 인내 능력치가 1 증가합니다",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertIn(("M_001", "파워", "ALWAYS", "5"), [(e.skill_id, e.stat, e.condition, e.values) for e in effects])
        self.assertIn(("M_001", "인내", "ALWAYS", "5"), [(e.skill_id, e.stat, e.condition, e.values) for e in effects])
        self.assertIn(("M_001", "파워", "홈", "1"), [(e.skill_id, e.stat, e.condition, e.values) for e in effects])
        self.assertIn(("M_001", "인내", "홈", "1"), [(e.skill_id, e.stat, e.condition, e.values) for e in effects])

    def test_ignores_non_stat_effects_with_review_note(self):
        row = SkillRow(
            skill_id="S_005",
            card_type="NORMAL",
            position="BATTER",
            name="선구안",
            description="볼일 때 체크스윙할 확률이 3*x% 증가합니다",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(effects, [])
        self.assertEqual(len(notes), 1)
        self.assertEqual(notes[0].reason, "비스탯효과")

    def test_decimal_percent_non_stat_effect_stays_one_review_note(self):
        row = SkillRow(
            skill_id="I_010",
            card_type="NORMAL",
            position="PITCHER",
            name="사고방지",
            description="볼이 한가운데로 몰리는 실투가 발생할 확률이 0.5*x% 감소합니다",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(effects, [])
        self.assertEqual(len(notes), 1)
        self.assertEqual(notes[0].text, "볼이 한가운데로 몰리는 실투가 발생할 확률이 0.5*x% 감소합니다")

    def test_ignores_duration_count_between_stat_effects(self):
        row = SkillRow(
            skill_id="M_029",
            card_type="MOMENT",
            position="PITCHER",
            name="스탠드아웃",
            description="구위, 변화, 제구 능력치가 7 증가하고 등판 후 9타자 동안 구위, 변화, 제구 능력치가 추가로 5 증가합니다.",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("구위", "ALWAYS", "7"),
                ("변화", "ALWAYS", "7"),
                ("제구", "ALWAYS", "7"),
                ("구위", "등판후9타자", "5"),
                ("변화", "등판후9타자", "5"),
                ("제구", "등판후9타자", "5"),
            ],
        )

    def test_conditions_standout_batter_second_plate_appearance_bonus(self):
        row = SkillRow(
            skill_id="M_032",
            card_type="MOMENT",
            position="BATTER",
            name="스탠드아웃",
            description="파워, 정확, 선구 능력치가 7 증가하고 두 번째 타석까지 파워, 정확, 선구 능력치가 추가로 5 증가합니다.",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("파워", "ALWAYS", "7"),
                ("정확", "ALWAYS", "7"),
                ("선구", "ALWAYS", "7"),
                ("파워", "두번째타석까지", "5"),
                ("정확", "두번째타석까지", "5"),
                ("선구", "두번째타석까지", "5"),
            ],
        )

    def test_conditions_power_pitcher_stat_comparison_bonus(self):
        row = SkillRow(
            skill_id="M_041",
            card_type="MOMENT",
            position="PITCHER",
            name="파워 피처",
            description="변화, 구위 능력치가 10 증가합니다. 구속 능력치가 상대 타자의 인내 능력치보다 높은 경우 상대 타자의 파워, 정확, 선구 능력치를 추가로 10 감소시킵니다.",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("구위", "ALWAYS", "10"),
                ("변화", "ALWAYS", "10"),
                ("파워", "인내<구속", "10"),
                ("정확", "인내<구속", "10"),
                ("선구", "인내<구속", "10"),
            ],
        )

    def test_conditions_playoff_hero_ovr_comparison_bonus(self):
        cases = [
            (
                SkillRow(
                    skill_id="M_004",
                    card_type="MOMENT",
                    position="BATTER",
                    name="플레이오프 히어로",
                    description="상대 투수의 OVR이 더 높을 경우 상대의 변화, 구위가 4 감소합니다.",
                    variables={},
                ),
                [("구위", "OVR열세", "4"), ("변화", "OVR열세", "4")],
            ),
            (
                SkillRow(
                    skill_id="M_020",
                    card_type="MOMENT",
                    position="PITCHER",
                    name="플레이오프 히어로",
                    description="상대 타자의 OVR이 더 높을 경우 상대의 파워, 정확이 4 감소합니다.",
                    variables={},
                ),
                [("파워", "OVR열세", "4"), ("정확", "OVR열세", "4")],
            ),
        ]

        for row, expected in cases:
            with self.subTest(row.skill_id):
                effects, notes = build_effect_rows(row)

                self.assertEqual(notes, [])
                self.assertEqual(
                    [(effect.stat, effect.condition, effect.values) for effect in effects],
                    expected,
                )

    def test_conditions_maestro_cumulative_out_count_bonus(self):
        row = SkillRow(
            skill_id="M_042",
            card_type="MOMENT",
            position="PITCHER",
            name="마에스트로",
            description=(
                "상대 타자의 모든 능력치를 11 감소시킵니다. "
                "등판 후 처리한 아웃 카운트 하나 당 변화, 구위가 1 증가합니다. "
                "변화, 구위 능력치는 최대 12까지 증가하며 강판 시까지 유지됩니다."
            ),
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("파워", "ALWAYS", "11"),
                ("정확", "ALWAYS", "11"),
                ("선구", "ALWAYS", "11"),
                ("인내", "ALWAYS", "11"),
                ("주루", "ALWAYS", "11"),
                ("수비", "ALWAYS", "11"),
                ("구위", "마에스트로누적", "12"),
                ("변화", "마에스트로누적", "12"),
            ],
        )

    def test_builds_pitcher_inning_tier_rows(self):
        row = SkillRow(
            skill_id="G_075",
            card_type="NORMAL",
            position="PITCHER",
            name="오버페이스",
            description="상대 타자의 파워 능력치를 x 감소시킵니다. 현재 이닝에 따라 구위, 변화 능력치가 증가합니다. 1~3회 7 증가, 4~6회 5 증가, 7~9회 2 증가",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
                ("구위", "1_3회", "7"),
                ("변화", "1_3회", "7"),
                ("구위", "4_6회", "5"),
                ("변화", "4_6회", "5"),
                ("구위", "7_9회", "2"),
                ("변화", "7_9회", "2"),
            ],
        )

    def test_builds_batter_plate_decay_rows(self):
        row = SkillRow(
            skill_id="G_034",
            card_type="NORMAL",
            position="BATTER",
            name="오버페이스",
            description="상대 투수의 구위 능력치를 x 감소시킵니다. 첫 타석에 파워, 정확 능력치가 8 증가하며, 이후 타석마다 2씩 감소합니다.(4~7번째 타석은 파워, 정확, 능력치가 2 증가합니다)",
            variables={},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("구위", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
                ("파워", "타석1", "8"),
                ("정확", "타석1", "8"),
                ("파워", "타석2", "6"),
                ("정확", "타석2", "6"),
                ("파워", "타석3", "4"),
                ("정확", "타석3", "4"),
                ("파워", "타석4_7", "2"),
                ("정확", "타석4_7", "2"),
            ],
        )

    def test_builds_batter_plate_accumulation_rows(self):
        row = SkillRow(
            skill_id="G_033",
            card_type="NORMAL",
            position="BATTER",
            name="빌드업",
            description="상대 투수의 변화 능력치를 y 감소시킵니다. 타석에 들어설 때마다 파워 정확 선구 능력치가 z 증가합니다.(최대 10까지만, 7타석까지만 적용)",
            variables={"y": "1/1/2/2/3/4/5/6/7", "z": "2/2/2/2/2/3/3/3/3", "a": "", "b": "", "c": ""},
        )

        effects, notes = build_effect_rows(row)

        self.assertEqual(notes, [])
        self.assertEqual(
            [(effect.stat, effect.condition, effect.values) for effect in effects],
            [
                ("변화", "ALWAYS", "1/1/2/2/3/4/5/6/7"),
                ("파워", "타석1", "2/2/2/2/2/3/3/3/3"),
                ("정확", "타석1", "2/2/2/2/2/3/3/3/3"),
                ("선구", "타석1", "2/2/2/2/2/3/3/3/3"),
                ("파워", "타석2", "4/4/4/4/4/6/6/6/6"),
                ("정확", "타석2", "4/4/4/4/4/6/6/6/6"),
                ("선구", "타석2", "4/4/4/4/4/6/6/6/6"),
                ("파워", "타석3", "6/6/6/6/6/9/9/9/9"),
                ("정확", "타석3", "6/6/6/6/6/9/9/9/9"),
                ("선구", "타석3", "6/6/6/6/6/9/9/9/9"),
                ("파워", "타석4", "8/8/8/8/8/10/10/10/10"),
                ("정확", "타석4", "8/8/8/8/8/10/10/10/10"),
                ("선구", "타석4", "8/8/8/8/8/10/10/10/10"),
                ("파워", "타석5_7", "10"),
                ("정확", "타석5_7", "10"),
                ("선구", "타석5_7", "10"),
            ],
        )

    def test_adds_inning_until_condition_to_limited_effects(self):
        cases = [
            (
                SkillRow(
                    skill_id="G_055",
                    card_type="NORMAL",
                    position="RP",
                    name="퀄리티 스타트",
                    description="구속, 제구, 수비 능력치가 x+1 증가합니다. 선발로 등판 시, 6회까지 상대 타자 파워, 정확, 선구가 x+1 감소합니다.",
                    variables={},
                ),
                [("파워", "포지션_SP+6회까지"), ("정확", "포지션_SP+6회까지"), ("선구", "포지션_SP+6회까지")],
            ),
            (
                SkillRow(
                    skill_id="M_019",
                    card_type="MOMENT",
                    position="PITCHER",
                    name="프론트라인",
                    description="구위, 변화, 수비 능력치가 6 증가합니다. 선발로 등판 시, 7회까지 상대 타자 파워, 정확, 선구가 3 감소합니다.",
                    variables={},
                ),
                [("파워", "포지션_SP+7회까지"), ("정확", "포지션_SP+7회까지"), ("선구", "포지션_SP+7회까지")],
            ),
            (
                SkillRow(
                    skill_id="BLACK_017",
                    card_type="BLACK",
                    position="SP",
                    name="무실점 스타트",
                    description="구위, 변화, 구속 능력치가 y 증가합니다. 1, 2선발로 등판 시 6회까지 상대 타자의 정확, 선구 능력치를 z 감소시킵니다.",
                    variables={"y": "4/6/9", "z": "4/7/10", "a": "", "b": "", "c": ""},
                ),
                [("정확", "선발1_2+6회까지"), ("선구", "선발1_2+6회까지")],
            ),
            (
                SkillRow(
                    skill_id="HOF_038",
                    card_type="HOF",
                    position="SP",
                    name="300승 투수",
                    description="구위, 변화 능력치가 y 증가합니다. 1선발 등판 시 7회까지 상대 타자의 파워, 정확 능력치를 z 감소시킵니다. 비기거나 리드 상황일 경우 상대 타자의 파워, 정확 능력치를 추가로 y 감소시킵니다.",
                    variables={"y": "1/1/2/2/3/4", "z": "1/3/5/7/9/11", "a": "", "b": "", "c": ""},
                ),
                [("파워", "선발1+7회까지"), ("정확", "선발1+7회까지")],
            ),
        ]

        for row, expected_pairs in cases:
            with self.subTest(row.skill_id):
                effects, notes = build_effect_rows(row)

                self.assertEqual(notes, [])
                actual = [(effect.stat, effect.condition) for effect in effects]
                for expected in expected_pairs:
                    self.assertIn(expected, actual)


    def test_proportional_effect_stat_based(self):
        row = SkillRow(
            skill_id="G_023",
            card_type="NORMAL",
            position="BATTER",
            name="신속한 타격",
            description="주루, 수비 능력치가 y증가합니다. 주루+수비 능력치의 z%만큼 파워, 정확이 증가합니다",
            variables={"y": "2/3/4/5/6/7/9/11/13", "z": "1/1/2/2/2/3/3/4/4"},
        )
        effects, notes = build_effect_rows(row)
        self.assertEqual(notes, [])
        self.assertEqual(
            [(e.stat, e.condition, e.values, e.base_stat) for e in effects],
            [
                ("주루", "ALWAYS", "2/3/4/5/6/7/9/11/13", ""),
                ("수비", "ALWAYS", "2/3/4/5/6/7/9/11/13", ""),
                ("파워", "ALWAYS", "0.01/0.01/0.02/0.02/0.02/0.03/0.03/0.04/0.04", "주루+수비"),
                ("정확", "ALWAYS", "0.01/0.01/0.02/0.02/0.02/0.03/0.03/0.04/0.04", "주루+수비"),
            ]
        )

    def test_proportional_effect_deck_score_based(self):
        row = SkillRow(
            skill_id="G_038",
            card_type="NORMAL",
            position="BATTER",
            name="결속력",
            description="상대 투수의 변화 능력치를 x+1 감소시킵니다. 팀의 스페셜 덱 스코어의 1%만큼 파워 정확 선구 능력치가 증가합니다",
            variables={},
        )
        effects, notes = build_effect_rows(row)
        self.assertEqual(notes, [])
        self.assertEqual(
            [(e.stat, e.condition, e.values, e.base_stat) for e in effects],
            [
                ("변화", "ALWAYS", "2/3/4/5/6/7/8/9/10", ""),
                ("파워", "ALWAYS", "0.01", "스페셜덱"),
                ("정확", "ALWAYS", "0.01", "스페셜덱"),
                ("선구", "ALWAYS", "0.01", "스페셜덱"),
            ]
        )

    def test_proportional_effect_mixed_clause(self):
        row = SkillRow(
            skill_id="HOF_034",
            card_type="HOF",
            position="PITCHER",
            name="디셉션 마스터",
            description="변화 능력치의 y%만큼 구위 능력치가 증가하고 상대 타자의 파워, 정확 능력치를 4 감소시킵니다. 직전 타자를 삼진 아웃 시켰을 시 상대 타자의 파워, 정확 능력치를 추가로 z 감소시킵니다.",
            variables={"y": "1/1/2/2/2/3", "z": "4/5/6/7/8/9"},
        )
        effects, notes = build_effect_rows(row)
        self.assertEqual(notes, [])
        self.assertEqual(
            [(e.stat, e.condition, e.values, e.base_stat) for e in effects],
            [
                ("구위", "ALWAYS", "0.01/0.01/0.02/0.02/0.02/0.03", "변화"),
                ("파워", "ALWAYS", "4", ""),
                ("정확", "ALWAYS", "4", ""),
                ("파워", "ALWAYS", "4/5/6/7/8/9", ""),
                ("정확", "ALWAYS", "4/5/6/7/8/9", ""),
            ]
        )

    def test_pitcher_slot_conditions(self):
        self.assertEqual(_condition_for("3,4,5선발 또는 3,4,5중계 배치 시"), "선발3_4_5+중계3_4_5")
        self.assertEqual(_condition_for("1,2선발 배치 시"), "선발1_2")
        self.assertEqual(_condition_for("1,2선발로 등판 시"), "선발1_2")
        self.assertEqual(_condition_for("1,2선발 등판 시"), "선발1_2")
        self.assertEqual(_condition_for("4,5선발 배치 시"), "선발4_5")
        self.assertEqual(_condition_for("3,4선발 배치 시"), "선발3_4")
        self.assertEqual(_condition_for("1선발 배치 시"), "선발1")
        # 번호 없는 선발/중계는 포지션_SP/포지션_RP_CP
        self.assertEqual(_condition_for("선발 등판 시"), "포지션_SP")
        self.assertEqual(_condition_for("중계 등판 시"), "포지션_RP_CP")

    def test_r19_conditioned_skill_mappings_from_xlsx(self):
        rows = {row.skill_id: row for row in read_skill_rows(Path(__file__).resolve().parent / "rivals_skills.xlsx")}

        expected_rows = {
            "G_026": [
                ("파워", "OVR열세", "1/2/3/4/5/6/7/8/9"),
                ("정확", "OVR열세", "1/2/3/4/5/6/7/8/9"),
                ("선구", "OVR열세", "1/2/3/4/5/6/7/8/9"),
                ("인내", "OVR열세", "1/2/3/4/5/6/7/8/9"),
            ],
            "G_060": [
                ("구위", "OVR열세", "1/2/3/4/5/6/7/8/9"),
                ("변화", "OVR열세", "1/2/3/4/5/6/7/8/9"),
                ("제구", "OVR열세", "1/2/3/4/5/6/7/8/9"),
                ("구속", "OVR열세", "1/2/3/4/5/6/7/8/9"),
            ],
            "M_030": [
                ("구위", "OVR열세", "5"),
                ("변화", "OVR열세", "5"),
                ("제구", "OVR열세", "5"),
            ],
            "M_033": [
                ("파워", "OVR열세", "5"),
                ("정확", "OVR열세", "5"),
                ("선구", "OVR열세", "5"),
            ],
            "G_036": [
                ("구위", "덱스코어열세", "2/3/3/4/4/5/6/7/8"),
                ("변화", "덱스코어열세", "2/3/3/4/4/5/6/7/8"),
            ],
            "G_070": [
                ("파워", "덱스코어열세", "2/3/3/4/4/5/6/7/8"),
                ("정확", "덱스코어열세", "2/3/3/4/4/5/6/7/8"),
            ],
            "G_037": [
                ("파워", "덱스코어열세", "1/2/3/4/5/6/7/8/9"),
                ("정확", "덱스코어열세", "1/2/3/4/5/6/7/8/9"),
                ("선구", "덱스코어열세", "1/2/3/4/5/6/7/8/9"),
            ],
            "G_071": [
                ("구위", "덱스코어열세", "1/2/3/4/5/6/7/8/9"),
                ("변화", "덱스코어열세", "1/2/3/4/5/6/7/8/9"),
                ("제구", "덱스코어열세", "1/2/3/4/5/6/7/8/9"),
            ],
            "G_028": [
                ("정확", "구위>파워", "4/5/6/7/8/9/10/11/12"),
                ("파워", "구위>파워", "-4"),
            ],
            "HOF_018": [
                ("구위", "선구>제구", "1/2/3/4/5/6"),
                ("변화", "선구>제구", "1/2/3/4/5/6"),
            ],
            "HOF_036": [
                ("파워", "제구>선구", "1/2/3/4/5/6"),
                ("정확", "제구>선구", "1/2/3/4/5/6"),
            ],
            "G_015": [
                ("파워", "상대등급우세", "1"),
                ("정확", "상대등급우세", "1"),
                ("선구", "상대등급우세", "1"),
                ("인내", "상대등급우세", "1"),
                ("주루", "상대등급우세", "1"),
                ("수비", "상대등급우세", "1"),
            ],
            "G_053": [
                ("구속", "상대등급우세", "1"),
                ("구위", "상대등급우세", "1"),
                ("변화", "상대등급우세", "1"),
                ("제구", "상대등급우세", "1"),
                ("지구력", "상대등급우세", "1"),
                ("수비", "상대등급우세", "1"),
            ],
            "BLACK_009": [
                ("파워", "홈런3이상", "2/4/6"),
                ("정확", "홈런3이상", "2/4/6"),
                ("선구", "홈런3이상", "2/4/6"),
                ("인내", "홈런3이상", "2/4/6"),
            ],
        }

        always_rows = {
            "G_026": [
                ("파워", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
                ("정확", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
                ("선구", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
                ("인내", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
            ],
            "G_060": [
                ("구위", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
                ("변화", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
                ("제구", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
                ("구속", "ALWAYS", "1/1/1/1/1/2/2/3/3"),
            ],
            "G_036": [
                ("정확", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
                ("선구", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
            ],
            "G_070": [
                ("변화", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
                ("제구", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
            ],
            "G_037": [
                ("구위", "ALWAYS", "1/1/2/2/3/3/4/4/5"),
                ("변화", "ALWAYS", "1/1/2/2/3/3/4/4/5"),
                ("제구", "ALWAYS", "1/1/2/2/3/3/4/4/5"),
            ],
            "G_071": [
                ("파워", "ALWAYS", "1/1/2/2/3/3/4/4/5"),
                ("정확", "ALWAYS", "1/1/2/2/3/3/4/4/5"),
                ("선구", "ALWAYS", "1/1/2/2/3/3/4/4/5"),
            ],
            "BLACK_009": [
                ("구위", "ALWAYS", "4/7/9"),
                ("변화", "ALWAYS", "4/7/9"),
                ("제구", "ALWAYS", "4/7/9"),
                ("구속", "ALWAYS", "4/7/9"),
            ],
        }

        for skill_id, expected in expected_rows.items():
            if skill_id not in rows:
                continue
            with self.subTest(skill_id=skill_id):
                effects, notes = build_effect_rows(rows[skill_id])
                self.assertEqual(notes, [])
                actual = [(effect.stat, effect.condition, effect.values) for effect in effects]
                for row in expected:
                    self.assertIn(row, actual)
                for row in always_rows.get(skill_id, []):
                    self.assertIn(row, actual)



if __name__ == "__main__":
    unittest.main()
