// Writes docs/brand/options/<id>.svg (full-colour icon tile) and <id>-mono.svg (one-colour mark) for every direction.
// Usage: node docs/brand/src/export-options.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
const here = dirname(fileURLToPath(import.meta.url));
const ctx = {}; vm.createContext(ctx);
vm.runInContext(readFileSync(resolve(here, 'options.js'), 'utf8') + ';this.ALL = ROUND1.concat(FAMILY); this.optIcon = optIcon; this.optMark = optMark;', ctx);
const x = (s) => '<?xml version="1.0" encoding="UTF-8"?>\n' + s.replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" ') + '\n';
for (const o of ctx.ALL) {
  writeFileSync(resolve(here, '../options', `${o.id}-${o.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/-$/, '')}.svg`), x(ctx.optIcon(o, 512, 'rounded')));
  writeFileSync(resolve(here, '../options', `${o.id}-mono.svg`), x(ctx.optMark(o, 512, '#0F1216')));
}
console.log('exported', ctx.ALL.length);
