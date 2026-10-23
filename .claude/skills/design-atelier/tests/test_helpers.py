import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from route import route
from evidence import validate


class RoutingTests(unittest.TestCase):
    def test_mixed_persian_booking_recovers_required_context(self):
        result = route('فرم رزرو فارسی با خطا و کیبورد')
        ids = {item['id'] for item in result['modules']}
        self.assertTrue({'localization', 'interaction', 'typography', 'responsive', 'accessibility'} <= ids)

    def test_persian_arabic_variants_and_joiners(self):
        result = route('كيبرد و دسترس\u200cپذیری')
        self.assertIn('accessibility', [item['id'] for item in result['modules']])

    def test_domain_does_not_force_a_style(self):
        result = route('dashboard filters and chart')
        self.assertIn('data-interfaces', [item['id'] for item in result['modules']])
        self.assertNotIn('art-direction', [item['id'] for item in result['modules']])

    def test_prerequisites_survive_small_limit(self):
        result = route('RTL', limit=1)
        self.assertEqual([item['id'] for item in result['modules']], ['typography', 'responsive', 'localization'])

    def test_unrelated_and_ambiguous_requests_abstain(self):
        for query in ['optimize postgres joins', 'design', 'سلام', '']:
            self.assertTrue(route(query)['abstained'], query)

    def test_word_boundaries(self):
        self.assertTrue(route('colorado reformer')['abstained'])

    def test_native_platform(self):
        self.assertIn('platforms', [item['id'] for item in route('SwiftUI iOS application')['modules']])

    def test_cycle_and_missing_dependency_fail(self):
        catalog = [{'id': 'a', 'path': 'a.md', 'terms': ['a'], 'requires': ['a']}]
        with self.assertRaisesRegex(ValueError, 'cycle'):
            route('a', catalog=catalog)
        catalog[0]['requires'] = ['unknown']
        with self.assertRaisesRegex(ValueError, 'unknown dependency'):
            route('a', catalog=catalog)

    def test_invalid_limit(self):
        with self.assertRaises(ValueError):
            route('RTL', limit=0)

    def test_self_critique_routes_both_languages_with_verification(self):
        for query in ['self-critique', 'خودنقدی', 'کاربر تازه\u200cکار', 'experienced user']:
            result = route(query, limit=1)
            self.assertEqual([item['id'] for item in result['modules']],
                             ['verification', 'self-critique'], query)


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'result.log').write_text('observed output\n')
        self.artifact = {'path': 'result.log', 'sha256': hashlib.sha256(b'observed output\n').hexdigest()}
        self.document = {'schema_version': 1, 'scope': 'fixture check', 'checks': [
            {'id': 'journey', 'status': 'pass', 'method': 'Inspect recorded journey',
             'artifacts': [self.artifact]}]}

    def tearDown(self):
        self.temp.cleanup()

    def errors(self):
        return validate(self.document, self.root)

    def test_valid_declaration(self):
        self.assertEqual(self.errors(), [])

    def test_missing_artifact_fails(self):
        self.artifact['path'] = 'nonexistent.png'
        self.assertTrue(self.errors())

    def test_tamper_fails(self):
        (self.root / 'result.log').write_text('different output')
        self.assertTrue(self.errors())

    def test_outside_root_and_absolute_paths_fail(self):
        for path in ['../outside.log', str(self.root / 'result.log')]:
            self.artifact['path'] = path
            self.assertTrue(self.errors())

    def test_symlink_escape_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            outside = Path(directory) / 'private.log'
            outside.write_text('observed output\n')
            (self.root / 'link.log').symlink_to(outside)
            self.artifact['path'] = 'link.log'
            self.assertTrue(self.errors())

    def test_empty_evidence_is_not_pass(self):
        self.document['checks'][0]['artifacts'] = []
        self.assertTrue(self.errors())

    def test_not_run_requires_reason_and_cannot_claim_exit(self):
        check = self.document['checks'][0]
        check.update(status='not_run', artifacts=[])
        self.assertTrue(self.errors())
        check['reason'] = 'Target browser unavailable'
        self.assertEqual(self.errors(), [])
        check['exit_code'] = 0
        self.assertTrue(self.errors())

    def test_nonzero_exit_cannot_pass(self):
        self.document['checks'][0].update(command=['python', 'check.py'], exit_code=1)
        self.assertTrue(self.errors())

    def test_unknown_status_and_duplicate_ids(self):
        self.document['checks'][0]['status'] = 'certified'
        self.assertTrue(self.errors())
        self.document['checks'][0]['status'] = 'pass'
        self.document['checks'].append(copy.deepcopy(self.document['checks'][0]))
        self.assertTrue(self.errors())

    def test_schema_rejects_unknown_and_boolean_version(self):
        self.document['schema_version'] = True
        self.assertTrue(self.errors())
        self.document['schema_version'] = 1
        self.document['approved_quality'] = True
        self.assertTrue(self.errors())

    def test_unrun_template_is_honest_and_valid(self):
        document = json.loads((ROOT / 'templates/evidence.example.json').read_text())
        self.assertEqual(validate(document, self.root), [])


if __name__ == '__main__':
    unittest.main()
