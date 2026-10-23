// Render mockups to PNG with the preinstalled Chromium.
// Usage: PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers node docs/design/src/render.mjs [name ...]
// Phone screens: <name>.html containing .screen (412x915) -> png/<name>.png at DPR 2.
// Sheets: files starting with "00-" or containing class "sheet" are captured full page.
import { createRequire } from 'node:module';
import { readdirSync } from 'node:fs';
const require = createRequire(import.meta.url);
let pw; try { pw = require('playwright'); } catch { pw = require('/opt/node-tools/node_modules/playwright'); }
const { chromium } = pw;
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, '../png');
const wanted = process.argv.slice(2);
const pages = readdirSync(here).filter((f) => f.endsWith('.html') && !f.startsWith('_'))
  .filter((f) => !wanted.length || wanted.some((w) => f.startsWith(w))).sort();

const browser = await chromium.launch();
for (const f of pages) {
  const isSheet = f.startsWith('00-') || f.includes('sheet');
  const ctx = await browser.newContext({ deviceScaleFactor: 2, viewport: isSheet ? { width: 1920, height: 1200 } : { width: 412, height: 2000 } });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.error(f, 'pageerror', e.message));
  await page.goto('file://' + join(here, f));
  await page.waitForSelector('body[data-ready="1"]', { timeout: 20000 });
  await page.waitForTimeout(250);
  const name = f.replace('.html', '.png');
  if (isSheet) await page.screenshot({ path: join(out, name), fullPage: true });
  else await page.locator('.screen').first().screenshot({ path: join(out, name) });
  console.log('rendered', name);
  await ctx.close();
}
await browser.close();
