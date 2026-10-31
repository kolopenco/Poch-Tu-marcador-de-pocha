/* Poch! web · piezas comunes de interfaz, iconos, ventanas y almacenamiento. */
(function () {
  'use strict';
  const P = window.P = window.P || {};

  // ------------------------------------------------------------------ DOM

  function h(tag, props, ...kids) {
    const el = document.createElement(tag);
    if (props) {
      for (const k of Object.keys(props)) {
        const v = props[k];
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
        else if (k === 'html') el.innerHTML = v;
        else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
        else if (k in el && typeof v !== 'string') el[k] = v;
        else el.setAttribute(k, v === true ? '' : v);
      }
    }
    add(el, kids);
    return el;
  }
  function add(el, kids) {
    for (const c of kids) {
      if (c == null || c === false) continue;
      if (Array.isArray(c)) add(el, c);
      else el.appendChild(c instanceof Node ? c : document.createTextNode(String(c)));
    }
  }
  P.h = h;

  // ------------------------------------------------------------------ colores y textos

  P.css = (c) => '#' + ((c >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
  P.mix = (c, f) => {               // f > 0 aclara, f < 0 oscurece
    const n = (c >>> 0) & 0xFFFFFF;
    let r = n >> 16, g = (n >> 8) & 255, b = n & 255;
    if (f >= 0) { r += (255 - r) * f; g += (255 - g) * f; b += (255 - b) * f; }
    else { r *= 1 + f; g *= 1 + f; b *= 1 + f; }
    return 'rgb(' + Math.round(r) + ',' + Math.round(g) + ',' + Math.round(b) + ')';
  };
  P.PLAYER_COLORS = [0xFF1E9E54, 0xFFE2531C, 0xFF1C6FE3, 0xFF2D9BEA, 0xFFE3A214, 0xFF8C44D6, 0xFFEE5AAE,
    0xFFE2353A, 0xFF33CDB0, 0xFFA294F0, 0xFF7A9A1E, 0xFFB86A3C].map((c) => c | 0);

  const MINUS = '−';
  P.num = (v) => v < 0 ? MINUS + (-v) : String(v);
  P.signed = (v) => v > 0 ? '+' + v : v < 0 ? MINUS + (-v) : '±0';
  P.plural = (n, one, many) => n + ' ' + (n === 1 ? one : many);
  P.fmt1 = (v) => {
    if (v == null || isNaN(v)) return '—';
    const s = (Math.round(v * 10) / 10).toFixed(1).replace('.', ',');
    return s.endsWith(',0') ? s.slice(0, -2) : s;
  };
  P.pct = (v) => (v == null || isNaN(v)) ? '—' : Math.round(v) + '%';
  const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  P.date = (t) => { const d = new Date(t); return d.getDate() + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear(); };
  P.dateTime = (t) => { const d = new Date(t); return P.date(t) + ' · ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
  P.joinNames = (n) => n.length <= 1 ? (n[0] || '') : n.slice(0, -1).join(', ') + ' y ' + n[n.length - 1];
  P.newId = (p) => p + Date.now().toString(36) + Math.floor(Math.random() * 1296).toString(36);
  P.vibrate = (ms) => { try { if (navigator.vibrate) navigator.vibrate(ms || 8); } catch (e) { /* iPhone no vibra */ } };

  // ------------------------------------------------------------------ iconos (rejilla 24×24, como Ico.java)

  const F = 'fill="currentColor"', S = 'fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"';
  const ICONS = {
    trophy: `<path ${F} d="M6.4 3.4h11.2v4.8a5.6 5.6 0 0 1-11.2 0z"/><path ${S} stroke-width="1.7" d="M6.6 5.3H3.9c-.3 3.3 1.1 5.3 3.6 5.6M17.4 5.3h2.7c.3 3.3-1.1 5.3-3.6 5.6"/><rect ${F} x="10.9" y="13.6" width="2.2" height="3.8"/><rect ${F} x="7.2" y="17.2" width="9.6" height="3.4" rx="1.2"/>`,
    list: `<g ${F}><rect x="3.2" y="4.5" width="3" height="3" rx=".7"/><rect x="3.2" y="10.5" width="3" height="3" rx=".7"/><rect x="3.2" y="16.5" width="3" height="3" rx=".7"/></g><path ${S} stroke-width="2.2" d="M9.2 6h11.4M9.2 12h11.4M9.2 18h11.4"/>`,
    people: `<g ${F}><circle cx="16.6" cy="7.4" r="2.7"/><path d="M14.4 12.7c.7-.35 1.4-.5 2.2-.5 2.8 0 4.6 2.2 4.6 5.2v.8h-3.9c-.1-2.3-1.1-4.2-2.9-5.5z"/><circle cx="9.4" cy="7.8" r="3.4"/><path d="M2.8 19.4v-.8c0-3.4 2.8-5.6 6.6-5.6s6.6 2.2 6.6 5.6v.8z"/></g>`,
    chart: `<g ${F}><rect x="3.6" y="11.5" width="4.4" height="8.9" rx="1"/><rect x="9.8" y="3.6" width="4.4" height="16.8" rx="1"/><rect x="16" y="8" width="4.4" height="12.4" rx="1"/></g>`,
    gear: `<g ${F}>${[0, 45, 90, 135, 180, 225, 270, 315].map((a) => `<rect x="10.2" y="1.6" width="3.6" height="4.4" rx="1" transform="rotate(${a} 12 12)"/>`).join('')}<path fill-rule="evenodd" d="M12 4.2a7.8 7.8 0 1 1 0 15.6 7.8 7.8 0 0 1 0-15.6zm0 4.7a3.1 3.1 0 1 0 0 6.2 3.1 3.1 0 0 0 0-6.2z"/></g>`,
    back: `<path ${S} stroke-width="2" d="M19.5 12H5M11 5.8 4.8 12l6.2 6.2"/>`,
    chev: `<path ${S} stroke-width="2" d="M9 5.5 15.5 12 9 18.5"/>`,
    chevL: `<path ${S} stroke-width="2" d="M15 5.5 8.5 12l6.5 6.5"/>`,
    chevD: `<path ${S} stroke-width="2" d="M5.5 9 12 15.5 18.5 9"/>`,
    plus: `<path ${S} stroke-width="2" d="M12 5v14M5 12h14"/>`,
    minus: `<path ${S} stroke-width="2" d="M5 12h14"/>`,
    search: `<circle ${S} stroke-width="2" cx="10.5" cy="10.5" r="6.3"/><path ${S} stroke-width="2" d="m15.3 15.3 4.9 4.9"/>`,
    edit: `<path ${S} stroke-width="2" d="M14.6 4.4l5 5L9.2 19.8H4.2v-5zM12.4 6.6l5 5"/>`,
    gavel: `<g fill="#F2B41F"><g transform="rotate(-45 12 12)"><rect x="5.5" y="3.2" width="10" height="5.2" rx="1.3"/><rect x="4.4" y="2.5" width="2" height="6.6" rx=".8" fill="#D39A12"/><rect x="14.6" y="2.5" width="2" height="6.6" rx=".8" fill="#D39A12"/><rect x="9.4" y="8" width="2.2" height="12.5" rx="1"/></g><rect x="12.5" y="18.6" width="8.5" height="2.6" rx="1.1" fill="#D39A12"/></g>`,
    crown: `<g ${F}><path d="M3.2 8.2 7.6 12.4 12 5.2l4.4 7.2 4.4-4.2-1.8 9.4H5z"/><circle cx="3.2" cy="7.6" r="1.5"/><circle cx="12" cy="4.4" r="1.5"/><circle cx="20.8" cy="7.6" r="1.5"/><rect x="5" y="18.6" width="14" height="2.4" rx=".9"/></g>`,
    pause: `<g ${F}><rect x="6.6" y="5" width="3.6" height="14" rx="1.1"/><rect x="13.8" y="5" width="3.6" height="14" rx="1.1"/></g>`,
    coin: `<circle cx="12" cy="12" r="9.6" fill="#B9780A"/><circle cx="12" cy="12" r="8.6" fill="#F5B623"/><circle cx="12" cy="12" r="5.9" fill="none" stroke="#C27E0C" stroke-width="1.2"/><circle cx="12" cy="12" r="2.2" fill="#D48C10"/><circle cx="9.2" cy="8.6" r="1.3" fill="#fff" opacity=".6"/>`,
    cup: `<g ${F}><path d="M5.8 3.6h12.4c0 5.2-2.6 8-6.2 8s-6.2-2.8-6.2-8z"/><rect x="10.9" y="11" width="2.2" height="5.6"/><path d="M7.4 20.4c.4-2.8 2.2-4.2 4.6-4.2s4.2 1.4 4.6 4.2z"/></g>`,
    sword: `<path ${F} d="m12 1.8 1.5 2.8-.1 10.6h-2.8l-.1-10.6z"/><path ${S} stroke-width="2.2" d="M6.8 15.8h10.4M12 16.4v4"/><circle ${F} cx="12" cy="21.4" r="1.3"/>`,
    club: `<path ${S} stroke-width="4.4" d="M8.2 20.4 15.8 4"/><path ${S} stroke-width="1.6" d="M12.4 11.5l4.4-1.1M10.2 16.6l-3.6-1.4"/>`,
    deck: `<g transform="rotate(-15 12 13)"><rect x="4.2" y="4.2" width="9.6" height="15.2" rx="1.6" fill="#E8EDF5" stroke="rgba(0,0,0,.4)" stroke-width=".9"/></g><g transform="rotate(12 12 13)"><rect x="10" y="3.6" width="9.6" height="15.2" rx="1.6" fill="#fff" stroke="rgba(0,0,0,.4)" stroke-width=".9"/><circle cx="14.8" cy="11.2" r="2.6" fill="#E2A21A"/><circle cx="14.8" cy="11.2" r="1.5" fill="none" stroke="#B97A08" stroke-width=".7"/></g>`,
    check: `<path ${S} stroke-width="2.4" d="m5 12.6 4.8 4.6 9.4-10"/>`,
    close: `<path ${S} stroke-width="2" d="m6.2 6.2 11.6 11.6M17.8 6.2 6.2 17.8"/>`,
    dots: `<g ${F}><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></g>`,
    filter: `<path ${S} stroke-width="1.8" d="M3.8 5h16.4L14 12.4v6.2l-4 1.6v-7.8z"/>`,
    gamepad: `<path ${F} fill-rule="evenodd" d="M7.6 6.6h8.8a5.4 5.4 0 0 1 0 11H7.6a5.4 5.4 0 0 1 0-11zm-.9 2.5v2.1H4.6v1.8h2.1v2.1h1.8V13h2.1v-1.8H8.5V9.1zm8.9.3a1.25 1.25 0 1 0 0 2.5 1.25 1.25 0 0 0 0-2.5zm2.3 2.7a1.25 1.25 0 1 0 0 2.5 1.25 1.25 0 0 0 0-2.5z"/>`,
    star: `<path ${F} d="m12 3 2.4 6.1 6.5.4-5 4.2 1.6 6.3L12 16.5 6.5 20l1.6-6.3-5-4.2 6.5-.4z"/>`,
    target: `<circle ${S} stroke-width="2" cx="12" cy="12" r="8.6"/><circle ${S} stroke-width="2" cx="12" cy="12" r="4.8"/><circle ${F} cx="12" cy="12" r="1.6"/>`,
    share: `<path ${S} stroke-width="1.8" d="M17.6 5.6 6.4 12l11.2 6.4"/><g ${F}><circle cx="17.6" cy="5.4" r="2.7"/><circle cx="6.4" cy="12" r="2.7"/><circle cx="17.6" cy="18.6" r="2.7"/></g>`,
    flag: `<path ${S} stroke-width="2" d="M5.6 3.4V21"/><path ${F} d="M6.2 4h12.4l-3.2 4.3 3.2 4.3H6.2z"/>`,
    play: `<path ${F} stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" d="M7.2 4.4 19.2 12 7.2 19.6z"/>`,
    trash: `<path ${S} stroke-width="1.9" d="M4 6.4h16M9.2 6V3.8h5.6V6M6.2 6.8l1 13.6h9.6l1-13.6M10.3 10.2v6.6M13.7 10.2v6.6"/>`,
    drag: `<path ${S} stroke-width="2" d="M5 7.5h14M5 12h14M5 16.5h14"/>`,
    download: `<path ${S} stroke-width="2" d="M12 3.6v11M7.4 10.2l4.6 4.6 4.6-4.6M4.2 15.6v4.2h15.6v-4.2"/>`,
    upload: `<path ${S} stroke-width="2" d="M12 15V4M7.4 8.4 12 3.8l4.6 4.6M4.2 15.6v4.2h15.6v-4.2"/>`,
    sliders: `<path ${S} stroke-width="1.8" d="M4 7h16M4 12h16M4 17h16"/><g ${F}><circle cx="15" cy="7" r="2.4"/><circle cx="8.5" cy="12" r="2.4"/><circle cx="13" cy="17" r="2.4"/></g>`,
    info: `<circle ${S} stroke-width="1.8" cx="12" cy="12" r="8.8"/><path ${S} stroke-width="2.1" d="M12 11v5.6"/><circle ${F} cx="12" cy="7.7" r="1.3"/>`,
    undo: `<path ${S} stroke-width="2" d="M8.4 4.8 4.2 9l4.2 4.2M4.6 9h9.6a5.8 5.8 0 0 1 0 11H9"/>`,
    sync: `<path ${S} stroke-width="2.3" d="M6.2 16.5A7 7 0 1 1 18.8 10"/><path ${S} stroke-width="2.3" d="m20.2 6.4-1.2 4-3.9-1.5"/>`,
    backup: `<path ${S} stroke-width="1.8" d="M7 18.5c-2.8 0-4.4-1.9-4.4-4.1 0-2.3 1.8-4 4-4 .7-3 3-5 6-5 3.6 0 6 2.6 6.2 5.6 1.6.2 2.8 1.7 2.8 3.7 0 2.1-1.6 3.8-4 3.8zM12 16.6v-6M9.6 12.8l2.4-2.4 2.4 2.4"/>`,
    cards: `${[-16, 0, 16].map((a) => `<g transform="rotate(${a} 12 22)"><rect x="8" y="4" width="8" height="13" rx="1.4" fill="#fff" stroke="rgba(0,0,0,.35)" stroke-width=".8"/></g>`).join('')}<circle cx="12" cy="9.4" r="2.1" fill="#E2A21A"/>`,
    android: `<path ${F} d="M7 9.5h10v7.2a1.3 1.3 0 0 1-1.3 1.3h-.9v2.5a1.1 1.1 0 0 1-2.2 0V18h-1.2v2.5a1.1 1.1 0 0 1-2.2 0V18h-.9A1.3 1.3 0 0 1 7 16.7zM4.6 9.7a1.1 1.1 0 0 1 2.2 0v5a1.1 1.1 0 0 1-2.2 0zm12.6 0a1.1 1.1 0 0 1 2.2 0v5a1.1 1.1 0 0 1-2.2 0zM7.1 8.7a5 5 0 0 1 9.8 0zM9.6 4.3l-.9-1.5M14.4 4.3l.9-1.5"/><path ${S} stroke-width="1" d="m9.6 4.3-.9-1.5M14.4 4.3l.9-1.5"/>`,
  };
  P.icon = (name, color, size) => {
    const s = size || 24;
    const span = h('span', { style: { display: 'inline-flex', width: s + 'px', height: s + 'px', color: color || 'currentColor', flexShrink: '0' } });
    span.innerHTML = `<svg width="${s}" height="${s}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ''}</svg>`;
    return span;
  };
  P.SUIT_ICON = { oros: 'coin', copas: 'cup', espadas: 'sword', bastos: 'club' };
  P.SUIT_COLOR = { oros: '#F2B41F', copas: '#E2453A', espadas: '#5AA8F0', bastos: '#4FB548' };

  // ------------------------------------------------------------------ piezas

  P.dot = (color, size) => h('span', { class: 'dot', style: { width: size + 'px', height: size + 'px', background: `radial-gradient(circle at 36% 30%, ${P.mix(color, 0.28)}, ${P.mix(color, -0.12)} 85%)` } });
  P.ibtn = (icon, color, size, run, label) => h('button', { class: 'ibtn', 'aria-label': label || icon, onclick: run }, P.icon(icon, color, size));
  P.header = (lead, title, sub, ...right) => h('div', { class: 'header' }, lead, h('div', { class: 'ht' }, h('h1', null, title), sub ? h('div', { class: 'sub' }, sub) : null), right);
  P.cheader = (title, back, right) => h('div', { class: 'cheader' },
    back ? h('div', { class: 'l' }, P.ibtn('back', '#fff', 24, back, 'Atrás')) : null, h('h1', null, title), right ? h('div', { class: 'r' }, right) : null);
  P.primary = (label, run, enabled) => h('button', { class: 'primary', disabled: enabled === false, onclick: run }, label);
  P.secondary = (label, color, run, icon) => h('button', { class: 'secondary', style: { color, borderColor: color + '99' }, onclick: run }, icon ? P.icon(icon, color, 20) : null, label);
  P.section = (t) => h('div', { class: 'section' }, t);
  P.note = (t) => h('div', { class: 'note' }, t);
  P.seg = (labels, sel, onSel) => {
    const bar = h('div', { class: 'seg' });
    labels.forEach((l, i) => bar.appendChild(h('button', {
      class: i === sel ? 'on' : '', onclick: () => {
        [...bar.children].forEach((b, k) => b.classList.toggle('on', k === i));
        onSel(i);
      },
    }, l)));
    return bar;
  };
  P.chip = (label, on, run, icon) => h('button', { class: 'chip' + (on ? ' on' : ''), onclick: run }, icon ? P.icon(icon, on ? '#fff' : 'var(--text2)', 16) : null, label);
  P.lrow = (lead, title, sub, run, cls) => h('button', { class: 'lrow ' + (cls || ''), onclick: run },
    lead, h('div', { class: 'grow ell', style: { flex: '1' } }, h('div', { class: 't ell' }, title), sub ? h('div', { class: 's ell' }, sub) : null),
    run ? P.icon('chev', 'var(--text2)', 20) : null);
  P.tile = (icon, color, value, label) => h('div', { class: 'tile' }, icon ? P.icon(icon, color, 24) : null, h('div', { class: 'v' }, value), h('div', { class: 'l' }, label));
  P.kv = (k, v) => h('div', { class: 'kv' }, h('div', { class: 'k' }, k), h('div', { class: 'v' }, v));
  P.toggle = (on, cb) => {
    const t = h('button', { class: 'toggle' + (on ? ' on' : ''), role: 'switch', 'aria-checked': String(!!on) });
    t.onclick = () => { const v = !t.classList.contains('on'); t.classList.toggle('on', v); t.setAttribute('aria-checked', String(v)); P.vibrate(); cb(v); };
    return t;
  };
  P.stepper = (value, min, max, suffix, cb) => {
    let v = value;
    const sp = h('span', null, v + (suffix || ''));
    const set = (d) => { const n = Math.max(min, Math.min(max, v + d)); if (n !== v) { v = n; sp.textContent = v + (suffix || ''); cb(v); } };
    return h('div', { class: 'stepper' }, h('button', { 'aria-label': 'Menos', onclick: () => set(-1) }, P.icon('minus', '#fff', 18)), sp,
      h('button', { 'aria-label': 'Más', onclick: () => set(1) }, P.icon('plus', '#fff', 18)));
  };
  P.setRow = (title, sub, control) => h('div', { class: 'set' }, h('div', { class: 'st' }, title, sub ? h('div', { class: 'ss' }, sub) : null), control);
  P.option = (title, sub, on, run) => h('button', { class: 'option' + (on ? ' on' : ''), onclick: run }, h('span', { class: 'rd' }),
    h('div', { style: { flex: '1', minWidth: '0' } }, h('div', { class: 't' }, title), sub ? h('div', { class: 's' }, sub) : null));
  P.input = (placeholder, value) => h('input', { class: 'input', type: 'text', placeholder, value: value || '', autocapitalize: 'words', autocomplete: 'off' });

  // ------------------------------------------------------------------ ventanas

  const layers = () => document.getElementById('overlays');

  function layer(dimClose) {
    const l = h('div');
    const dim = h('div', { class: 'dim' });
    dim.onclick = () => { if (dimClose) P.closeOverlay(); };
    l.appendChild(dim);
    layers().appendChild(l);
    return l;
  }
  P.closeOverlay = () => { const o = layers(); if (o.lastChild) o.removeChild(o.lastChild); };
  P.closeAll = () => { layers().innerHTML = ''; };
  P.hasOverlay = () => layers().children.length > 0;

  /** actions: [{label, style: 'plain'|'main'|'danger', run}] */
  P.dialog = (title, msg, extra, actions) => {
    const l = layer(true);
    const stack = actions.length > 2 || actions.some((a) => a.label.length > 16);
    const acts = h('div', { class: 'acts' + (stack ? ' stack' : '') });
    for (const a of actions) {
      acts.appendChild(h('button', {
        class: a.style === 'main' ? 'primary' : a.style === 'danger' ? 'btn-danger' : 'btn-plain',
        style: a.style === 'main' ? { fontSize: '16px', minHeight: '50px' } : null,
        onclick: () => { P.closeOverlay(); if (a.run) a.run(); },
      }, a.label));
    }
    l.appendChild(h('div', { class: 'dialog', role: 'dialog' }, title ? h('h2', null, title) : null, msg ? h('p', null, msg) : null,
      extra ? h('div', { style: { marginTop: '14px' } }, extra) : null, acts));
    return l;
  };
  P.info = (title, msg) => P.dialog(title, msg, null, [{ label: 'Entendido', style: 'main' }]);
  P.confirm = (title, msg, yes, danger, run) => P.dialog(title, msg, null, [{ label: 'Cancelar' }, { label: yes, style: danger ? 'danger' : 'main', run }]);
  P.prompt = (title, placeholder, value, ok, done) => {
    const inp = P.input(placeholder, value);
    P.dialog(title, null, inp, [{ label: 'Cancelar' }, { label: ok, style: 'main', run: () => done(inp.value.trim()) }]);
    setTimeout(() => inp.focus(), 60);
  };
  P.sheet = (title, body) => {
    const l = layer(true);
    l.appendChild(h('div', { class: 'sheet', role: 'dialog' }, h('div', { class: 'handle' }),
      h('div', { class: 'sh' }, h('h2', null, title), P.ibtn('close', 'var(--text2)', 20, P.closeOverlay, 'Cerrar')),
      h('div', { class: 'sb' }, body)));
    return l;
  };
  P.menu = (items) => {
    const l = layer(true);
    const box = h('div', { class: 'menu' }, h('div', { style: { display: 'flex', justifyContent: 'flex-end' } }, P.ibtn('close', 'var(--text2)', 20, P.closeOverlay, 'Cerrar')));
    for (const it of items) {
      box.appendChild(h('button', { class: 'mitem', style: { opacity: it.enabled === false ? '.5' : '1' }, onclick: () => { P.closeOverlay(); it.run(); } },
        P.icon(it.icon, it.color, 22), h('div', null, h('div', { class: 't', style: { color: it.color === 'var(--red)' ? 'var(--red-hi)' : '#fff' } }, it.label),
          it.sub ? h('div', { class: 's' }, it.sub) : null)));
    }
    l.appendChild(box);
  };
  let toastTimer;
  P.toast = (msg) => {
    let t = document.getElementById('toast');
    if (!t) { t = h('div', { id: 'toast' }); document.body.appendChild(t); }
    t.textContent = msg;
    t.style.opacity = '1';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.style.opacity = '0'; }, 2300);
  };

  /** Hoja con casillas para elegir varios jugadores; done(ids) devuelve true para cerrar. */
  P.multiPick = (title, note, players, preset, button, done) => {
    const chosen = new Set(preset);
    const rows = [];
    const paint = () => rows.forEach(([p, row, box]) => {
      const on = chosen.has(p.id);
      box.className = 'check' + (on ? ' on' : '');
      box.innerHTML = '';
      if (on) box.appendChild(P.icon('check', '#fff', 18));
      row.style.background = on ? '#0F2242' : 'var(--surface2)';
    });
    const list = h('div');
    for (const p of players) {
      const box = h('span', { class: 'check' });
      const row = h('button', { class: 'lrow', style: { border: '0', minHeight: '52px', marginBottom: '6px' } }, P.dot(p.color, 24),
        h('div', { class: 't ell', style: { flex: '1', fontWeight: '500', fontSize: '15.5px' } }, p.name), box);
      row.onclick = () => { if (!chosen.delete(p.id)) chosen.add(p.id); paint(); };
      rows.push([p, row, box]);
      list.appendChild(row);
    }
    paint();
    const body = h('div', null,
      note ? h('div', { class: 'note', style: { margin: '0 2px 10px', color: 'var(--text2)', fontSize: '13px' } }, note) : null,
      h('div', { class: 'chips', style: { marginBottom: '10px' } },
        P.chip('Todos', false, () => { players.forEach((p) => chosen.add(p.id)); paint(); }),
        P.chip('Ninguno', false, () => { chosen.clear(); paint(); })),
      list, players.length ? null : h('div', { class: 'muted' }, 'No hay más jugadores.'),
      h('div', { style: { height: '10px' } }),
      P.primary(button, () => { if (done(players.filter((p) => chosen.has(p.id)).map((p) => p.id))) P.closeOverlay(); }));
    P.sheet(title, body);
  };

  // ------------------------------------------------------------------ almacenamiento (en el propio móvil)

  const KEY = 'poch.v1';
  const st = P.st = {
    players: [], profiles: [], defaultProfile: 'clasica', games: [], activeGame: null,
    statsSel: [], statsCmp: [], lastBackup: 0, prevData: null, prevTime: 0, groups: [], lastGroup: null,
  };
  P.load = () => {
    try {
      const o = JSON.parse(localStorage.getItem(KEY) || 'null');
      if (o) Object.assign(st, o);
    } catch (e) { /* datos dañados: se empieza de cero */ }
    st.profiles = (st.profiles || []).map(Engine.normRules);
    if (!st.profiles.length) st.profiles.push(Engine.defaultRules());
    if (!st.profiles.some((r) => r.id === st.defaultProfile)) st.defaultProfile = st.profiles[0].id;
    st.games = (st.games || []).map(Engine.normGame);
    const a = st.games.find((g) => g.id === st.activeGame);
    if (!a || a.status !== Engine.ACTIVE) st.activeGame = null;
  };
  P.save = () => {
    try { localStorage.setItem(KEY, JSON.stringify(st)); }
    catch (e) { P.toast('No se pudo guardar: el móvil no deja más espacio'); }
  };
  P.player = (id) => st.players.find((p) => p.id === id);
  P.game = (id) => st.games.find((g) => g.id === id);
  P.active = () => st.activeGame ? P.game(st.activeGame) : null;
  P.sorted = () => st.players.slice().sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }));
  P.closedGames = () => st.games.filter(Engine.counts);
  P.byDate = () => st.games.slice().sort((a, b) => b.created - a.created);
  P.defaultRules = () => st.profiles.find((r) => r.id === st.defaultProfile) || st.profiles[0];
  P.nextColor = () => P.PLAYER_COLORS.find((c) => !st.players.some((p) => p.color === c)) ?? P.PLAYER_COLORS[st.players.length % 12];
  P.nameTaken = (name, except) => st.players.some((p) => p.id !== except && Engine.norm(p.name) === Engine.norm(name));
  P.snapshot = () => ({ players: st.players, profiles: st.profiles, defaultProfile: st.defaultProfile, games: st.games, activeGame: st.activeGame, statsSel: st.statsSel, groups: st.groups || [] });
  P.replaceAll = (d, keepPrev) => {
    if (keepPrev) { st.prevData = Engine.buildBackup(P.snapshot(), P.VERSION, Date.now()); st.prevTime = Date.now(); }
    st.players = d.players; st.profiles = d.profiles; st.defaultProfile = d.defaultProfile;
    st.games = d.games; st.activeGame = d.activeGame || null; st.statsSel = d.statsSel || [];
    st.statsCmp = st.statsCmp.filter((id) => P.player(id));
    if (d.groups) st.groups = d.groups;      // copia antigua (sin grupos): se conservan los actuales
    else d.groups = st.groups || [];
    P.save();
    // Verificación: lo guardado coincide con lo que se quería guardar
    const saved = JSON.parse(localStorage.getItem(KEY) || '{}');
    return JSON.stringify(Engine.buildBackup({ players: saved.players, profiles: saved.profiles, defaultProfile: saved.defaultProfile, games: saved.games, activeGame: saved.activeGame, statsSel: saved.statsSel, groups: saved.groups }, '', 0))
      === JSON.stringify(Engine.buildBackup(d, '', 0));
  };
  P.saveGame = () => P.save();
})();
