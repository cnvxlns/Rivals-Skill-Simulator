"""Gemini updates: identity preservation, validation, failure isolation and deduplication."""
import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, MagicMock

import update_skills_gemini as updater
from test_check_notices import POST


TEXT = '새 스킬: 파워 능력치가 5 증가합니다'


def proposal(**changes):
    row = dict(skill_id='', card_type='NORMAL', tier='GOLD', position='BATTER',
               name='새 스킬', description='파워 능력치가 5 증가합니다', evidence=TEXT,
               effects=[dict(stat='파워', condition='ALWAYS', values='5', base_stat='')], placeholders=[])
    row.update(changes)
    return dict(skills=[row], review_reasons=[])


class UpdateTests(unittest.TestCase):
    def setUp(self):
        self.data = {key: [] for key in updater.FILES}
        self.state = {'posts': {}, 'skills': {}}

    def apply(self, result):
        updater.apply_result(self.data, result, TEXT, POST, self.state)

    def test_new_id_and_existing_replacement(self):
        self.apply(proposal())
        self.assertEqual(self.data['skills'][0]['skill_id'], 'G_001')
        self.apply(proposal(skill_id='G_001', effects=[dict(stat='파워', condition='ALWAYS', values='6', base_stat='')]))
        self.assertEqual(len(self.data['skills']), 1)
        self.assertEqual(self.data['effects'][0]['values'], '6')

    def test_roles_have_distinct_ids(self):
        self.apply(proposal())
        self.apply(proposal(position='PITCHER'))
        self.assertEqual([r['skill_id'] for r in self.data['skills']], ['G_001', 'G_002'])

    def test_rejects_wrong_ids_tiers_evidence_and_missing_ladders(self):
        for result in [proposal(skill_id='G_999'), proposal(tier='MOMENT'),
                       proposal(evidence='invented'), proposal(description='파워 x 증가'),
                       proposal(effects=[]), {'skills': [], 'review_reasons': ['unknown condition']}]:
            with self.subTest(result=result), self.assertRaises(ValueError):
                self.apply(result)

    def test_nonfinite_or_large_values_rejected(self):
        for value in ['nan', 'inf', '155', '1/2', '']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.apply(proposal(effects=[dict(stat='파워', condition='ALWAYS', values=value, base_stat='')]))

    def test_old_notice_cannot_roll_back_newer_skill(self):
        self.apply(proposal())
        self.state['skills']['G_001'] = '2099-01-01'
        before = copy.deepcopy(self.data)
        self.apply(proposal(skill_id='G_001'))
        self.assertEqual(self.data, before)

    def test_duplicate_effects_and_empty_condition_atoms_rejected(self):
        effect = dict(stat='파워', condition='ALWAYS', values='5', base_stat='')
        for effects in [[effect, effect], [{**effect, 'condition': 'ALWAYS+'}]]:
            with self.subTest(effects=effects), self.assertRaises(ValueError):
                self.apply(proposal(effects=effects))

    def test_unknown_condition_rejected_by_csv_validation(self):
        self.apply(proposal(effects=[dict(stat='파워', condition='UNKNOWN', values='5', base_stat='')]))
        with self.assertRaisesRegex(ValueError, 'BAD_CONDITION'):
            updater.validate(self.data, updater.validator.RESOURCES)

    def test_http_request_and_incomplete_response(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({
            'candidates': [{'finishReason': 'STOP', 'content': {'parts': [{'text': json.dumps(proposal())}]}}]
        }).encode()
        with patch.object(updater.urllib.request, 'urlopen', return_value=response) as call:
            self.assertEqual(updater.generate(POST, TEXT, self.data, 'secret', 'gemini-3.8-flash'), proposal())
            request = call.call_args.args[0]
            self.assertNotIn('secret', request.full_url)
            self.assertEqual(request.get_header('X-goog-api-key'), 'secret')
            self.assertIn('responseJsonSchema', json.loads(request.data)['generationConfig'])
            response.__enter__.return_value.read.return_value = b'{"candidates":[{"finishReason":"MAX_TOKENS"}]}'
            with self.assertRaises(ValueError):
                updater.generate(POST, TEXT, self.data, 'secret', 'gemini-3.8-flash')

    def test_run_dry_run_apply_skip_hash_and_failure_no_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for filename, _ in updater.FILES.values():
                (root / filename).write_bytes((updater.validator.RESOURCES / filename).read_bytes())
            (root / 'stat_weights.csv').write_bytes((updater.validator.RESOURCES / 'stat_weights.csv').read_bytes())
            state_path = root / 'state.json'
            html = '<table><tr><td>스킬명</td><td>설명</td></tr><tr><td>새 스킬</td><td>파워 5 증가</td></tr></table>'
            before = {p.name: p.read_bytes() for p in root.iterdir()}
            with patch.dict(os.environ, GEMINI_API_KEY='test'), \
                 patch.object(updater.notices, 'BOARDS', (9,)), \
                 patch.object(updater.notices, 'fetch_board', return_value=[POST]), \
                 patch.object(updater.notices, '_get', return_value=html), \
                 patch.object(updater.time, 'sleep'), \
                 patch.object(updater, 'generate', return_value=proposal(evidence='새 스킬')) as generate:
                updater.run(root, state_path)
                self.assertEqual({p.name: p.read_bytes() for p in root.iterdir()}, before)
                generate.return_value = proposal(evidence='not present')
                with self.assertRaises(ValueError):
                    updater.run(root, state_path, apply=True)
                self.assertEqual({p.name: p.read_bytes() for p in root.iterdir()}, before)
                generate.return_value = dict(skills=[], review_reasons=[])
                with self.assertRaisesRegex(ValueError, 'omitted'):
                    updater.run(root, state_path, apply=True)
                self.assertEqual({p.name: p.read_bytes() for p in root.iterdir()}, before)
                generate.return_value = proposal(evidence='새 스킬')
                updater.run(root, state_path, apply=True)
                self.assertTrue(state_path.exists())
                generate.reset_mock()
                updater.run(root, state_path, apply=True)
                generate.assert_not_called()


if __name__ == '__main__':
    unittest.main()
