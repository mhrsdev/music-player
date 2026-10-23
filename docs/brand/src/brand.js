// Mowj final mark = option M2 of options.js ("M ~ م": Latin M and Persian م joined by one wave line).
// Load options.js first. Keep res/drawable/ic_launcher_foreground.xml, ic_launcher_monochrome.xml and
// ic_stat_playback.xml in sync with the M2 paths there.
const MOWJ = OPTIONS.find((o) => o.id === RECOMMENDED);
const MOWJ_BOX = '23 30.5 61.1 49'; // stroke-inclusive bounds of the unscaled mark, +1 unit air
let _bid = 0;
function mark(h, stroke) { // h = rendered height in px
  const id = 'm' + (++_bid), [, , bw, bh] = MOWJ_BOX.split(' ').map(Number);
  const grads = { grad: ['#2F5F95', '#0E8C84'], 'grad-dark': ['#A6C8FF', '#7FD6CB'] };
  const g = grads[stroke];
  const paint = g ? `url(#${id})` : stroke;
  const paths = MOWJ.paths().map((p) => `<path d="${p.d}" fill="none" stroke="${paint}" stroke-width="${p.stroke}" stroke-linecap="round" stroke-linejoin="round"/>`).join('');
  return `<svg class="mark" viewBox="${MOWJ_BOX}" height="${h}" width="${r2(h * bw / bh)}">${g ? `<defs><linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="24" y1="78" x2="84" y2="32"><stop offset="0" stop-color="${g[0]}"/><stop offset="1" stop-color="${g[1]}"/></linearGradient></defs>` : ''}${paths}</svg>`;
}
function icon(px, mask = 'rounded', opts = {}) {
  if (opts.bg) return optIcon(MOWJ, px, mask === 'none' ? 'rounded' : mask, { themed: { bg: opts.bg, fg: opts.fg } });
  return optIcon(MOWJ, px, mask, opts);
}
