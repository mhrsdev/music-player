import importlib.util
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from contrast import check_pairs, parse_color, ratio, shown, tokens_from_css
from route import route
import inspect_page

TEMPLATE = (ROOT / 'templates/theme-tokens.css').read_text(encoding='utf-8')


class ContrastTests(unittest.TestCase):
    def test_known_values(self):
        self.assertEqual(shown(ratio('#000', '#fff')), 21.0)
        self.assertEqual(shown(ratio('#767676', '#ffffff')), 4.54)
        self.assertGreaterEqual(ratio('#767676', '#ffffff'), 4.5)
        self.assertLess(ratio('#777777', '#ffffff'), 4.5)

    def test_order_does_not_matter(self):
        self.assertAlmostEqual(ratio('#1d4ed8', '#fff'), ratio('#fff', '#1d4ed8'))

    def test_display_never_rounds_up_across_a_threshold(self):
        self.assertLess(shown(ratio('#777777', '#ffffff')), 4.5)

    def test_rejects_transparency_and_garbage(self):
        for value in ['#11223344', 'red', 'rgb(0,0,0)', '#12', '']:
            with self.assertRaises(ValueError, msg=value):
                parse_color(value)

    def test_short_hex(self):
        self.assertEqual(parse_color('#fff'), (255, 255, 255))


class ThemeTemplateTests(unittest.TestCase):
    PAIRS = ['fg:bg', 'fg:surface', 'muted:bg', 'muted:surface', 'accent-fg:accent',
             'accent:bg:3', 'focus:bg:3', 'good:surface', 'warn:surface', 'critical:surface']

    def test_template_passes_in_both_themes(self):
        rows = check_pairs(TEMPLATE, self.PAIRS)
        self.assertEqual({row['theme'] for row in rows}, {'light', 'dark'})
        self.assertEqual([row for row in rows if not row['pass']], [])

    def test_a_weak_pair_is_reported(self):
        weak = TEMPLATE.replace('--muted: #4a5666;', '--muted: #9aa5b3;', 1)
        rows = check_pairs(weak, ['muted:bg'])
        self.assertFalse(next(row for row in rows if row['theme'] == 'light')['pass'])

    def test_unknown_token_fails_loudly(self):
        with self.assertRaises(ValueError):
            check_pairs(TEMPLATE, ['nonexistent:bg'])

    def test_dark_blocks_only_redefine_tokens_declared_on_root(self):
        light, dark = tokens_from_css(TEMPLATE)
        blocks = re.findall(r'([^{}]+)\{([^{}]*)\}', re.sub(r'/\*.*?\*/', '', TEMPLATE, flags=re.S))
        dark_blocks = [dict(re.findall(r'--([\w-]+)\s*:\s*([^;]+);', body)) for sel, body in blocks
                       if 'dark' in sel or ':not([data-theme="light"])' in sel]
        self.assertEqual(len(dark_blocks), 2)
        self.assertEqual(dark_blocks[0], dark_blocks[1], 'the media and explicit dark blocks must agree')
        for block in dark_blocks:
            self.assertLessEqual(set(block), set(light))
        self.assertNotEqual(light['bg'], dark['bg'])

    def test_body_states_its_background_and_color_scheme_follows_the_theme(self):
        self.assertRegex(TEMPLATE, r'body\s*\{[^}]*background:\s*var\(--bg\)')
        self.assertEqual(len(re.findall(r'^\s*color-scheme:\s*dark;', TEMPLATE, re.M)), 2)


