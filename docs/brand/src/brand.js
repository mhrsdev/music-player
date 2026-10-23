// Shared Mowj geometry (keep in sync with src/mark.svg and res/drawable/ic_launcher_foreground.xml).
const MOWJ_D = 'M2 35 C9 35 10 27 17 27 C24 27 24 35 31 35 C42 35 37 3 51 3 A12 12 0 0 1 63 15 A12 12 0 0 1 51 27 A6 6 0 0 1 45 21';
let _id = 0;
function mark(h, stroke) { // h = rendered height in px of the 40-unit-tall mark box
  const id = 'g' + (++_id);
  const grad = stroke === 'grad' || stroke === 'grad-dark';
  const [c0, c1] = stroke === 'grad-dark' ? ['#A6C8FF', '#7FD6CB'] : ['#2F5F95', '#0E8C84'];
  return `<svg class="mark" viewBox="-2 -1 69 40" height="${h}" width="${h*69/40}">${grad ? `<defs><linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="0" y1="35" x2="64" y2="3"><stop offset="0" stop-color="${c0}"/><stop offset="1" stop-color="${c1}"/></linearGradient></defs>` : ''}<path d="${MOWJ_D}" stroke="${grad ? `url(#${id})` : stroke}"/></svg>`;
}
// Adaptive icon: 108-unit canvas, mask shape over the 72-unit visible area. mask: circle|squircle|rounded|square|none
function icon(px, mask = 'rounded', opts = {}) {
  const id = 'i' + (++_id);
  const bg = opts.bg || `url(#${id}b)`, fg = opts.fg || '#F2FAFF';
  const clip = mask === 'circle' ? `<circle cx="54" cy="54" r="36"/>`
    : mask === 'squircle' ? `<path d="M54 18 C80 18 90 28 90 54 C90 80 80 90 54 90 C28 90 18 80 18 54 C18 28 28 18 54 18Z"/>`
    : mask === 'square' ? `<rect x="18" y="18" width="72" height="72"/>`
    : `<rect x="18" y="18" width="72" height="72" rx="16"/>`;
  const vb = mask === 'none' ? '0 0 108 108' : '18 18 72 72';
  return `<svg viewBox="${vb}" width="${px}" height="${px}"><defs><linearGradient id="${id}b" gradientUnits="userSpaceOnUse" x1="18" y1="18" x2="90" y2="90"><stop offset="0" stop-color="#24548C"/><stop offset="1" stop-color="#0E8C84"/></linearGradient><clipPath id="${id}c">${clip}</clipPath></defs>
  <g clip-path="${mask === 'none' ? '' : `url(#${id}c)`}"><rect width="108" height="108" fill="${bg}"/>
  <g transform="translate(26.94 38.42) scale(0.82)"><path d="${MOWJ_D}" fill="none" stroke="${fg}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/></g></g>
  ${opts.guides ? `<circle cx="54" cy="54" r="33" fill="none" stroke="#FF4FD8" stroke-width="0.4" stroke-dasharray="1.5 1.5"/><rect x="18" y="18" width="72" height="72" fill="none" stroke="#FF4FD8" stroke-width="0.4"/>` : ''}</svg>`;
}
