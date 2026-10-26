"""Gemini updates: identity preservation, validation, failure isolation and deduplication."""
import copy
import contextlib
import io
import json
import os
from pathlib import Path
import ssl
import sys
import tempfile
import unittest
import urllib.error
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


def response(value):
    result = MagicMock()
    result.__enter__.return_value.read.return_value = json.dumps(value).encode()
    return result


def generated(value):
    return response({'candidates': [{'finishReason': 'STOP', 'content': {'parts': [{'text': json.dumps(value)}]}}]})


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

    def test_context_keeps_aliases_all_roles_and_only_their_effects(self):
        self.data = {
            'skills': [dict(skill_id='G_001', name='파워 피처', position='BATTER', card_type='NORMAL'),
                       dict(skill_id='HOF_001', name='파워 피처', position='PITCHER', card_type='HOF'),
                       dict(skill_id='G_002', name='다른 스킬', position='BATTER', card_type='NORMAL')],
            'effects': [dict(skill_id=sid) for sid in ('G_001', 'HOF_001', 'G_002')],
            'placeholders': [dict(skill_id=sid) for sid in ('G_001', 'HOF_001', 'G_002')],
        }
        before = copy.deepcopy(self.data)
        payload = updater.build_payload(POST, '파워 피쳐 변경', self.data, updater.DEFAULT_MODEL)
        context = json.loads(payload['contents'][0]['parts'][0]['text'])
        for rows in context['existing'].values():
            self.assertEqual({row['skill_id'] for row in rows}, {'G_001', 'HOF_001'})
        self.assertEqual(self.data, before)
        self.assertIn('파워 피쳐'.encode(), updater.encoded(payload))
        self.assertEqual(payload['generationConfig']['thinkingConfig'], {'thinkingLevel': 'low'})
        self.assertNotIn('temperature', payload['generationConfig'])
        self.assertNotIn('thinkingConfig', updater.generation_config('gemini-2.5-flash', updater.SCHEMA))

    def test_timeout_during_connect_or_body_read_is_retried(self):
        for failure in (TimeoutError('secret timeout'), response({})):
            if not isinstance(failure, Exception):
                failure.__enter__.return_value.read.side_effect = TimeoutError('secret read timeout')
            with self.subTest(failure=type(failure).__name__), \
                 patch.object(updater.urllib.request, 'urlopen', side_effect=[failure, generated(proposal())]) as call, \
                 patch.object(updater.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()) as logs:
                self.assertEqual(updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL,
                                                 timeout=7, attempts=2), proposal())
                self.assertEqual(call.call_count, 2)
                self.assertEqual(call.call_args.kwargs['timeout'], 7)
                sleep.assert_called_once()
                self.assertNotIn('secret', logs.getvalue())

    def test_transient_http_and_network_errors_are_retried(self):
        errors = [urllib.error.URLError('secret network details')]
        errors.extend(urllib.error.HTTPError('secret-url', code, 'secret server error', {}, None)
                      for code in (408, 429, 500, 502, 503, 504))
        for error in errors:
            with self.subTest(error=type(error).__name__, code=getattr(error, 'code', None)), \
                 patch.object(updater.urllib.request, 'urlopen', side_effect=[error, generated(proposal())]) as call, \
                 patch.object(updater.time, 'sleep'), contextlib.redirect_stdout(io.StringIO()) as logs:
                self.assertEqual(updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL), proposal())
                self.assertEqual(call.call_count, 2)
                self.assertNotIn('secret', logs.getvalue())

    def test_terminal_errors_are_not_retried_or_leaked(self):
        errors = [urllib.error.HTTPError('secret-url', code, 'secret server error', {}, None)
                  for code in (400, 401, 403, 404)]
        errors.append(urllib.error.URLError(ssl.SSLCertVerificationError('secret certificate details')))
        errors.append(ssl.SSLCertVerificationError('secret certificate details'))
        for error in errors:
            with self.subTest(error=type(error).__name__, code=getattr(error, 'code', None)), \
                 patch.object(updater.urllib.request, 'urlopen', side_effect=error) as call, \
                 patch.object(updater.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()) as logs:
                with self.assertRaises(RuntimeError) as caught:
                    updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL)
                self.assertEqual(call.call_count, 1)
                sleep.assert_not_called()
                self.assertIn(updater.DEFAULT_MODEL, str(caught.exception))
                self.assertNotIn('secret', str(caught.exception) + logs.getvalue())

    def test_exhausted_timeouts_are_bounded_and_identify_phase(self):
        with patch.object(updater.urllib.request, 'urlopen', side_effect=TimeoutError('secret')) as call, \
             patch.object(updater.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()) as logs:
            with self.assertRaisesRegex(RuntimeError, 'Gemini generate model=gemini-3.8-flash.*attempt 3/3'):
                updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL)
            self.assertEqual(call.call_count, 3)
            self.assertEqual(sleep.call_count, 2)
            self.assertNotIn('secret', logs.getvalue())

    def test_retry_after_is_honored_but_bounded_and_never_logged(self):
        error = urllib.error.HTTPError('secret-url', 429, 'secret', {'Retry-After': '120'}, None)
        with patch.object(updater.urllib.request, 'urlopen', side_effect=[error, generated(proposal())]), \
             patch.object(updater.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()) as logs:
            updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL)
            sleep.assert_called_once_with(60)
            self.assertNotIn('secret', logs.getvalue())
        with patch.object(updater.random, 'uniform', return_value=0), patch.object(updater.time, 'time', return_value=0):
            self.assertEqual(updater.retry_delay(1, 'Thu, 01 Jan 1970 00:00:30 GMT'), 30)
            for bad in ('secret malformed', 'inf', 'nan', '-1'):
                self.assertEqual(updater.retry_delay(1, bad), 2)

    def test_invalid_limits_fail_before_network(self):
        with patch.object(updater.urllib.request, 'urlopen') as call:
            for timeout, attempts in [(0, 3), (601, 3), (float('nan'), 3), (float('inf'), 3),
                                      (1, 0), (1, 6), (1, True), (True, 3)]:
                with self.subTest(timeout=timeout, attempts=attempts), self.assertRaises(ValueError):
                    updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL,
                                     timeout=timeout, attempts=attempts)
            call.assert_not_called()

    def test_malformed_api_or_candidate_json_fails_without_retry(self):
        invalid = [[], {'candidates': None}, {'candidates': {'0': {}}},
                   {'candidates': [None]}, {'candidates': [{'finishReason': 'STOP', 'content': None}]},
                   {'candidates': [{'finishReason': 'STOP', 'content': {'parts': None}}]},
                   {'candidates': [{'finishReason': 'STOP', 'content': {'parts': [None]}}]},
                   {'candidates': [{'finishReason': 'STOP', 'content': {'parts': [{'text': 1}]}}]}]
        for item in invalid:
            with self.subTest(response=item), patch.object(updater.urllib.request, 'urlopen', return_value=response(item)) as call:
                with self.assertRaises(ValueError):
                    updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL)
                self.assertEqual(call.call_count, 1)
        bad = response({})
        bad.__enter__.return_value.read.return_value = b'not JSON secret'
        with patch.object(updater.urllib.request, 'urlopen', return_value=bad) as call:
            with self.assertRaisesRegex(ValueError, 'invalid JSON response') as caught:
                updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL)
            self.assertNotIn('secret', str(caught.exception))
            self.assertEqual(call.call_count, 1)

    def test_check_api_verifies_model_and_real_structured_probe(self):
        with patch.object(updater.urllib.request, 'urlopen', side_effect=[
            response({'supportedGenerationMethods': ['generateContent']}), generated({'ok': True})
        ]) as call:
            updater.check_api('secret', updater.DEFAULT_MODEL, timeout=11, attempts=2)
            self.assertEqual(call.call_count, 2)
            metadata_request = call.call_args_list[0].args[0]
            self.assertIsNone(metadata_request.data)
            self.assertEqual(metadata_request.full_url, updater.API + updater.DEFAULT_MODEL)
            probe = json.loads(call.call_args_list[1].args[0].data)
            self.assertEqual(probe['generationConfig']['responseJsonSchema']['properties'], {'ok': {'type': 'boolean'}})

    def test_check_api_cli_does_not_touch_resources_state_or_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.dict(os.environ, GEMINI_API_KEY='secret'), \
                 patch.object(sys, 'argv', ['update', '--check-api', '--apply', '--resources', str(root),
                                           '--state', str(root / 'state.json'), '--cache', str(root / 'cache')]), \
                 patch.object(updater, 'check_api') as check, patch.object(updater, 'run') as run:
                self.assertEqual(updater.main(), 0)
                check.assert_called_once()
                run.assert_not_called()
                self.assertEqual(list(root.iterdir()), [])

    def test_check_api_rejects_unsupported_model_or_wrong_probe(self):
        with patch.object(updater.urllib.request, 'urlopen', return_value=response({'supportedGenerationMethods': []})) as call:
            with self.assertRaisesRegex(ValueError, 'does not support generateContent'):
                updater.check_api('secret', updater.DEFAULT_MODEL)
            self.assertEqual(call.call_count, 1)
        with patch.object(updater.urllib.request, 'urlopen', side_effect=[
            response({'supportedGenerationMethods': ['generateContent']}), generated({'ok': 1})
        ]):
            with self.assertRaisesRegex(ValueError, 'structured-output probe failed'):
                updater.check_api('secret', updater.DEFAULT_MODEL)

    def test_cache_keys_follow_payload_model_and_relevant_data(self):
        path = updater.cache_file(Path('cache'), POST, TEXT, self.data, updater.DEFAULT_MODEL)
        self.assertNotEqual(path, updater.cache_file(Path('cache'), POST, TEXT + ' changed', self.data, updater.DEFAULT_MODEL))
        self.assertNotEqual(path, updater.cache_file(Path('cache'), POST, TEXT, self.data, 'gemini-3.6-flash'))
        self.apply(proposal())
        self.assertNotEqual(path, updater.cache_file(Path('cache'), POST, TEXT, self.data, updater.DEFAULT_MODEL))
        changed = updater.cache_file(Path('cache'), POST, TEXT, self.data, updater.DEFAULT_MODEL)
        self.data['effects'][0]['values'] = '6'
        self.assertNotEqual(changed, updater.cache_file(Path('cache'), POST, TEXT, self.data, updater.DEFAULT_MODEL))

    def test_cache_resumes_after_later_failure_without_publishing_partial_data(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            resources, cache = root / 'resources', root / 'cache'
            resources.mkdir()
            for filename in [item[0] for item in updater.FILES.values()] + ['stat_weights.csv']:
                (resources / filename).write_bytes((updater.validator.RESOURCES / filename).read_bytes())
            state_path = root / 'state.json'
            before = {path.name: path.read_bytes() for path in resources.iterdir()}
            second = {**POST, 'date': '2026-10-22 10:00:00', 'url': POST['url'] + '1'}
            html = '<table><tr><td>스킬명</td><td>설명</td></tr><tr><td>{}</td><td>파워 5 증가</td></tr></table>'
            with patch.dict(os.environ, GEMINI_API_KEY='test'), \
                 patch.object(updater.notices, 'BOARDS', (9,)), \
                 patch.object(updater.notices, 'fetch_board', return_value=[POST, second]), \
                 patch.object(updater.notices, '_get', side_effect=lambda url: html.format('새 스킬' if url == POST['url'] else '또 스킬')), \
                 patch.object(updater.time, 'sleep'), \
                 patch.object(updater, 'generate', side_effect=[proposal(evidence='새 스킬'), RuntimeError('later timeout')]) as generate:
                with self.assertRaisesRegex(RuntimeError, 'later timeout'):
                    updater.run(resources, state_path, apply=True, cache=cache)
                self.assertEqual(generate.call_count, 2)
                self.assertEqual({path.name: path.read_bytes() for path in resources.iterdir()}, before)
                self.assertFalse(state_path.exists())
                entries = list(cache.iterdir())
                self.assertEqual(len(entries), 1)
                saved = entries[0].read_bytes()

                # A cache hit is still subject to the exact same manual-review checks.
                entries[0].write_text(json.dumps({'skills': [], 'review_reasons': ['needs review']}))
                generate.reset_mock()
                with self.assertRaisesRegex(ValueError, 'Manual review required'):
                    updater.run(resources, state_path, apply=True, cache=cache)
                generate.assert_not_called()
                self.assertFalse(state_path.exists())
                self.assertEqual({path.name: path.read_bytes() for path in resources.iterdir()}, before)

                entries[0].write_bytes(saved)
                generate.side_effect = None
                generate.return_value = proposal(name='또 스킬', evidence='또 스킬')
                updater.run(resources, state_path, apply=True, cache=cache)
                generate.assert_called_once()
                self.assertEqual(generate.call_args.args[0], second)
                self.assertTrue(state_path.exists())
                self.assertEqual(len(list(cache.glob('*.json'))), 2)
                self.assertEqual(list(cache.glob('.response-*')), [])

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