class RoutingAdditionTests(unittest.TestCase):
    def ids(self, query, **kwargs):
        return [item['id'] for item in route(query, **kwargs)['modules']]

    def test_diagram_requests_no_longer_abstain(self):
        self.assertIn('diagrams', self.ids('architecture diagram of our queue'))
        self.assertIn('diagrams', self.ids('دیاگرام معماری سیستم'))

    def test_chart_requests_in_both_languages_bring_color(self):
        for query in ['bar chart with a dark mode palette', 'نمودار فروش منطقه‌ای']:
            ids = self.ids(query)
            self.assertIn('charts', ids, query)
            self.assertLess(ids.index('color'), ids.index('charts'), query)

    def test_dashboard_with_chart_gets_both_data_modules(self):
        ids = self.ids('dashboard filters and chart')
        self.assertTrue({'data-interfaces', 'charts'} <= set(ids))
        self.assertNotIn('art-direction', ids)

    def test_published_pages(self):
        ids = self.ids('single page html artifact')
        self.assertEqual(ids[-1], 'hosted-pages')
        self.assertTrue({'color', 'responsive'} <= set(ids))
        self.assertIn('hosted-pages', self.ids('صفحه تک‌فایلی'))

    def test_landing_page_is_a_product_pattern(self):
        self.assertIn('product-patterns', self.ids('landing page for a notes app'))
        self.assertIn('product-patterns', self.ids('صفحه فرود برای یک اپ'))

    def test_authorship_routes_in_both_languages_with_judgment(self):
        for query in ['this looks like AI slop', 'make it less generic', 'این طراحی شبیه هوش مصنوعی است', 'قالبی']:
            ids = self.ids(query)
            self.assertEqual(ids[-1], 'authorship', query)
            self.assertIn('judgment', ids, query)

    def test_detecting_ai_text_or_images_is_not_a_design_request(self):
        for query in ['detect whether an essay was written by an ai', 'classify ai generated images with pytorch']:
            self.assertNotIn('authorship', self.ids(query), query)

    def test_new_vocabulary_does_not_capture_unrelated_requests(self):
        for query in ['optimize postgres joins', 'plot twist in my novel', 'graphql schema for orders']:
            self.assertTrue(route(query)['abstained'], query)


