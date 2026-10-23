/* Mockup helpers: generative artwork, avatars, wave paths, M3 Expressive shapes, chrome. */
(function () {
  let uid = 0;
  const id = (p) => `${p}${++uid}`;
  function rng(seed) { let s = seed >>> 0 || 1; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }
  const ZW = '‌';
  const grainDef = (g) => `<filter id="${g}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="2.2" numOctaves="2" seed="7" stitchTiles="stitch"/><feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .9 -.35"/></filter>`;
  const grainRect = (g, o = .16) => `<rect width="100" height="100" filter="url(#${g})" opacity="${o}" style="mix-blend-mode:soft-light"/>`;
  const T = (x, y, s, txt, o = {}) => `<text x="${x}" y="${y}" font-size="${s}" fill="${o.fill || '#fff'}" font-weight="${o.w || 700}" text-anchor="${o.fa ? ({ start: 'end', end: 'start' }[o.anchor || 'start'] || o.anchor) : (o.anchor || 'start')}" ${o.fa ? 'direction="rtl" font-family="Vazirmatn"' : 'direction="ltr" font-family="GSF"'} letter-spacing="${o.ls || 0}" opacity="${o.op || 1}" style="font-variation-settings:'wdth' ${o.wdth || 100}, 'ROND' ${o.rond || 0}">${txt}</text>`;

  // ---------- Covers ----------
  const ART = {
    'without-you': { t: 'Without You', a: 'NEFFEX', dyn: ['#A9D4F5', '#00344D', '#1A4B66', '#CDE8FF', '#1f5f86'], draw(g) {
      const a = id('a'), b = id('b'), r = rng(3); let stars = '';
      for (let i = 0; i < 40; i++) stars += `<circle cx="${r() * 100}" cy="${r() * 55}" r="${r() * .35 + .1}" fill="#dff1ff" opacity="${r() * .7 + .2}"/>`;
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#081a33"/><stop offset=".62" stop-color="#11486c"/><stop offset="1" stop-color="#1f7584"/></linearGradient>
      <radialGradient id="${b}" cx=".66" cy=".34" r=".42"><stop offset="0" stop-color="#bfe6ff" stop-opacity=".55"/><stop offset="1" stop-color="#bfe6ff" stop-opacity="0"/></radialGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><rect width="100" height="100" fill="url(#${b})"/>${stars}
      <circle cx="66" cy="34" r="11.5" fill="#eef8ff"/><circle cx="62.5" cy="31" r="2.2" fill="#d4e6f3"/><circle cx="69" cy="38.5" r="1.6" fill="#d4e6f3"/><circle cx="70" cy="29" r="1" fill="#d4e6f3"/>
      <path d="M0 76 Q 22 64 46 72 T 100 68 V100 H0Z" fill="#0c2c41"/><path d="M0 86 Q 30 76 62 86 T 100 82 V100 H0Z" fill="#05141f"/>
      ${T(8, 12.5, 4.6, 'NEFFEX', { ls: 1.6, op: .85 })}${T(8, 94, 7, 'WITHOUT YOU', { w: 800, wdth: 62, ls: .4 })}${grainRect(g)}`; } },

    'electric-dusk': { t: 'Electric Dusk', a: 'Halcyon Drift', dyn: ['#FFB0CD', '#5E1133', '#7B2950', '#FFD9E4', '#a3346c'], draw(g) {
      const a = id('a'), s = id('s'), m = id('m'); let grid = '', stripes = '';
      for (let i = 0; i < 9; i++) { const y = 62 + Math.pow(i / 8, 1.8) * 38; grid += `<line x1="0" x2="100" y1="${y}" y2="${y}" stroke="#ff5fa2" stroke-width=".35" opacity=".7"/>`; }
      for (let i = -8; i <= 8; i++) grid += `<line x1="${50 + i * 4}" y1="62" x2="${50 + i * 22}" y2="100" stroke="#ff5fa2" stroke-width=".35" opacity=".6"/>`;
      for (let i = 0; i < 6; i++) stripes += `<rect x="0" y="${50 + i * 2.2 + i * i * .25}" width="100" height="${.5 + i * .35}" fill="#000"/>`;
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#170935"/><stop offset=".45" stop-color="#5d1b6b"/><stop offset=".62" stop-color="#f0567a"/></linearGradient>
      <linearGradient id="${s}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffe28a"/><stop offset="1" stop-color="#ff4f8b"/></linearGradient>
      <mask id="${m}"><rect width="100" height="100" fill="#fff"/>${stripes}</mask>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><circle cx="50" cy="44" r="22" fill="url(#${s})" mask="url(#${m})"/>
      <rect y="62" width="100" height="38" fill="#12062a"/>${grid}
      ${T(50, 12, 4.2, 'HALCYON DRIFT', { anchor: 'middle', ls: 1.8, op: .9 })}${T(50, 93, 6.4, 'Electric Dusk', { anchor: 'middle', w: 500, rond: 100, fill: '#ffd1e4' })}${grainRect(g)}`; } },

    'tehran-nights': { fa: true, t: `شب${ZW}های تهران`, a: `آوا نیک${ZW}زاد`, dyn: ['#E3B6FF', '#4A1A6B', '#5A2F78', '#F3DAFF', '#7c3a9c'], draw(g) {
      const a = id('a'), r = rng(11); let stars = '', city = '', win = '';
      for (let i = 0; i < 55; i++) stars += `<circle cx="${r() * 100}" cy="${r() * 45}" r="${r() * .3 + .08}" fill="#fff" opacity="${r() * .8 + .2}"/>`;
      let x = 0; while (x < 100) { const w = 3 + r() * 6, h = 8 + r() * 16; city += `<rect x="${x}" y="${84 - h}" width="${w}" height="${h + 20}" fill="#0b0718"/>`;
        for (let k = 0; k < 4; k++) if (r() > .45) win += `<rect x="${x + .8 + r() * (w - 2)}" y="${84 - h + 2 + r() * (h - 3)}" width=".8" height=".9" fill="#ffcf7a" opacity="${.5 + r() * .5}"/>`; x += w + .4; }
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0c0a2c"/><stop offset=".5" stop-color="#3b1c6b"/><stop offset=".86" stop-color="#d3508a"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/>${stars}
      <path d="M0 70 L12 58 L20 63 L33 47 L41 55 L52 44 L63 56 L74 49 L86 60 L100 52 V100 H0Z" fill="#2b1650"/>
      <path d="M30.6 49.6 L33 47 L35.4 49.8 L34 49.2 L33 50.4 L31.8 49.4Z M49.8 46.4 L52 44 L54.6 47 L53 46.4 L52 47.6 L50.8 46.6Z M72 51 L74 49 L76 51.2 L74.8 50.8 L74 51.8Z" fill="#e9d8ff" opacity=".85"/>
      ${city}<rect x="69.6" y="30" width=".8" height="12" fill="#0b0718"/><ellipse cx="70" cy="44.5" rx="3.6" ry="2.4" fill="#0b0718"/><path d="M68.6 46 L67.4 86 H72.6 L71.4 46Z" fill="#0b0718"/>
      <circle cx="70" cy="30" r=".55" fill="#ff4d6d"/>${win}<rect y="84" width="100" height="16" fill="#08051a"/>
      ${T(92, 17, 10, `شب${ZW}های تهران`, { fa: true, anchor: 'end', w: 800, fill: '#ffe6f2' })}${T(92, 24.5, 4.6, `آوا نیک${ZW}زاد`, { fa: true, anchor: 'end', w: 500, fill: '#ffd0e6', op: .85 })}${grainRect(g, .2)}`; } },

    'koucheh-bagh': { fa: true, t: `کوچه${ZW}باغ`, a: 'گروه دریاچه', dyn: ['#8FD3DD', '#003640', '#1F4D86', '#D7E8FF', '#2a5db0'], draw(g) {
      let tiles = '';
      const star = (cx, cy, R, fill, stroke, sw) => { let p = ''; for (let i = 0; i < 16; i++) { const rr = i % 2 ? R * .62 : R; const t = Math.PI * 2 * i / 16 - Math.PI / 2; p += `${i ? 'L' : 'M'}${(cx + rr * Math.cos(t)).toFixed(2)} ${(cy + rr * Math.sin(t)).toFixed(2)}`; } return `<path d="${p}Z" fill="${fill}" stroke="${stroke}" stroke-width="${sw}" stroke-linejoin="round"/>`; };
      for (let gx = -1; gx < 6; gx++) for (let gy = -1; gy < 5; gy++) { const cx = gx * 20 + (gy % 2 ? 10 : 0), cy = gy * 17.3;
        tiles += star(cx, cy, 9.4, '#16408a', '#e2b64a', .7) + star(cx, cy, 4.6, '#3fc1c9', '#0b2a5c', .5) + `<circle cx="${cx}" cy="${cy}" r="1.1" fill="#f2e6cc"/>`; }
      return `<defs>${grainDef(g)}</defs><rect width="100" height="100" fill="#0e2b62"/>${tiles}
      <rect y="72" width="100" height="28" fill="#f2e6cc"/><rect y="72" width="100" height="1.4" fill="#e2b64a"/>
      ${T(50, 87.5, 10.5, `کوچه${ZW}باغ`, { fa: true, anchor: 'middle', w: 900, fill: '#0e2b62' })}${T(50, 95.5, 4.2, 'گروه دریاچه', { fa: true, anchor: 'middle', w: 600, fill: '#7a5a1c' })}${grainRect(g, .22)}`; } },

    'anar': { fa: true, t: 'انار', a: 'سامان آرین', dyn: ['#FFB3B1', '#5F1416', '#7C1E24', '#FFDAD8', '#9e1a26'], draw(g) {
      const a = id('a'), f = id('f'), r = rng(5); let seeds = '', dots = '';
      for (let i = 0; i < 16; i++) { const x = 64 + r() * 22, y = 80 + r() * 13; seeds += `<ellipse cx="${x}" cy="${y}" rx="1.5" ry="2" transform="rotate(${r() * 60 - 30} ${x} ${y})" fill="#ff4a5e"/><circle cx="${x - .4}" cy="${y - .6}" r=".45" fill="#fff" opacity=".7"/>`; }
      for (let i = 0; i < 36; i++) { const t = i / 36 * Math.PI * 2; dots += `<circle cx="${52 + 36 * Math.cos(t)}" cy="${54 + 36 * Math.sin(t)}" r=".55" fill="#e9b85a" opacity=".8"/>`; }
      return `<defs><radialGradient id="${a}" cx=".5" cy=".5" r=".75"><stop offset="0" stop-color="#9e1a26"/><stop offset="1" stop-color="#2a040b"/></radialGradient>
      <radialGradient id="${f}" cx=".38" cy=".35" r=".8"><stop offset="0" stop-color="#ff5468"/><stop offset=".55" stop-color="#c41229"/><stop offset="1" stop-color="#5e0614"/></radialGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><circle cx="52" cy="54" r="36" fill="none" stroke="#e9b85a" stroke-width=".35" opacity=".7"/>${dots}
      <path d="M46.5 30 L48 24 L50.5 28 L52 23 L53.5 28 L56 24 L57.5 30Z" fill="#6e0a18"/>
      <circle cx="52" cy="54" r="25" fill="url(#${f})"/><ellipse cx="44" cy="44" rx="6" ry="3.5" fill="#fff" opacity=".18" transform="rotate(-35 44 44)"/>${seeds}
      ${T(90, 18, 13, 'انار', { fa: true, anchor: 'end', w: 900, fill: '#f3c97a' })}${T(90, 25.5, 4.4, 'سامان آرین', { fa: true, anchor: 'end', w: 500, fill: '#f3c97a', op: .85 })}${grainRect(g, .2)}`; } },

    'baran': { fa: true, t: `باران که می${ZW}آید`, a: 'نیلوفر', dyn: ['#86D5C9', '#003731', '#1D4F49', '#A2F2E5', '#1f6b62'], draw(g) {
      const a = id('a'), r = rng(9); let rain = '', rip = '';
      for (let i = 0; i < 90; i++) { const x = r() * 110 - 5, y = r() * 80, l = 4 + r() * 7; rain += `<line x1="${x}" y1="${y}" x2="${x - l * .18}" y2="${y + l}" stroke="#d4f5ef" stroke-width="${.2 + r() * .25}" opacity="${.15 + r() * .4}" stroke-linecap="round"/>`; }
      for (let i = 0; i < 7; i++) { const x = 10 + r() * 80, y = 82 + r() * 14; for (let k = 1; k < 4; k++) rip += `<ellipse cx="${x}" cy="${y}" rx="${k * 2.4}" ry="${k * .6}" fill="none" stroke="#d4f5ef" stroke-width=".25" opacity="${.5 / k}"/>`; }
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2=".3" y2="1"><stop offset="0" stop-color="#091c1b"/><stop offset=".7" stop-color="#1b5450"/><stop offset="1" stop-color="#2c7a70"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><circle cx="30" cy="30" r="20" fill="#2f8f82" opacity=".22"/><circle cx="74" cy="50" r="14" fill="#9be3d5" opacity=".1"/>${rain}${rip}
      ${T(91, 58, 9, `باران`, { fa: true, anchor: 'end', w: 800, fill: '#e8fffb' })}${T(91, 68, 6.2, `که می${ZW}آید`, { fa: true, anchor: 'end', w: 400, fill: '#c9f1ea' })}${T(91, 75, 3.8, 'نیلوفر', { fa: true, anchor: 'end', w: 600, fill: '#9be3d5' })}${grainRect(g)}`; } },

    'topography': { t: 'Cold Light', a: 'Mira Vale', dyn: ['#B8F27A', '#1F3700', '#2E4F0A', '#D3FF9C', '#3e6e12'], draw(g) {
      let lines = '';
      const blob = (cx, cy, base, n, ph) => { for (let k = 1; k <= n; k++) { let p = ''; for (let i = 0; i <= 90; i++) { const t = i / 90 * Math.PI * 2; const rr = base * k * (1 + .16 * Math.sin(3 * t + ph + k * .3) + .08 * Math.sin(5 * t - ph * 2 + k * .5)); p += `${i ? 'L' : 'M'}${(cx + rr * Math.cos(t)).toFixed(2)} ${(cy + rr * Math.sin(t) * .8).toFixed(2)}`; }
        lines += `<path d="${p}Z" fill="none" stroke="#b6ff5c" stroke-width="${k === 1 ? .7 : .4}" opacity="${Math.max(.18, 1 - k * .085)}"/>`; } };
      blob(36, 40, 4.2, 12, .4); blob(78, 74, 3.6, 8, 2.1);
      return `<defs>${grainDef(g)}</defs><rect width="100" height="100" fill="#0a0e0b"/>${lines}<circle cx="36" cy="40" r="1.1" fill="#b6ff5c"/>
      ${T(8, 92, 7.5, 'cold light', { w: 450, fill: '#e6ffd0', rond: 100 })}${T(92, 12, 4, 'MIRA VALE', { anchor: 'end', ls: 1.6, fill: '#b6ff5c' })}${grainRect(g, .2)}`; } },

    'salt-static': { t: 'Salt & Static', a: 'The Low Tides', dyn: ['#FFB68F', '#552000', '#6F3A1F', '#FFDBCB', '#c4562a'], draw(g) {
      const a = id('a'); let dots = '';
      for (let y = 2; y < 100; y += 3.2) for (let x = 1 + (Math.round(y / 3.2) % 2) * 1.6; x < 100; x += 3.2) { const v = (y / 100) * .9 + .25 * Math.sin(x / 9 + y / 15); const rr = Math.max(0, Math.min(1.55, v * 1.7 - .2)); if (rr > .08) dots += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${rr.toFixed(2)}" fill="#0e1a4a"/>`; }
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffb35c"/><stop offset=".5" stop-color="#ff6a3d"/><stop offset="1" stop-color="#3a52d8"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/>${dots}<rect x="7" y="7" width="47" height="13" fill="#fff4e8"/>
      ${T(9.5, 16.4, 6.2, 'SALT &amp; STATIC', { w: 850, wdth: 50, fill: '#0e1a4a' })}${T(93, 94, 4, 'THE LOW TIDES', { anchor: 'end', ls: 1.4, fill: '#fff4e8' })}${grainRect(g)}`; } },

    'cold': { t: 'Cold', a: 'NEFFEX', dyn: ['#B3CDEB', '#1B3249', '#34495F', '#D3E4FF', '#4b6d93'], draw(g) {
      const a = id('a'), r = rng(21); let sh = '';
      for (let i = 0; i < 14; i++) { const x = r() * 100, y = r() * 70; sh += `<path d="M${x} ${y} L${x + 6 + r() * 14} ${y + r() * 10 - 5} L${x + r() * 8} ${y + 8 + r() * 14}Z" fill="${r() > .5 ? '#ffffff' : '#bcd6ef'}" opacity="${.25 + r() * .45}"/>`; }
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f2f8ff"/><stop offset="1" stop-color="#86acd2"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/>${sh}${T(5, 95, 40, 'COLD', { w: 900, wdth: 25, fill: '#0d2238', ls: -.5 })}${T(93, 11, 4.4, 'NEFFEX', { anchor: 'end', ls: 1.6, fill: '#0d2238' })}${grainRect(g, .22)}`; } },

    'grateful': { t: 'Grateful', a: 'NEFFEX', dyn: ['#FFB870', '#4A2800', '#6A3C00', '#FFDDB8', '#b8730f'], draw(g) {
      const a = id('a'); let rays = '';
      for (let i = 0; i < 36; i++) rays += `<path d="M50 62 L${50 + 90 * Math.cos(i / 36 * Math.PI * 2)} ${62 + 90 * Math.sin(i / 36 * Math.PI * 2)} L${50 + 90 * Math.cos((i + .5) / 36 * Math.PI * 2)} ${62 + 90 * Math.sin((i + .5) / 36 * Math.PI * 2)}Z" fill="#fff3c4" opacity=".28"/>`;
      return `<defs><radialGradient id="${a}" cx=".5" cy=".62" r=".8"><stop offset="0" stop-color="#ffe07a"/><stop offset=".6" stop-color="#ff9a3d"/><stop offset="1" stop-color="#e2562b"/></radialGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/>${rays}<circle cx="50" cy="62" r="13" fill="#fff6d8"/>
      ${T(50, 24, 11, 'GRATEFUL', { anchor: 'middle', w: 900, wdth: 70, fill: '#2a1300' })}${T(50, 31, 4, 'NEFFEX', { anchor: 'middle', ls: 1.8, fill: '#2a1300' })}${grainRect(g)}`; } },

    'fight-back': { t: 'Fight Back', a: 'NEFFEX', dyn: ['#FFB4AA', '#690003', '#8C1D18', '#FFDAD5', '#a82219'], draw(g) {
      let sl = ''; for (let i = 0; i < 7; i++) sl += `<path d="M${-20 + i * 19} 100 L${10 + i * 19} 0 L${15 + i * 19} 0 L${-15 + i * 19} 100Z" fill="#e3261b" opacity="${i % 2 ? .9 : .45}"/>`;
      return `<defs>${grainDef(g)}</defs><rect width="100" height="100" fill="#0b0b0c"/>${sl}<rect x="0" y="40" width="100" height="22" fill="#0b0b0c"/>
      ${T(50, 57.5, 17, 'FIGHT BACK', { anchor: 'middle', w: 900, wdth: 40, fill: '#ffffff' })}${T(50, 92, 4, 'NEFFEX', { anchor: 'middle', ls: 2, fill: '#fff' })}${grainRect(g, .25)}`; } },

    'desert-bloom': { t: 'Desert Bloom', a: 'Kaveh Rostami', dyn: ['#FFB59A', '#5C1B05', '#7A3A2A', '#FFDBD0', '#a8513a'], draw(g) {
      const a = id('a');
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2e2363"/><stop offset=".55" stop-color="#e9866b"/><stop offset=".8" stop-color="#f7cf8e"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><circle cx="66" cy="50" r="7" fill="#fff0c9"/>
      <path d="M0 64 C 20 56, 40 70, 60 62 S 90 58, 100 62 V100 H0Z" fill="#d07a52"/><path d="M0 74 C 25 66, 45 80, 70 72 S 95 70, 100 74 V100 H0Z" fill="#a24c3c"/><path d="M0 86 C 30 78, 55 92, 100 82 V100 H0Z" fill="#5a2a3c"/>
      ${T(8, 14, 4.2, 'KAVEH ROSTAMI', { ls: 1.5, fill: '#fff0c9' })}${T(8, 22, 7.5, 'Desert Bloom', { w: 500, fill: '#fff0c9', rond: 100 })}${grainRect(g)}`; } },

    'paper-planes': { t: 'Paper Planes', a: 'Juniper Lane', dyn: ['#FFB4A5', '#5D1710', '#7A2E23', '#FFDAD3', '#c45a46'], draw(g) {
      return `<defs>${grainDef(g)}</defs><rect width="100" height="100" fill="#efe5d3"/>
      <path d="M18 62 L70 30 L48 70Z" fill="#f2735c"/><path d="M48 70 L70 30 L52 58Z" fill="#c9503c"/><path d="M52 58 L70 30 L58 76Z" fill="#1d2b4a"/>
      <path d="M62 22 L84 12 L76 26Z" fill="#e7b33d"/><path d="M76 26 L84 12 L78 30Z" fill="#b8862a"/><path d="M10 86 Q 40 70 66 78" fill="none" stroke="#1d2b4a" stroke-width=".5" stroke-dasharray="1.6 1.6"/>
      ${T(8, 14, 4.2, 'JUNIPER LANE', { ls: 1.5, fill: '#1d2b4a' })}${T(92, 93, 7, 'paper planes', { anchor: 'end', w: 500, fill: '#1d2b4a', rond: 100 })}${grainRect(g, .3)}`; } },

    'mercury': { t: 'Mercury', a: 'Oslo Haze', dyn: ['#C3C6CF', '#2C3138', '#43474E', '#DFE2EB', '#5d6470'], draw(g) {
      const a = id('a'), b = id('b');
      return `<defs><radialGradient id="${a}" cx=".4" cy=".35" r=".7"><stop offset="0" stop-color="#ffffff"/><stop offset=".3" stop-color="#9aa3b4"/><stop offset=".55" stop-color="#2b3140"/><stop offset=".75" stop-color="#c7cfdd"/><stop offset="1" stop-color="#1a1d24"/></radialGradient>
      <linearGradient id="${b}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#16181d"/><stop offset="1" stop-color="#2a2f3a"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${b})"/><circle cx="52" cy="50" r="30" fill="url(#${a})"/><ellipse cx="44" cy="40" rx="8" ry="4" fill="#fff" opacity=".5" transform="rotate(-30 44 40)"/>
      ${T(8, 93, 7, 'MERCURY', { w: 300, ls: 3, fill: '#dfe2eb' })}${T(92, 12, 4, 'OSLO HAZE', { anchor: 'end', ls: 1.6, fill: '#9aa3b4' })}${grainRect(g)}`; } },

    // playlist-type covers (generated mixes)
    'mix-2016': { t: 'On repeat, 2016', a: '42 songs', dyn: ['#9CD0FF', '#003352', '#1E4A6B', '#CDE5FF', '#235a85'], draw(g) {
      const a = id('a');
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0d6e7a"/><stop offset="1" stop-color="#ff7aa8"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><circle cx="78" cy="22" r="30" fill="#ffd36e" opacity=".85" style="mix-blend-mode:multiply"/>
      ${T(6, 88, 34, '2016', { w: 900, wdth: 40, fill: '#fff', ls: -1 })}${T(8, 12, 4.4, 'ON REPEAT', { ls: 1.6, fill: '#fff' })}${grainRect(g)}`; } },
    'mix-summer': { t: 'Summer of 2019', a: '31 songs', dyn: ['#FFD27A', '#3F2E00', '#5B4300', '#FFE1A6', '#a07a14'], draw(g) {
      let st = ''; for (let i = 0; i < 9; i++) st += `<rect y="${i * 12}" width="100" height="6" fill="#0f8a8a" opacity=".85"/>`;
      return `<defs>${grainDef(g)}</defs><rect width="100" height="100" fill="#ffd451"/>${st}<circle cx="50" cy="50" r="27" fill="#ff5a3c"/>
      ${T(50, 56, 15, 'SUMMER', { anchor: 'middle', w: 900, wdth: 50, fill: '#fff3d6' })}${T(50, 66, 6, "'19", { anchor: 'middle', w: 800, fill: '#fff3d6' })}${grainRect(g, .25)}`; } },
    'mix-fa': { fa: true, t: 'دهه‌ی هشتاد', a: '۵۸ آهنگ', dyn: ['#F5BE6F', '#462A00', '#653F00', '#FFDDB4', '#a96a13'], draw(g) {
      const a = id('a');
      return `<defs><linearGradient id="${a}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a1f12"/><stop offset="1" stop-color="#8a3f16"/></linearGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><rect x="14" y="30" width="72" height="44" rx="4" fill="#f1dcb6"/><rect x="22" y="38" width="56" height="16" rx="2" fill="#d9573a"/>
      <circle cx="36" cy="62" r="5" fill="#3a1f12"/><circle cx="64" cy="62" r="5" fill="#3a1f12"/><circle cx="36" cy="62" r="2" fill="#f1dcb6"/><circle cx="64" cy="62" r="2" fill="#f1dcb6"/>
      ${T(50, 49.5, 7, 'دهه‌ی ۸۰', { fa: true, anchor: 'middle', w: 900, fill: '#fff3df' })}${T(50, 91, 5, 'نوستالژی', { fa: true, anchor: 'middle', w: 600, fill: '#f1dcb6' })}${grainRect(g, .25)}`; } },
  };

  // ---------- Artist avatars (silhouette + rim light, generated) ----------
  const ARTISTS = {
    'neffex': ['#1f6fb2', '#08121f', '#9fd3ff', 'cap'], 'halcyon': ['#c43c8a', '#1a0826', '#ffb0d6', 'duo'],
    'ava': ['#7d3cc4', '#120826', '#e5c4ff', 'long'], 'mira': ['#3e8f1a', '#060d05', '#c8ff8c', 'short'],
    'tides': ['#e2683a', '#130a1f', '#ffc9a8', 'duo'], 'saman': ['#b0182e', '#1a0307', '#ffb3b1', 'short'],
    'kaveh': ['#c98a3a', '#1d1206', '#ffd9a0', 'beard'], 'juniper': ['#2f8f82', '#071a17', '#9ef2e7', 'bun'],
    'oslo': ['#5d6a80', '#0b0d12', '#dfe6f5', 'hood'], 'niloufar': ['#2c7a70', '#06120f', '#a2f2e5', 'long'], 'daryacheh': ['#2a5db0', '#060d1d', '#c4d8ff', 'trio'],
  };
  function person(dx, s, kind) {
    const hx = 50 + dx, hy = 44;
    let hair = '';
    if (kind === 'long' || kind === 'bun') hair = kind === 'long' ? `<path d="M${hx - 15 * s} ${hy} C ${hx - 18 * s} ${hy + 26 * s}, ${hx - 8 * s} ${hy + 30 * s}, ${hx} ${hy + 28 * s} C ${hx + 8 * s} ${hy + 30 * s}, ${hx + 18 * s} ${hy + 26 * s}, ${hx + 15 * s} ${hy}Z"/>` : `<circle cx="${hx + 4 * s}" cy="${hy - 15 * s}" r="${6 * s}"/>`;
    if (kind === 'cap') hair = `<path d="M${hx - 14 * s} ${hy - 4 * s} Q ${hx} ${hy - 22 * s} ${hx + 14 * s} ${hy - 4 * s} L ${hx + 22 * s} ${hy - 2 * s} L ${hx + 12 * s} ${hy + 1 * s}Z"/>`;
    if (kind === 'hood') hair = `<path d="M${hx - 19 * s} ${hy + 20 * s} C ${hx - 22 * s} ${hy - 22 * s}, ${hx + 22 * s} ${hy - 22 * s}, ${hx + 19 * s} ${hy + 20 * s}Z"/>`;
    if (kind === 'beard') hair = `<path d="M${hx - 11 * s} ${hy + 2 * s} Q ${hx} ${hy + 22 * s} ${hx + 11 * s} ${hy + 2 * s}Z"/>`;
    return `${hair}<ellipse cx="${hx}" cy="${hy}" rx="${12.5 * s}" ry="${14.5 * s}"/><rect x="${hx - 5 * s}" y="${hy + 10 * s}" width="${10 * s}" height="${12 * s}"/>
      <path d="M${hx - 34 * s} 104 C ${hx - 32 * s} ${hy + 30 * s}, ${hx - 18 * s} ${hy + 22 * s}, ${hx} ${hy + 22 * s} C ${hx + 18 * s} ${hy + 22 * s}, ${hx + 32 * s} ${hy + 30 * s}, ${hx + 34 * s} 104Z"/>`;
  }
  function avatarSVG(key, g) {
    const [c, dark, rim, kind] = ARTISTS[key]; const a = id('a');
    let figs;
    if (kind === 'duo') figs = [[-14, .82, 'short'], [16, .9, 'long']];
    else if (kind === 'trio') figs = [[-22, .68, 'short'], [22, .7, 'long'], [0, .8, 'short']];
    else figs = [[0, 1, kind]];
    const shape = figs.map(([dx, s, k]) => person(dx, s, k)).join('');
    return `<defs><radialGradient id="${a}" cx=".72" cy=".28" r=".9"><stop offset="0" stop-color="${rim}" stop-opacity=".95"/><stop offset=".35" stop-color="${c}"/><stop offset="1" stop-color="${dark}"/></radialGradient>${grainDef(g)}</defs>
      <rect width="100" height="100" fill="url(#${a})"/><g fill="${rim}" transform="translate(1.3 -.8)" opacity=".9">${shape}</g><g fill="${dark}">${shape}</g>${grainRect(g, .22)}`;
  }

  function fill(el) {
    const key = el.dataset.art, av = el.dataset.avatar, g = id('g');
    const inner = av ? avatarSVG(av, g) : (ART[key] ? ART[key].draw(g) : '');
    const z = el.dataset.zoom ? ` style="transform:scale(${el.dataset.zoom});transform-origin:${el.dataset.origin || '50% 50%'}"` : '';
    el.innerHTML = `<svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg"${z}>${inner}</svg>` + el.innerHTML;
  }

  // ---------- Wave / shape paths ----------
  function wavyPath(x0, x1, y, amp, wl, phase = 0) {
    let d = ''; for (let x = x0; x <= x1 + .01; x += 1) { const yy = y + amp * Math.sin((x - x0) / wl * Math.PI * 2 + phase); d += `${x === x0 ? 'M' : 'L'}${x.toFixed(1)} ${yy.toFixed(2)}`; } return d;
  }
  function cookiePath(cx, cy, R, n, k) {
    let d = ''; for (let i = 0; i <= 360; i++) { const t = i / 360 * Math.PI * 2; const r = R * (1 + k * Math.cos(n * t)) / (1 + k); d += `${i ? 'L' : 'M'}${(cx + r * Math.cos(t - Math.PI / 2)).toFixed(2)} ${(cy + r * Math.sin(t - Math.PI / 2)).toFixed(2)}`; } return d + 'Z';
  }
  // Seek bar. data-w width, data-p progress, data-style wavy|waveform|flat, data-c color, data-t track color
  function seek(el) {
    const w = +el.dataset.w, p = +el.dataset.p, st = el.dataset.style || 'wavy', c = el.dataset.c || 'currentColor', tc = el.dataset.t || 'rgba(255,255,255,.28)';
    const h = +(el.dataset.h || 32), y = h / 2, px = w * p, gap = 6, thumbH = +(el.dataset.thumb || 26);
    let body = '';
    if (st === 'waveform') { const yb = h * .74;
      const r = rng(+(el.dataset.seed || 4)), N = 72; let raw = []; let v = .55;
      for (let i = 0; i <= N; i++) { v += (r() - .5) * .55; v = Math.max(.25, Math.min(1, v)); raw.push(v); }
      const amps = raw.map((_, i) => { let s2 = 0, c2 = 0; for (let k = -4; k <= 4; k++) { const j = i + k; if (j >= 0 && j <= N) { s2 += raw[j]; c2++; } } const env = .45 + .55 * Math.sin(i / N * Math.PI * 1.05 + .12); return (s2 / c2) * env; });
      const layer = (scale, sh, fq) => { const pts = amps.map((a0, i) => [i / N * w, yb - Math.max(1, a0 * (yb - 2) * scale * (.5 + .5 * Math.sin(i * fq + sh)))]);
        let d = `M0 ${yb}L${pts[0][0]} ${pts[0][1]}`; for (let i = 1; i < pts.length; i++) { const [x0, y0] = pts[i - 1], [x1, y1] = pts[i]; const mx = (x0 + x1) / 2; d += `C${mx.toFixed(1)} ${y0.toFixed(1)} ${mx.toFixed(1)} ${y1.toFixed(1)} ${x1.toFixed(1)} ${y1.toFixed(1)}`; }
        return d + `L${w} ${yb}Z`; };
      const cp = id('cp'), cq = id('cq');
      body = `<defs><clipPath id="${cp}"><rect x="0" y="0" width="${px - gap}" height="${h}"/></clipPath><clipPath id="${cq}"><rect x="${px + gap}" y="0" width="${w}" height="${h}"/></clipPath></defs>
        <g clip-path="url(#${cq})"><path d="${layer(1, 0, .42)}" fill="${tc}" opacity=".7"/><path d="${layer(.8, 2.1, .27)}" fill="${tc}" opacity=".7"/><rect x="0" y="${yb - 1}" width="${w}" height="2" rx="1" fill="${tc}"/></g>
        <g clip-path="url(#${cp})"><path d="${layer(1, 0, .42)}" fill="${c}" opacity=".42"/><path d="${layer(.8, 2.1, .27)}" fill="${c}" opacity=".5"/><path d="${layer(.6, 4.2, .61)}" fill="${c}" opacity=".85"/><rect x="0" y="${yb - 1.5}" width="${w}" height="3" rx="1.5" fill="${c}"/></g>`;
    } else {
      const played = st === 'flat' ? `<line x1="2" x2="${px - gap}" y1="${y}" y2="${y}" stroke="${c}" stroke-width="4" stroke-linecap="round"/>` : `<path d="${wavyPath(2, px - gap, y, +(el.dataset.amp || 3.2), +(el.dataset.wl || 26), +(el.dataset.ph || 0))}" fill="none" stroke="${c}" stroke-width="${el.dataset.sw || 4}" stroke-linecap="round" stroke-linejoin="round"/>`;
      body = `${played}<line x1="${px + gap}" x2="${w - 2}" y1="${y}" y2="${y}" stroke="${tc}" stroke-width="${el.dataset.sw || 4}" stroke-linecap="round"/><circle cx="${w - 2}" cy="${y}" r="2" fill="${c}"/>`;
    }
    const thumb = el.dataset.nothumb ? '' : `<rect x="${px - 2}" y="${y - thumbH / 2}" width="4" height="${thumbH}" rx="2" fill="${c}"/>`;
    el.innerHTML = `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" style="display:block;overflow:visible">${body}${thumb}</svg>`;
  }
  function cookie(el) {
    const s = +el.dataset.size, n = +(el.dataset.n || 9), k = +(el.dataset.k || .085), c = el.dataset.c || 'var(--primary)', rot = +(el.dataset.rot || 0);
    el.style.width = el.style.height = s + 'px';
    const shape = el.dataset.shape === 'square' ? `<rect x="0" y="0" width="${s}" height="${s}" rx="${s * .3}" fill="${c}"/>` : el.dataset.shape === 'circle' ? `<circle cx="${s / 2}" cy="${s / 2}" r="${s / 2}" fill="${c}"/>` : `<path d="${cookiePath(s / 2, s / 2, s / 2, n, k)}" fill="${c}" transform="rotate(${rot} ${s / 2} ${s / 2})"/>`;
    el.insertAdjacentHTML('afterbegin', `<svg width="${s}" height="${s}" viewBox="0 0 ${s} ${s}">${shape}</svg>`);
  }

  // ---------- Status bar ----------
  const faDigits = (s) => String(s).replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
  function statusbar(el) {
    const fa = document.documentElement.lang === 'fa';
    const time = el.dataset.time || '20:47';
    const icons = `<svg width="17" height="14" viewBox="0 0 17 14"><path d="M1 13h2.4V9.5H1zM5 13h2.4V7H5zM9 13h2.4V4.5H9zM13 13h2.4V1.5H13z" fill="currentColor"/></svg>
      <svg width="17" height="14" viewBox="0 0 24 20"><path d="M12 19.2 1 7.4C3.9 4.6 7.8 3 12 3s8.1 1.6 11 4.4z" fill="currentColor"/></svg>
      <svg width="24" height="13" viewBox="0 0 24 13"><rect x=".6" y=".6" width="20" height="11.8" rx="3.4" fill="none" stroke="currentColor" stroke-width="1.2" opacity=".55"/><rect x="2.4" y="2.4" width="13" height="8.2" rx="1.8" fill="currentColor"/><rect x="21.6" y="4.2" width="1.6" height="4.6" rx=".8" fill="currentColor" opacity=".55"/></svg>`;
    el.innerHTML = `<span class="num">${fa ? faDigits(time) : time}</span><span class="sys">${icons}</span>`;
  }

  window.MP = { ART, ARTISTS, wavyPath, cookiePath, faDigits, rng };
  window.buildMock = function () {
    document.querySelectorAll('[data-art],[data-avatar]').forEach(fill);
    document.querySelectorAll('.seek').forEach(seek);
    document.querySelectorAll('.cookie').forEach(cookie);
    document.querySelectorAll('.statusbar').forEach(statusbar);
    document.querySelectorAll('.gesture-auto').forEach((e) => e.classList.add('gesture'));
    return document.fonts.ready.then(() => { document.body.dataset.ready = '1'; });
  };
})();
