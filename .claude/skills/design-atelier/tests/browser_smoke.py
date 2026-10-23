"""Optional example checks: requires Playwright Python and installed Chromium.

Run: python tests/browser_smoke.py --output /path/outside/skill
No dependencies are installed by this script. This is fixture QA, not a model benchmark.
"""
import argparse
from functools import partial
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from threading import Thread

from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
CONTRAST = r"""() => {
 const rgb=s=>{const n=s.match(/[\d.]+/g);return n?[+n[0],+n[1],+n[2],n[3]===undefined?1:+n[3]]:[0,0,0,0]};
 const over=(f,b)=>f.slice(0,3).map((v,i)=>v*f[3]+b[i]*(1-f[3]));
 const lum=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0);
 const failures=[];let checked=0;
 for(const el of document.querySelectorAll('body *')){
  const s=getComputedStyle(el),r=el.getBoundingClientRect();
  if(!r.width||!r.height||s.visibility==='hidden'||s.display==='none'||['SCRIPT','STYLE','NOSCRIPT'].includes(el.tagName))continue;
  const text=Array.from(el.childNodes).filter(n=>n.nodeType===Node.TEXT_NODE).map(n=>n.textContent.trim()).join(' ').trim();
  if(!text)continue;
  const chain=[];for(let a=el;a;a=a.parentElement)chain.push(rgb(getComputedStyle(a).backgroundColor));
  let bg=[255,255,255];for(const c of chain.reverse())bg=over(c,bg);
  const fg=over(rgb(s.color),bg),a=lum(fg),b=lum(bg),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  const large=parseFloat(s.fontSize)>=24||(parseFloat(s.fontSize)>=18.666&&parseFloat(s.fontWeight)>=700),minimum=large?3:4.5;
  checked++;if(ratio+.001<minimum)failures.push({text:text.slice(0,70),ratio,minimum});
 }
 return {checked,failures,limit:'Heuristic direct text with ancestor solid-color compositing; no placeholder, image, overlap or full a11y audit.'};
}"""


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def run(output, executable=None):
    output.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(ROOT)))
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    origin = f'http://127.0.0.1:{server.server_port}'
    checks = []
    errors = []
    try:
        with sync_playwright() as runtime:
            browser = runtime.chromium.launch(executable_path=executable)
            version = browser.version
            for width in [320, 390, 768, 1440]:
                for motion in ['no-preference', 'reduce']:
                    for name in ['editorial', 'operations', 'booking']:
                        page = browser.new_page(viewport={'width': width, 'height': 900}, reduced_motion=motion)
                        page.on('pageerror', lambda error: errors.append(str(error)))
                        page.goto(f'{origin}/examples/web/{name}.html')
                        page.evaluate('document.fonts.ready')
                        assert page.locator('h1').is_visible(), (name, width)
                        assert page.evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth'), (name, width, 'page overflow')
                        missing = page.locator('button, input, select').evaluate_all("els => els.filter(el=>!el.closest('[hidden]') && el.getBoundingClientRect().width>0).filter(el=>!(el.labels?.length || el.getAttribute('aria-label') || el.textContent.trim())).length")
                        assert missing == 0, (name, width, 'unnamed controls')
                        contrast = page.evaluate(CONTRAST)
                        assert not contrast['failures'], (name, contrast)
                        if motion == 'reduce' and name == 'editorial':
                            assert page.evaluate("getComputedStyle(document.documentElement).scrollBehavior") == 'auto'
                        filename = f'{name}-{width}-{motion}.png'
                        page.screenshot(path=str(output / filename), full_page=True)
                        checks.append({'case': f'{name}-{width}-{motion}', 'kind': 'render', 'status': 'pass', 'screenshot': filename, 'text_contrast': contrast})
                        page.close()
                page = browser.new_page(viewport={'width': width, 'height': 900})
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.goto(f'{origin}/examples/web/operations.html')
                expect(page.locator('#rows tr')).to_have_count(5)
                page.locator('#status').select_option('Delayed')
                expect(page.locator('#rows tr')).to_have_count(2)
                page.locator('#search').fill('no-matching-record')
                expect(page.locator('#rows tr')).to_have_count(0)
                expect(page.locator('#empty')).to_be_visible()
                page.get_by_role('button', name='Clear filters').click()
                expect(page.locator('#rows tr')).to_have_count(5)
                opener = page.get_by_role('button', name='View shipment HB-2408')
                opener.focus()
                opener.press('Enter')
                expect(page.locator('#details')).to_be_visible()
                expect(page.locator('#detail-title')).to_have_text('HB-2408')
                page.keyboard.press('Escape')
                expect(page.locator('#details')).not_to_be_visible()
                expect(opener).to_be_focused()
                checks.append({'case': f'operations-journey-{width}', 'kind': 'interaction', 'status': 'pass', 'steps': ['filter', 'combined-empty', 'reset', 'keyboard-open', 'correct-detail', 'escape', 'focus-return']})
                page.goto(f'{origin}/examples/web/booking.html')
                page.get_by_role('button', name='مرور درخواست').click()
                expect(page.locator('#error-summary')).to_contain_text('اصلاح')
                expect(page.locator('input[name=slot]').first).to_be_focused()
                page.locator('input[name=slot]').first.press('Space')
                expect(page.locator('input[name=slot]').first).to_be_checked()
                page.locator('#name').fill('کاربر نمونه')
                page.locator('#phone').fill('۱۲۳')
                page.locator('#phone').press('Enter')
                expect(page.locator('#phone')).to_have_attribute('aria-invalid', 'true')
                expect(page.locator('#name')).to_have_value('کاربر نمونه')
                page.locator('#phone').fill('۰۹۱۲ ۰۰۰ ۰۰۰۰')
                page.locator('#phone').press('Enter')
                expect(page.locator('#review')).to_be_visible()
                expect(page.locator('#review')).to_be_focused()
                expect(page.locator('#review')).to_contain_text('هیچ نوبتی رزرو نشده')
                page.get_by_role('button', name='بازگشت و ویرایش').click()
                expect(page.locator('#name')).to_have_value('کاربر نمونه')
                expect(page.locator('#phone')).to_have_value('۰۹۱۲ ۰۰۰ ۰۰۰۰')
                expect(page.locator('#name')).to_be_focused()
                checks.append({'case': f'booking-journey-{width}', 'kind': 'interaction', 'status': 'pass', 'steps': ['required-errors', 'first-error-focus', 'keyboard-radio', 'invalid-phone', 'input-preserved', 'persian-numerals', 'keyboard-submit', 'honest-review', 'edit-preserves-input']})
                page.goto(f'{origin}/examples/web/editorial.html')
                page.get_by_role('link', name='Read the opening essay').click()
                assert page.url.endswith('#story')
                summary = page.locator('summary').first
                summary.focus()
                summary.press('Enter')
                expect(page.locator('details').first).to_have_attribute('open', '')
                checks.append({'case': f'editorial-journey-{width}', 'kind': 'interaction', 'status': 'pass', 'steps': ['reading-anchor', 'keyboard-disclosure']})
                page.close()
            # An additional short-height layout sample is distinct from zoom/device tests.
            page = browser.new_page(viewport={'width': 1024, 'height': 600})
            for name in ['editorial', 'operations', 'booking']:
                page.goto(f'{origin}/examples/web/{name}.html')
                assert page.evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth')
                checks.append({'case': f'{name}-short-height', 'kind': 'layout', 'status': 'pass'})
            page.close()
            browser.close()
        assert not errors, errors
        report = {'environment': {'browser': 'Chromium ' + version, 'mode': 'headless desktop browser, CSS viewport emulation'}, 'checks': checks, 'runtime_errors': errors,
                  'limits': ['Not a controlled model comparison', 'No independent designer or user study', 'No real-device, Safari, screen-reader or native-runtime test', '320px layout is a reflow proxy, not all browser-zoom behavior', 'Solid-color text contrast heuristic is not a full WCAG audit']}
        (output / 'browser-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'passed_cases': len(checks), 'screenshots': 24, 'runtime_errors': len(errors), 'browser': version}))
    finally:
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--executable', help='Optional existing Chromium executable path')
    args = parser.parse_args()
    target = args.output.resolve()
    if target.is_relative_to(ROOT):
        parser.error('write evidence outside the skill directory')
    run(target, args.executable)