class InspectPageTests(unittest.TestCase):
    def report(self, **changes):
        base = {'scrollWidth': 390, 'clientWidth': 390, 'overflowing': [], 'errors': [], 'imagesWithoutAlt': 0,
                'unnamedControls': [], 'smallTargets': [], 'invisibleAtRest': [], 'placeholderText': False, 'h1': 1,
                'viewportMeta': True, 'title': 'Name', 'fonts': [], 'contrast': {'checked': 3, 'failures': []}}
        base.update(changes)
        return base

    def test_clean_report_has_no_flags(self):
        self.assertEqual(inspect_page.flags(self.report()), [])

    def test_each_measurable_problem_is_flagged(self):
        problems = self.report(scrollWidth=900, overflowing=['div.wide right=900 left=8'], errors=['failed to load x.png'],
                               imagesWithoutAlt=1, unnamedControls=['button'], invisibleAtRest=['p.reveal'],
                               placeholderText=True, h1=0, viewportMeta=False, title=' ', fonts=['Brand:error'],
                               contrast={'checked': 3, 'failures': [{'text': 'pale', 'ratio': 2.0, 'minimum': 4.5}]})
        text = ' | '.join(inspect_page.flags(problems))
        for expected in ['sideways', 'past the window edge', 'errors', 'contrast', 'alt', 'without a name', 'invisible',
                         'placeholder', '0 h1', 'viewport', '<title>', 'fonts']:
            self.assertIn(expected, text)

    def test_instrumentation_goes_after_head_and_resolves_relative_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            page = Path(directory) / 'p.html'
            page.write_text('<!doctype html><html><head><title>t</title></head><body></body></html>', encoding='utf-8')
            copy = inspect_page.instrumented_copy(page, directory).read_text(encoding='utf-8')
            self.assertLess(copy.index('<base href='), copy.index('<title>'))
            self.assertIn(page.parent.resolve().as_uri(), copy)

    @unittest.skipUnless(inspect_page.find_browser(None), 'no Chromium-family browser installed')
    def test_real_render_finds_a_planted_overflow_and_a_clean_page(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bad = root / 'bad.html'
            bad.write_text('<!doctype html><meta name=viewport content="width=device-width"><title>t</title>'
                           '<h1>x</h1><div style="width:900px;height:20px;background:#ccc"></div>', encoding='utf-8')
            good = root / 'good.html'
            good.write_text('<!doctype html><meta name=viewport content="width=device-width"><title>t</title>'
                            '<style>body{background:#fff;color:#111}</style><h1>x</h1><p>text</p>', encoding='utf-8')
            browser = inspect_page.find_browser(None)
            copy = inspect_page.instrumented_copy(bad, directory)
            measured = inspect_page.probe(browser, copy, 390, 800, 1)
            self.assertIsNotNone(measured)
            self.assertGreater(measured['scrollWidth'], measured['clientWidth'])
            clean = inspect_page.probe(browser, inspect_page.instrumented_copy(good, directory), 390, 800, 1)
            self.assertEqual(inspect_page.flags(clean), [])


class SignalTests(unittest.TestCase):
    def report(self, **changes):
        base = {'headingCount': 6, 'tells': {}, 'radius': {'boxes': 0, 'top': 0, 'share': 0}, 'fontsUsed': {'body': 'Georgia', 'heading': 'Georgia'},
                'hoverLifts': 0, 'centered': {'blocks': 0, 'share': 0}}
        base.update(changes)
        return base

    def test_quiet_page_has_no_signals(self):
        self.assertEqual(inspect_page.tell_signals(self.report()), [])

    def test_each_family_of_tell_is_reported_in_plain_words(self):
        tells = {key: {'count': 9, 'examples': ['p.x']} for key in
                 ['eyebrow', 'gradientText', 'purpleBlueGradient', 'glass', 'decorativeGrid', 'sideStripeCard', 'iconTile', 'infiniteAnimation',
                  'animated', 'ctaArrow', 'zeroPaddedNumber', 'fillerWords', 'emDash', 'contrastSlogan', 'roundStats', 'socialProof',
                  'stockNames', 'emoji', 'largeSvg', 'pillBadge', 'accentedHeadline', 'statBanner', 'faqAccordion', 'emojiNav', 'coloredGlow',
                  'purpleCta', 'deadLink', 'clickableDiv', 'genericCta', 'puffery', 'rtlPhysical', 'arabicLetterSpacing', 'langMismatch']}
        tells['identicalCards'] = {'count': 1, 'examples': ['section.grid x3'], 'largest': 3}
        signals = inspect_page.tell_signals(self.report(tells=tells, radius={'boxes': 10, 'top': 20, 'share': 0.9}, fontsUsed={'body': 'Inter', 'heading': 'Inter'},
                                                         hoverLifts=4, centered={'blocks': 10, 'share': 0.9}))
        text = ' | '.join(signals)
        for expected in ['uppercase', 'gradient-filled', 'purple-to-blue', 'glass', 'grid or dot', 'side stripe', 'tinted', 'identical cards', 'corner radius',
                         'common default: Inter', 'hover rules', 'animate forever', 'animated elements', 'centered', 'arrow', 'zero-padded',
                         'filler', 'em dashes', 'construction', 'round figure', 'social-proof', 'stock name', 'emoji', 'inline drawing',
                         'pill badge', 'main headline is styled', 'headline statistics', 'question-and-answer', 'start with an emoji', 'colored shadows',
                         'purple or indigo', 'go nowhere', 'onclick', 'generic call-to-action', 'promotional or significance', 'physical left or right',
                         'letter-spacing on connected script', 'Arabic-script text']:
            self.assertIn(expected, text)

    def test_first_screen_count_is_shown(self):
        tells = {'gradientText': {'count': 1, 'examples': ['h1'], 'first': 1}}
        self.assertIn('1 on the first screen', ' '.join(inspect_page.tell_signals(self.report(tells=tells))))

    def test_em_dash_rate_is_reported_per_thousand_words(self):
        tells = {'emDash': {'count': 6, 'examples': []}}
        text = ' '.join(inspect_page.tell_signals(self.report(tells=tells, words=300)))
        self.assertIn('20.0 per 1,000 words', text)

    def test_density_bands(self):
        self.assertEqual([inspect_page.signal_density(n) for n in (0, 1, 2, 3, 4, 5, 9)],
                         ['none', 'a few', 'a few', 'several', 'several', 'many', 'many'])

    def test_thresholds_keep_single_ordinary_uses_quiet(self):
        tells = {'eyebrow': {'count': 1, 'examples': []}, 'iconTile': {'count': 2, 'examples': []}, 'emDash': {'count': 2, 'examples': []},
                 'ctaArrow': {'count': 1, 'examples': []}, 'zeroPaddedNumber': {'count': 2, 'examples': []}}
        self.assertEqual(inspect_page.tell_signals(self.report(tells=tells, hoverLifts=2)), [])

    @unittest.skipUnless(inspect_page.find_browser(None), 'no Chromium-family browser installed')
    def test_real_render_flags_a_planted_generated_looking_page_and_not_a_plain_one(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            slop = root / 'slop.html'
            slop.write_text('<!doctype html><meta name=viewport content="width=device-width"><title>t</title><style>'
                            'body{font-family:Inter,sans-serif;text-align:center}'
                            '.eyebrow{font-size:12px;letter-spacing:.14em;text-transform:uppercase}'
                            'h1{background:linear-gradient(90deg,#7c3aed,#2563eb);-webkit-background-clip:text;background-clip:text;color:transparent}'
                            '.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}'
                            '.c{border:1px solid #ddd;border-radius:20px;padding:20px;box-shadow:0 4px 12px #0002}'
                            '</style><p class=eyebrow>Introducing</p><h1>Seamlessly unlock your potential</h1>'
                            '<div class=grid><div class=c><h3>A</h3><p>One</p></div><div class=c><h3>B</h3><p>Two</p></div><div class=c><h3>C</h3><p>Three</p></div></div>'
                            '<p class=eyebrow>Pricing</p><h2>Plans</h2>'
                            '<a href="#">Learn more</a><a class=btn href="#nowhere">Get started</a>'
                            '<details><summary>Is it safe?</summary>Yes.</details><details><summary>Is it fast?</summary>Yes.</details>'
                            '<details><summary>Is it free?</summary>No.</details>',
                            encoding='utf-8')
            plain = root / 'plain.html'
            plain.write_text('<!doctype html><meta name=viewport content="width=device-width"><title>t</title><h1>Field notes</h1><p>Written on the device.</p>',
                             encoding='utf-8')
            browser = inspect_page.find_browser(None)
            found = inspect_page.probe(browser, inspect_page.instrumented_copy(slop, directory), 1280, 900, 1)
            text = ' | '.join(inspect_page.tell_signals(found))
            for expected in ['uppercase', 'gradient-filled', 'identical cards', 'filler', 'common default: Inter', 'go nowhere', 'question-and-answer',
                             'generic call-to-action']:
                self.assertIn(expected, text)
            quiet = inspect_page.probe(browser, inspect_page.instrumented_copy(plain, directory), 1280, 900, 1)
            self.assertEqual(inspect_page.tell_signals(quiet), [])


class RtlSignalTests(unittest.TestCase):
    @unittest.skipUnless(inspect_page.find_browser(None), 'no Chromium-family browser installed')
    def test_physical_values_and_letter_spacing_are_flagged_on_a_rtl_page_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rtl = root / 'rtl.html'
            rtl.write_text('<!doctype html><html lang="fa" dir="rtl"><meta name=viewport content="width=device-width"><title>t</title>'
                           '<style>.a{margin-left:20px;text-align:left}.b{letter-spacing:2px}</style>'
                           '<h1>\u0633\u0644\u0627\u0645</h1><p class=a>\u0645\u062a\u0646 \u0641\u0627\u0631\u0633\u06cc</p><p class=b>\u0645\u062a\u0646 \u062f\u06cc\u06af\u0631</p></html>',
                           encoding='utf-8')
            ltr = root / 'ltr.html'
            ltr.write_text('<!doctype html><meta name=viewport content="width=device-width"><title>t</title>'
                           '<style>.a{margin-left:20px;text-align:left}</style><h1>Hello</h1><p class=a>Text</p>', encoding='utf-8')
            browser = inspect_page.find_browser(None)
            found = inspect_page.probe(browser, inspect_page.instrumented_copy(rtl, directory), 1280, 900, 1)
            text = ' | '.join(inspect_page.tell_signals(found))
            self.assertIn('physical left or right', text)
            self.assertIn('letter-spacing on connected script', text)
            plain = inspect_page.probe(browser, inspect_page.instrumented_copy(ltr, directory), 1280, 900, 1)
            self.assertNotIn('physical left or right', ' | '.join(inspect_page.tell_signals(plain)))


class PackageConsistencyTests(unittest.TestCase):
    def test_every_catalog_module_is_linked_from_the_entry_table(self):
        entry = (ROOT / 'SKILL.md').read_text(encoding='utf-8')
        for row in json.loads((ROOT / 'references/catalog.json').read_text(encoding='utf-8')):
            if row['id'] != 'judgment':
                self.assertIn(row['path'], entry, row['id'])


if __name__ == '__main__':
    unittest.main()
