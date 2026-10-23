// Mowj logo directions. Every mark lives on the 108-unit adaptive-icon canvas (centre 54,54; safe circle r=33).
// Each option returns SVG for a given paint: { fill, stroke } colours (or gradient urls) so one geometry serves
// colour, shadow and monochrome renders. Paths are plain M/L/C/A/Z so they port 1:1 to VectorDrawable pathData.
const r2 = (n) => Math.round(n * 100) / 100;
function pts(list) { return list.map(([x, y], i) => (i ? 'L' : 'M') + r2(x) + ' ' + r2(y)).join(' '); }
function sine(x0, x1, yc, amp, period, phase = 0, step = 1.5) {
  const p = []; for (let x = x0; x <= x1 + 1e-6; x += step) p.push([x, yc - amp * Math.sin((x - x0) / period * 2 * Math.PI + phase)]);
  return p;
}
function band(x0, x1, yc, amp, period, th, phase = 0) { // closed sine ribbon of thickness th
  const top = sine(x0, x1, yc - th / 2, amp, period, phase), bot = sine(x0, x1, yc + th / 2, amp, period, phase).reverse();
  return pts(top.concat(bot)) + ' Z';
}
function capsule(x, y0, y1, w) { // vertical rounded bar, x = centre
  const r = w / 2; if (y1 - y0 < w) y1 = y0 + w;
  return `M${r2(x - r)} ${r2(y0 + r)} A${r} ${r} 0 0 1 ${r2(x + r)} ${r2(y0 + r)} L${r2(x + r)} ${r2(y1 - r)} A${r} ${r} 0 0 1 ${r2(x - r)} ${r2(y1 - r)} Z`;
}
function circlePath(cx, cy, r, ccw) { // full circle as two arcs; ccw for evenodd holes is not needed but kept explicit
  const s = ccw ? 0 : 1;
  return `M${r2(cx - r)} ${cy} A${r} ${r} 0 1 ${s} ${r2(cx + r)} ${cy} A${r} ${r} 0 1 ${s} ${r2(cx - r)} ${cy} Z`;
}
function arcPath(cx, cy, r, a0, a1) { // degrees, clockwise in screen space
  const p = (a) => [cx + r * Math.cos(a * Math.PI / 180), cy + r * Math.sin(a * Math.PI / 180)];
  const [x0, y0] = p(a0), [x1, y1] = p(a1);
  return `M${r2(x0)} ${r2(y0)} A${r} ${r} 0 ${a1 - a0 > 180 ? 1 : 0} 1 ${r2(x1)} ${r2(y1)}`;
}
function star8(cx, cy, R, rin) {
  const p = []; for (let k = 0; k < 16; k++) { const a = (k * 22.5 - 90) * Math.PI / 180, r = k % 2 ? rin : R; p.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]); }
  return pts(p) + ' Z';
}

// ---- A: Bars iwan. Seven Bars-seek-bar capsules hang from one lintel; their lower ends cut a pointed Persian arch.
function archBottom(dx) { // pointed arch opening: apex y=44 at dx=0, springs at |dx|=18 -> y=64
  const a = Math.min(Math.abs(dx) / 18, 1); return 44 + 20 * (1 - Math.sqrt(1 - a * a)) * 0.55 + 20 * a * 0.45;
}
const A = { id: 'A', name: 'Bars iwan', paths() {
  const pitch = 8, w = 5.6, top = 30, xs = [-3, -2, -1, 0, 1, 2, 3].map((i) => 54 + i * pitch);
  const d = xs.map((x) => { const dx = x - 54; return capsule(x, top, Math.abs(dx) >= 20 ? 78 : archBottom(dx), w); }).join(' ');
  return [{ d, fill: true }];
} };

// ---- B: Geometric meem. Round head on the baseline, connector, vertical tail: isolated م.
const B = { id: 'B', name: 'Geometric meem', paths() {
  return [{ d: 'M57 55.5 L42 55.5 C38 55.5 36.5 58.5 36.5 63 L36.5 79', stroke: 8.5 },
          { d: circlePath(63.5, 45.5, 10), stroke: 8.5 }];
} };

// ---- C: Ripple. A bright source dot and two broken rings, gaps aligned like a play notch / a groove.
const C = { id: 'C', name: 'Ripple', paths() {
  return [{ d: circlePath(54, 54, 7.5), fill: true, accent: true },
          { d: arcPath(54, 54, 17, -20, 290), stroke: 6.5 },
          { d: arcPath(54, 54, 27, -32, 302), stroke: 6.5 }];
} };

// ---- D: M-wave monogram. Two stems joined by one sine: M for Mowj, the middle is the wave.
const D = { id: 'D', name: 'M-wave monogram', paths() {
  return [{ d: 'M31 75 L31 38 C35.5 38 37.5 61 43 61 C48.5 61 49.5 45 54 45 C58.5 45 59.5 61 65 61 C70.5 61 72.5 38 77 38 L77 75', stroke: 8.5 }];
} };

