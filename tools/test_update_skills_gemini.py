"""Gemini updates: identity preservation, validation, failure isolation and deduplication."""
import copy
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import re
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

    def test_notice_body_hash_and_cache_ignore_site_views_and_comments(self):
        article = ('<div class="post_cont"><p>신규 스킬 안내</p><div><div>타자<br>변경</div></div>'
                   '<table><tr><td>스킬명</td><td>설명</td></tr>'
                   '<tr><td>새 스킬</td><td>파워 5 증가</td></tr></table></div>')
        def page(views, comments):
            return (f'<header>사이트 메뉴</header><article><div class="title_wrap">'
                    f'<span class="i_hit">{views}</span></div>{article}'
                    f'<div class="comments">{comments}</div></article>')
        first = updater.notice_text(page('2,716', '댓글 하나'))
        second = updater.notice_text(page('2,717', '댓글 변경'))
        self.assertEqual(first, second)
        self.assertEqual(hashlib.sha256(first.encode()).hexdigest(), hashlib.sha256(second.encode()).hexdigest())
        key = updater.cache_file('cache', POST, first, self.data, updater.DEFAULT_MODEL)
        self.assertEqual(key, updater.cache_file('cache', POST, second, self.data, updater.DEFAULT_MODEL))
        for old, new in [('신규 스킬 안내', '기존 스킬 변경 안내'), ('파워 5 증가', '파워 6 증가')]:
            changed = updater.notice_text(page('2,716', '댓글 하나').replace(old, new))
            self.assertNotEqual(first, changed)
            self.assertNotEqual(key, updater.cache_file('cache', POST, changed, self.data, updater.DEFAULT_MODEL))
        for excluded in ('사이트 메뉴', '2,716', '댓글 하나'):
            self.assertNotIn(excluded, first)
        self.assertIn('새 스킬', first)
        self.assertIn('파워 5 증가', first)

    def test_notice_body_preserves_nested_divs_and_excludes_scripts_styles(self):
        markup = ('<div class="other post_cont extra"><p>본문 시작</p><div><div>중첩<br>다음 줄'
                  '<img src="image.png"><br/></div><script>hidden script</script>'
                  '<style>.hidden {}</style><p>중첩 끝</p></div><p>본문 끝</p></div>'
                  '<div>외부 텍스트</div>')
        self.assertEqual(updater.notice_text(markup), '본문 시작\n중첩\n다음 줄\n중첩 끝\n본문 끝')
        self.assertEqual(updater.notice_text('<p>일반 본문<br>다음 줄</p><script>hidden</script>'), '일반 본문\n다음 줄')

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

    def test_evidence_ignores_only_unicode_whitespace(self):
        cases = [(TEXT, '파워\n능력치가\t5 증가합니다'),
                 (TEXT.replace(' ', '\xa0'), '파워 능력치가 5\u2003증가합니다'),
                 (updater.notice_text('<table><tr><td>새 스킬</td><td>파워 5 증가</td></tr></table>'),
                  '새\n스킬\t|\xa0파워\u20035 증가')]
        for text, evidence in cases:
            with self.subTest(evidence=evidence):
                data = {key: [] for key in updater.FILES}
                state = {'posts': {}, 'skills': {}}
                updater.apply_result(data, proposal(evidence=evidence), text, POST, state)
                self.assertEqual(len(data['skills']), 1)

    def test_evidence_still_rejects_changed_facts_punctuation_and_empty_text(self):
        for evidence in ['파워 능력치가 6 증가합니다', '파워 능력치가 5 감소합니다',
                         '파워 능력치가 5 증가합니다.', '없는 스킬 설명', '\xa0\n\t\u2003']:
            with self.subTest(evidence=evidence), self.assertRaises(ValueError):
                self.apply(proposal(evidence=evidence))

    def test_evidence_failure_diagnostic_is_bounded_and_identifies_skill(self):
        evidence = 'missing\n' + '가' * 500
        with self.assertRaisesRegex(ValueError, 'Evidence is not in notice') as caught:
            self.apply(proposal(evidence=evidence))
        message = str(caught.exception)
        for part in ("name='새 스킬'", "card='NORMAL'", "position='BATTER'", repr(evidence[:160])):
            self.assertIn(part, message)
        self.assertNotIn('\n', message)
        self.assertLess(len(message), 500)

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
            with self.assertRaisesRegex(RuntimeError, rf'Gemini generate model={re.escape(updater.DEFAULT_MODEL)}.*attempt 3/3'):
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

    def test_overload_waits_longer_and_transport_retries_keep_short_backoff(self):
        for status, waits in ((429, [15, 30]), (503, [15, 30]), (500, [2, 4]), (None, [2, 4])):
            failures = [urllib.error.HTTPError('secret-url', status, 'secret', {}, None)
                        if status is not None else TimeoutError('secret') for _ in range(2)]
            with self.subTest(status=status), \
                 patch.object(updater.urllib.request, 'urlopen', side_effect=[*failures, generated(proposal())]), \
                 patch.object(updater.random, 'uniform', return_value=0), \
                 patch.object(updater.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL), proposal())
                self.assertEqual([call.args[0] for call in sleep.call_args_list], waits)
        with patch.object(updater.random, 'uniform', return_value=0.5):
            self.assertEqual(updater.retry_delay(3, status=503), 60)
            self.assertEqual(updater.retry_delay(1, '45', status=429), 45)
            self.assertEqual(updater.retry_delay(1, '2', status=503), 15.5)

    def test_overload_status_is_reset_before_a_later_transport_failure(self):
        overload = urllib.error.HTTPError('secret-url', 503, 'secret', {}, None)
        with patch.object(updater.urllib.request, 'urlopen', side_effect=[overload, TimeoutError('secret'), generated(proposal())]), \
             patch.object(updater.random, 'uniform', return_value=0), \
             patch.object(updater.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(updater.generate(POST, TEXT, self.data, 'secret', updater.DEFAULT_MODEL), proposal())
            self.assertEqual([call.args[0] for call in sleep.call_args_list], [15, 4])

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

    def test_empty_model_env_uses_default(self):
        with patch.dict(os.environ, GEMINI_API_KEY='secret', GEMINI_MODEL=''), \
             patch.object(sys, 'argv', ['update', '--check-api']), \
             patch.object(updater, 'check_api') as check:
            self.assertEqual(updater.main(), 0)
            self.assertEqual(check.call_args.args, ('secret', updater.DEFAULT_MODEL))

    def test_check_api_cli_does_not_touch_resources_state_or_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.dict(os.environ, GEMINI_API_KEY='secret'), \
                 patch.object(sys, 'argv', ['update', '--check-api', '--apply', '--resources', str(root),
                                           '--state', str(root / 'state.json'), '--cache', str(root / 'cache'),
                                           '--continue-on-review', '--review-report', str(root / 'report.json')]), \
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
        with patch.object(updater, 'REVIEW_RULES_VERSION', updater.REVIEW_RULES_VERSION + 1):
            self.assertNotEqual(path, updater.cache_file(Path('cache'), POST, TEXT, self.data, updater.DEFAULT_MODEL))
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


class ReviewTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.resources = self.root / 'resources'
        self.resources.mkdir()
        for filename in [item[0] for item in updater.FILES.values()] + ['stat_weights.csv']:
            (self.resources / filename).write_bytes((updater.validator.RESOURCES / filename).read_bytes())
        self.before = {path.name: path.read_bytes() for path in self.resources.iterdir()}
        self.state = self.root / 'state.json'
        self.cache = self.root / 'cache'
        self.report = self.root / 'review.json'
        self.posts, self.bodies, self.results = [], {}, {}
        self.first = self.add_post('검토 스킬', 1)
        stack = contextlib.ExitStack()
        self.addCleanup(stack.close)
        stack.enter_context(patch.dict(os.environ, GEMINI_API_KEY='test'))
        stack.enter_context(patch.object(updater.notices, 'BOARDS', (9,)))
        stack.enter_context(patch.object(updater.notices, 'fetch_board', side_effect=lambda board: self.posts))
        stack.enter_context(patch.object(updater.notices, '_get', side_effect=lambda url: self.bodies[url]))
        stack.enter_context(patch.object(updater.time, 'sleep'))
        stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
        self.generate = stack.enter_context(patch.object(updater, 'generate', side_effect=self.generated))

    def add_post(self, name, index):
        post = {**POST, 'date': f'2026-10-{index:02d} 10:00:00', 'url': POST['url'] + str(index)}
        self.posts.append(post)
        self.bodies[post['url']] = ('<table><tr><td>스킬명</td><td>설명</td></tr>'
                                    f'<tr><td>{name}</td><td>파워 5 증가</td></tr></table>')
        self.results[post['url']] = proposal(name=name, evidence=name)
        return post

    def generated(self, post, *args, **kwargs):
        result = self.results[post['url']]
        if isinstance(result, Exception):
            raise result
        return copy.deepcopy(result)

    def run_update(self, **kwargs):
        return updater.run(self.resources, self.state, cache=self.cache, review_report=self.report, **kwargs)

    def assert_unchanged(self):
        self.assertEqual({path.name: path.read_bytes() for path in self.resources.iterdir()}, self.before)
        self.assertFalse(self.state.exists())

    def positive_entries(self):
        return [path for path in self.cache.glob('*.json') if not path.name.endswith('.review.json')]

    def test_supported_notices_before_and_after_review_are_applied(self):
        middle = self.add_post('보류 스킬', 2)
        last = self.add_post('지원 스킬', 3)
        self.results[middle['url']] = {'skills': [], 'review_reasons': ['team buff requires support']}
        report = self.run_update(apply=True, continue_on_review=True)
        state = json.loads(self.state.read_text())
        self.assertEqual(set(state['posts']), {self.first['url'], last['url']})
        self.assertEqual(set(state['reviews']), {middle['url']})
        self.assertEqual(report['status'], 'partial-review')
        self.assertEqual(report['counts']['processed'], 2)
        self.assertEqual(report['counts']['pending_review'], 1)
        self.assertTrue(report['written'])
        self.assertFalse(report['dry_run'])
        self.assertEqual(len(self.positive_entries()), 2)
        names = {row['name'] for row in updater.validator.load(self.resources / 'score_skills.csv')}
        self.assertTrue({'검토 스킬', '지원 스킬'} <= names)
        self.assertNotIn('보류 스킬', names)
        self.assertEqual(json.loads(self.report.read_text()), report)

    def test_multiskill_rejected_notice_rolls_back_earlier_mutations(self):
        self.bodies[self.first['url']] += '<p>오류 스킬</p>'
        self.results[self.first['url']]['skills'].append(proposal(name='오류 스킬', evidence='오류 스킬',
            effects=[dict(stat='파워', condition='ALWAYS', values='nan', base_stat='')])['skills'][0])
        report = self.run_update(apply=True, continue_on_review=True)
        self.assertEqual({path.name: path.read_bytes() for path in self.resources.iterdir()}, self.before)
        state = json.loads(self.state.read_text())
        self.assertEqual(state['posts'], {})
        self.assertEqual(state['skills'], {})
        self.assertEqual(len(state['reviews']), 1)
        self.assertEqual(report['counts']['processed'], 0)
        self.assertEqual(self.positive_entries(), [])

    def test_strict_mode_still_aborts_without_publishing(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        with self.assertRaisesRegex(ValueError, 'Manual review required'):
            self.run_update(apply=True)
        self.assert_unchanged()
        report = json.loads(self.report.read_text())
        self.assertEqual(report['status'], 'aborted')
        self.assertFalse(report['written'])

    def test_unchanged_pending_review_skips_api_and_remains_visible(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(apply=True, continue_on_review=True)
        self.generate.reset_mock()
        report = self.run_update(apply=True, continue_on_review=True)
        self.generate.assert_not_called()
        self.assertEqual(report['counts']['skipped_review'], 1)
        self.assertEqual(report['counts']['pending_review'], 1)
        self.assertEqual(report['status'], 'partial-review')
        self.assertEqual(self.positive_entries(), [])

    def test_review_metadata_avoids_paid_repeat_before_state_pr_is_merged(self):
        pending = self.add_post('보류 스킬', 2)
        self.results[pending['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(apply=True, continue_on_review=True)
        self.state.unlink()
        for name, contents in self.before.items():
            (self.resources / name).write_bytes(contents)
        self.generate.reset_mock()
        report = self.run_update(apply=True, continue_on_review=True)
        self.generate.assert_not_called()
        self.assertEqual(report['counts']['cache_hits'], 1)
        self.assertEqual(report['counts']['skipped_review'], 1)
        self.assertEqual(report['status'], 'partial-review')
        metadata = json.loads(next(self.cache.glob('*.review.json')).read_text())
        self.assertEqual(set(metadata), {'title', 'date', 'url', 'body_hash', 'request_fingerprint', 'reason'})
        self.assertEqual(metadata['url'], pending['url'])

    def test_repeated_dry_run_reuses_review_metadata_without_state_write(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(continue_on_review=True)
        self.generate.reset_mock()
        report = self.run_update(continue_on_review=True)
        self.generate.assert_not_called()
        self.assertFalse(self.state.exists())
        self.assertEqual(report['counts']['skipped_review'], 1)
        self.assertEqual(report['counts']['pending_review'], 1)

    def test_invalid_or_mismatched_review_metadata_is_ignored(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(continue_on_review=True)
        path = next(self.cache.glob('*.review.json'))
        good = json.loads(path.read_text())
        invalid = ['not JSON', '[]']
        invalid.extend(json.dumps({**good, key: value}) for key, value in [
            ('url', 'another source'), ('body_hash', 'wrong'), ('request_fingerprint', 'wrong'),
            ('title', 'wrong'), ('date', 'wrong'), ('reason', None), ('proposal', {'skills': []})])
        for contents in invalid:
            with self.subTest(contents=contents):
                path.write_text(contents)
                self.generate.reset_mock()
                report = self.run_update(continue_on_review=True)
                self.generate.assert_called_once()
                self.assertEqual(report['counts']['new_reviews'], 1)
                self.assertEqual(self.positive_entries(), [])

    def test_review_metadata_retries_changed_body_model_and_rules(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(continue_on_review=True)
        self.bodies[self.first['url']] += '<p>공지 수정</p>'
        self.generate.reset_mock()
        self.run_update(continue_on_review=True)
        self.generate.assert_called_once()
        self.generate.reset_mock()
        self.run_update(continue_on_review=True, model='gemini-3.6-flash')
        self.generate.assert_called_once()
        self.generate.reset_mock()
        with (self.resources / 'score_skills.csv').open('a') as handle:
            handle.write('G_999,NORMAL,BATTER,검토 스킬,파워 5 증가\n')
        with (self.resources / 'score_effects.csv').open('a') as handle:
            handle.write('G_999,파워,ALWAYS,5,\n')
        self.run_update(continue_on_review=True, model='gemini-3.6-flash')
        self.generate.assert_called_once()
        self.generate.reset_mock()
        with patch.object(updater, 'REVIEW_RULES_VERSION', updater.REVIEW_RULES_VERSION + 1):
            self.run_update(continue_on_review=True, model='gemini-3.6-flash')
        self.generate.assert_called_once()
        self.assertFalse(self.state.exists())

    def test_strict_mode_ignores_review_metadata_cache(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(continue_on_review=True)
        self.generate.reset_mock()
        with self.assertRaisesRegex(ValueError, 'Manual review required'):
            self.run_update()
        self.generate.assert_called_once()
        self.assert_unchanged()

    def test_pending_review_retries_after_source_model_csv_or_rules_change(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(apply=True, continue_on_review=True)
        self.generate.reset_mock()
        self.bodies[self.first['url']] += '<p>공지 수정</p>'
        self.run_update(apply=True, continue_on_review=True)
        self.generate.assert_called_once()
        self.generate.reset_mock()
        self.run_update(apply=True, continue_on_review=True, model='gemini-3.6-flash')
        self.generate.assert_called_once()
        self.generate.reset_mock()
        skills = self.resources / 'score_skills.csv'
        effects = self.resources / 'score_effects.csv'
        with skills.open('a') as handle:
            handle.write('G_999,NORMAL,BATTER,검토 스킬,파워 5 증가\n')
        with effects.open('a') as handle:
            handle.write('G_999,파워,ALWAYS,5,\n')
        self.run_update(apply=True, continue_on_review=True, model='gemini-3.6-flash')
        self.generate.assert_called_once()
        self.generate.reset_mock()
        with patch.object(updater, 'REVIEW_RULES_VERSION', updater.REVIEW_RULES_VERSION + 1):
            self.run_update(apply=True, continue_on_review=True, model='gemini-3.6-flash')
        self.generate.assert_called_once()

    def test_repaired_notice_clears_review_and_marks_processed(self):
        good = self.results[self.first['url']]
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(apply=True, continue_on_review=True)
        self.bodies[self.first['url']] += '<p>정보 보완</p>'
        self.results[self.first['url']] = good
        report = self.run_update(apply=True, continue_on_review=True)
        state = json.loads(self.state.read_text())
        self.assertEqual(state['reviews'], {})
        self.assertIn(self.first['url'], state['posts'])
        self.assertEqual(report['status'], 'complete')
        self.assertEqual(report['counts']['pending_review'], 0)

    def test_pending_review_outside_current_window_is_preserved(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        self.run_update(apply=True, continue_on_review=True)
        last = self.add_post('지원 스킬', 3)
        self.posts = [last]
        report = self.run_update(apply=True, continue_on_review=True)
        self.assertEqual(report['counts']['pending_review'], 1)
        self.assertEqual(report['counts']['new_reviews'], 0)
        self.assertEqual(report['reviews'][0]['url'], self.first['url'])

    def test_network_or_malformed_api_failure_is_not_hidden_as_review(self):
        for failure in (RuntimeError('Gemini HTTP 503'), ValueError('Gemini response has invalid candidates')):
            self.results[self.first['url']] = failure
            with self.subTest(failure=str(failure)), self.assertRaises(type(failure)):
                self.run_update(apply=True, continue_on_review=True)
            self.assert_unchanged()
            report = json.loads(self.report.read_text())
            self.assertEqual(report['status'], 'aborted')
            self.assertEqual(report['counts']['pending_review'], 0)
            self.assertEqual(list(self.cache.glob('*.json')), [])

    def test_preexisting_csv_error_is_fatal_before_api(self):
        with (self.resources / 'score_effects.csv').open('a') as handle:
            handle.write('DOES_NOT_EXIST,파워,ALWAYS,5,\n')
        with self.assertRaisesRegex(ValueError, 'CSV validation failed'):
            self.run_update(apply=True, continue_on_review=True)
        self.generate.assert_not_called()
        self.assertFalse(self.state.exists())
        self.assertEqual(json.loads(self.report.read_text())['status'], 'aborted')

    def test_dry_run_reports_pending_without_writing_csv_or_state(self):
        self.results[self.first['url']] = {'skills': [], 'review_reasons': ['unsupported team buff']}
        report = self.run_update(continue_on_review=True)
        self.assert_unchanged()
        self.assertTrue(report['dry_run'])
        self.assertFalse(report['written'])
        self.assertEqual(report['status'], 'partial-review')
        self.assertEqual(report['counts']['pending_review'], 1)

    def test_invalid_review_state_is_fatal_and_report_write_errors_are_clear(self):
        self.state.write_text(json.dumps({'posts': {}, 'skills': {}, 'reviews': []}))
        with self.assertRaisesRegex(ValueError, 'Invalid notice state: reviews'):
            self.run_update(continue_on_review=True)
        self.generate.assert_not_called()
        with self.assertRaisesRegex(RuntimeError, 'Could not write review report'):
            updater.write_report(self.resources, {'status': 'complete'})

    def test_report_failure_preserves_original_api_failure_diagnostic(self):
        self.results[self.first['url']] = RuntimeError('ORIGINAL API 503')
        self.report = self.resources
        with self.assertRaises(RuntimeError) as caught:
            self.run_update(continue_on_review=True)
        message = str(caught.exception)
        self.assertIn('ORIGINAL API 503', message)
        self.assertIn('Could not write review report', message)
        self.assert_unchanged()

    def test_report_write_failure_without_other_error_still_fails(self):
        self.report = self.resources
        with self.assertRaisesRegex(RuntimeError, 'Could not write review report'):
            self.run_update(continue_on_review=True)
        self.assert_unchanged()


if __name__ == '__main__':
    unittest.main()
