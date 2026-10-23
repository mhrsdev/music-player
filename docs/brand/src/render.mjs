// Render brand sheets to PNG with the preinstalled Chromium (no install).
// Usage: PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers node docs/brand/src/render.mjs [name ...]
// Each <name>.html with a #shot element is captured as ../<name>.png (DPR from data-dpr, default 1).
import { createRequire } from 'node:module';
import { readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
let pw; try { pw = require('playwright'); } catch { pw = require('/opt/node-tools/node_modules/playwright'); }
const here = dirname(fileURLToPath(import.meta.url));
const wanted = process.argv.slice(2);
const pages = readdirSync(here).filter((f) => f.endsWith('.html') && !f.startsWith('_'))
  .filter((f) => !wanted.length || wanted.some((w) => f.startsWith(w))).sort();
const browser = await pw.chromium.launch();
for (const f of pages) {
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.error(f, 'pageerror', e.message));
  await page.goto('file://' + join(here, f));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(200);
  const out = await page.evaluate(() => document.body.dataset.out || '');
  await page.locator('#shot').screenshot({ path: resolve(here, '..', out || f.replace('.html', '.png')), omitBackground: true });
  console.log('rendered', out || f);
  await ctx.close();
}
await browser.close();