// ---- E: Girih star. A Persian 8-point star tile; its heart is cut by five equaliser bars.
const E = { id: 'E', name: 'Girih star', paths() {
  const hs = [9, 16, 23, 16, 9], xs = [-2, -1, 0, 1, 2].map((i) => 54 + i * 6.4);
  const holes = xs.map((x, i) => capsule(x, 54 - hs[i] / 2, 54 + hs[i] / 2, 4.2)).join(' ');
  return [{ d: star8(54, 54, 29, 22.2) + ' ' + holes, fill: true, evenodd: true, round: 3 }];
} };

// ---- F: Drop. A water drop whose belly carries one sound wave.
const F = { id: 'F', name: 'Drop wave', paths() {
  const drop = 'M54 24 C60 34 72 44 72 59 C72 69.5 64 77.5 54 77.5 C44 77.5 36 69.5 36 59 C36 44 48 34 54 24 Z';
  return [{ d: drop + ' ' + band(36.2, 71.8, 61, 4.2, 17.8, 5.2), fill: true, evenodd: true }];
} };

// ---- G: Iwan wave. A pointed Persian arch (the iwan of a Persian courtyard) with two waves crossing its threshold.
const G = { id: 'G', name: 'Iwan wave', paths() {
  const arch = 'M33 78 L33 52 C33 40 43 32 54 24 C65 32 75 40 75 52 L75 78 Z';
  return [{ d: arch + ' ' + band(33, 75, 57, 3.2, 21, 4.6) + ' ' + band(33, 75, 67.5, 3.2, 21, 4.6), fill: true, evenodd: true }];
} };

const ROUND1 = [A, B, C, D, E, F, G];
for (const o of ROUND1) o.scale = 0.84;

// ===== Bilingual family: Latin M + Persian م in one mark (owner direction, round 2) =====
// Persian isolated م = a closed round head on the baseline with a tail falling from its lower left.
function mPoly(x, y, w, h, t) { // solid geometric sans M outline (for negative-space cut-outs)
  const v = h * 0.62;
  return pts([[x, y + h], [x, y], [x + t * 1.1, y], [x + w / 2, y + v], [x + w - t * 1.1, y], [x + w, y], [x + w, y + h], [x + w - t, y + h],
    [x + w - t, y + t * 2.3], [x + w / 2 + t * 0.35, y + v + t * 1.15], [x + w / 2 - t * 0.35, y + v + t * 1.15], [x + t, y + t * 2.3], [x + t, y + h]]) + ' Z';
}
const M1 = { id: 'M1', name: 'Valley bowl', paths() { // M's valley is م's closed head; its tail drops from the valley
  const cx = 54, cy = 57, r = 7.2;
  return [{ d: `M31 70 L31 39 L${r2(cx - r * 0.72)} ${r2(cy - r * 0.69)}`, stroke: 7.5 },
          { d: `M${r2(cx + r * 0.72)} ${r2(cy - r * 0.69)} L77 39 L77 70`, stroke: 7.5 },
          { d: circlePath(cx, cy, r), stroke: 7.5 },
          { d: `M${r2(cx - r * 0.7)} ${r2(cy + r * 0.72)} C${r2(cx - r * 0.9)} ${cy + 12} ${cx - 7} ${cy + 18} ${cx - 8.5} ${cy + 22}`, stroke: 7.5 }];
} };
const M2 = { id: 'M2', name: 'Shared wave line', paths() { // M (LTR) and م (RTL) stand on one wave that links them
  const o = (x, y) => `${r2(x + 1.5)} ${r2(y - 4)}`; // optical centring offset
  const hx = 70.5, hy = 55, r = 7.6;
  return [{ d: `M${o(26, 62)} L${o(26, 39)} L${o(36, 53)} L${o(46, 39)} L${o(46, 62)} C${o(51, 62)} ${o(52.5, 67.5)} ${o(57.5, 67.5)} C${o(61.5, 67.5)} ${o(62.5, 62.6)} ${o(hx - 0.6, hy + r)}`, stroke: 7 },
          { d: circlePath(hx + 1.5, hy - 4, r), stroke: 7 },
          { d: `M${o(hx - r * 0.72, hy + r * 0.7)} C${o(hx - 7.6, hy + 12)} ${o(hx - 8.4, hy + 18)} ${o(hx - 8, hy + 24)}`, stroke: 7 }];
} };
const M3 = { id: 'M3', name: 'Arch-M, meem foot', paths() { // rounded M (two wave crests); its last stem is م's tail, the head sits at its foot
  return [{ d: 'M28 70 L28 49 C28 37.5 43 37.5 43 49 L43 60 M43 49 C43 37.5 58 37.5 58 49 L58 80', stroke: 7.5 },
          { d: circlePath(68, 61, 7), stroke: 7.5 }];
} };
const M4 = { id: 'M4', name: 'M inside م', paths() { // the whole silhouette is م (round head + tail); a sans M is cut from the head
  const cx = 59.5, cy = 46, R = 17.5;
  return [{ d: circlePath(cx, cy, R) + ' ' + mPoly(cx - 9.6, cy - 7.4, 19.2, 14.6, 4.3), fill: true, evenodd: true, round: 1.4 },
          { d: `M${r2(cx - R * 0.74)} ${r2(cy + R * 0.6)} C${cx - 17} ${cy + 20} ${cx - 17} ${cy + 28} ${cx - 20} ${cy + 32} C${cx - 22} ${cy + 34.5} ${cx - 26} ${cy + 34.5} ${cx - 28} ${cy + 33}`, stroke: 8.5 }];
} };
const M5 = { id: 'M5', name: 'Sans M, meem right half', paths() { // left stem + diagonal = M; valley drops as م's tail; the bowl is the right half
  return [{ d: 'M30 69 L30 38 L49 61.5 C49 68 48 74 46 80', stroke: 7.5 },
          { d: circlePath(62.5, 50.5, 11.5), stroke: 7.5 }];
} };
const FAMILY = [M1, M2, M3, M4, M5];
const OPTIONS = FAMILY.concat([C]);

// Render one option's mark. paint: { color, accent, shadow }
function markSVG(opt, paint) {
  const sc = opt.scale || 1, tr = sc === 1 ? '' : ` transform="translate(54 54) scale(${sc}) translate(-54 -54)"`;
  return `<g${tr}>` + opt.paths().map((p) => {
    const col = p.accent && paint.accent ? paint.accent : paint.color;
    const fr = p.evenodd ? ' fill-rule="evenodd"' : '';
    if (p.fill) return `<path d="${p.d}" fill="${col}"${fr}${p.round ? ` stroke="${col}" stroke-width="${p.round}" stroke-linejoin="round"` : ''}/>`;
    return `<path d="${p.d}" fill="none" stroke="${col}" stroke-width="${p.stroke}" stroke-linecap="round" stroke-linejoin="round"/>`;
  }).join('') + '</g>';
}
let _oid = 0;
const MASKS = {
  circle: '<circle cx="54" cy="54" r="36"/>',
  squircle: '<path d="M54 18 C80 18 90 28 90 54 C90 80 80 90 54 90 C28 90 18 80 18 54 C18 28 28 18 54 18Z"/>',
  rounded: '<rect x="18" y="18" width="72" height="72" rx="16"/>',
};
// Full-colour adaptive icon: lapis->turquoise base, soft radial glow, mark with foam gradient + soft drop shadow.
function optIcon(opt, px, mask = 'circle', o = {}) {
  const id = 'o' + (++_oid);
  const themed = o.themed; // {bg, fg}
  const defs = `<defs><clipPath id="${id}c">${MASKS[mask] || ''}</clipPath>
    <linearGradient id="${id}b" gradientUnits="userSpaceOnUse" x1="22" y1="18" x2="86" y2="90"><stop offset="0" stop-color="#1D4A80"/><stop offset="0.55" stop-color="#1B6787"/><stop offset="1" stop-color="#0E8C84"/></linearGradient>
    <radialGradient id="${id}g" gradientUnits="userSpaceOnUse" cx="40" cy="30" r="52"><stop offset="0" stop-color="#9EF2E7" stop-opacity="0.38"/><stop offset="1" stop-color="#9EF2E7" stop-opacity="0"/></radialGradient>
    <linearGradient id="${id}f" gradientUnits="userSpaceOnUse" x1="0" y1="26" x2="0" y2="80"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#CDEFEA"/></linearGradient></defs>`;
  const body = themed
    ? `<rect width="108" height="108" fill="${themed.bg}"/>${markSVG(opt, { color: themed.fg })}`
    : `<rect width="108" height="108" fill="url(#${id}b)"/><rect width="108" height="108" fill="url(#${id}g)"/>
       <g transform="translate(0 1.8)" opacity="0.28">${markSVG(opt, { color: '#05182C' })}</g>
       ${markSVG(opt, { color: `url(#${id}f)`, accent: '#9EF2E7' })}`;
  const full = mask === 'none';
  const guides = o.guides ? '<rect x="18" y="18" width="72" height="72" fill="none" stroke="#FF4FD8" stroke-width="0.4"/><circle cx="54" cy="54" r="33" fill="none" stroke="#FF4FD8" stroke-width="0.4" stroke-dasharray="1.5 1.5"/>' : '';
  return `<svg viewBox="${full ? '0 0 108 108' : '18 18 72 72'}" width="${px}" height="${px}">${defs}<g${full ? '' : ` clip-path="url(#${id}c)"`}>${body}</g>${guides}</svg>`;
}
// Mark alone (status bar / one colour): crop to the content box.
function optMark(opt, px, color) {
  return `<svg viewBox="22 22 64 64" width="${px}" height="${px}">${markSVG(opt, { color })}</svg>`;
}
for (const o of FAMILY) o.scale = 0.84; // keeps every corner inside the 66dp safe circle
const RECOMMENDED = 'M2';

// Status-bar icon (res/drawable/ic_stat_playback.xml): 24-unit box, mark fitted 22 wide.
function statIcon(opt, px, color) {
  return `<svg viewBox="0 0 24 24" width="${px}" height="${px}"><g transform="translate(-7.93 -8.47) scale(0.3722)">${markSVG(Object.assign({}, opt, { scale: 1 }), { color })}</g></svg>`;
}
