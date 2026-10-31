/* Poch! web · navegación por pestañas, arranque e instalación. */
(function () {
  'use strict';
  const P = window.P, E = window.Engine, h = P.h, st = P.st;
  P.VERSION = '1.2.2';

  const TABS = [['Partida', 'trophy'], ['Historial', 'list'], ['Jugadores', 'people'], ['Estadísticas', 'chart'], ['Ajustes', 'gear']];
  let tab = 0, shown = null;
  const stacks = [];
  const navBtns = [];
  let content, live, liveText;

  P.tab = () => tab;
  P.standalone = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  const isIOS = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  P.installText = () => isIOS()
    ? 'En Safari, pulsa el botón Compartir (el cuadrado con la flecha hacia arriba) y elige «Añadir a pantalla de inicio».\n\nTendrás el icono de Poch! como una app más: pantalla completa y funcionando sin Internet.'
    : 'En el menú del navegador (los tres puntos) elige «Instalar aplicación» o «Añadir a pantalla de inicio».\n\nEn Android también puedes instalar la app nativa desde la página del proyecto en GitHub.';
  P.installHint = () => h('div', { class: 'banner install' }, P.icon('download', 'var(--blue-text)', 22),
    h('div', { class: 'grow' }, h('div', { style: { fontWeight: '600' } }, 'Instálala en tu móvil'),
      h('div', { class: 'text2', style: { fontSize: '13px', marginTop: '3px', fontWeight: '400' } }, isIOS() ? 'Compartir › «Añadir a pantalla de inicio»' : 'Menú del navegador › «Instalar aplicación»')),
    h('button', { class: 'chip', onclick: () => P.info('Instalar Poch!', P.installText()) }, 'Cómo'));

  // ------------------------------------------------------------------ pila de pantallas por pestaña

  function show() {
    P.closeAll();
    if (shown) { if (shown.scrollEl) shown.scrollY = shown.scrollEl.scrollTop; if (shown.leave) shown.leave(); }
    const top = stacks[tab][stacks[tab].length - 1];
    top.scrollEl = null;
    content.replaceChildren(top.build());
    if (top.scrollEl && top.scrollY) top.scrollEl.scrollTop = top.scrollY;
    shown = top;
    P.updateChrome();
  }
  P.refresh = show;
  P.push = (s) => { stacks[tab].push(s); history.pushState({ d: depth() }, ''); show(); };
  P.pop = () => { if (stacks[tab].length > 1) { stacks[tab].pop(); show(); } };
  P.popToRoot = (t) => { stacks[t].length = 1; };
  P.openIn = (t, s) => { P.popToRoot(t); stacks[t].push(s); tab = t; show(); };
  P.selectTab = (i) => {
    if (i === tab) { if (stacks[i].length > 1) { P.popToRoot(i); show(); } return; }
    tab = i;
    show();
  };
  const depth = () => stacks[tab].length;

  P.updateChrome = () => {
    const g = P.active();
    navBtns.forEach((b, i) => { b.classList.toggle('on', i === tab); b.classList.toggle('live', i === 0 && !!g); });
    const showLive = g && tab !== 0;
    live.style.display = showLive ? 'flex' : 'none';
    if (showLive) liveText.textContent = 'Partida en curso · Ronda ' + (Math.min(E.current(g), g.rounds.length - 1) + 1) ;
  };

  // Botón «atrás» del navegador / gesto de Android
  window.addEventListener('popstate', () => {
    if (P.hasOverlay()) { P.closeOverlay(); history.pushState({}, ''); return; }
    const top = stacks[tab][stacks[tab].length - 1];
    if (top.onBack && top.onBack()) { history.pushState({}, ''); return; }
    if (stacks[tab].length > 1) { stacks[tab].pop(); show(); return; }
    if (tab !== 0) { tab = 0; show(); history.pushState({}, ''); }
  });

  // ------------------------------------------------------------------ arranque

  function boot() {
    P.load();
    const app = document.getElementById('app');
    content = h('div', { id: 'content' });
    liveText = h('span', { class: 'grow ell' });
    live = h('button', { id: 'live', onclick: () => { P.popToRoot(0); tab = 0; show(); } },
      h('span', { class: 'dot', style: { width: '9px', height: '9px', background: 'var(--blue-hi)' } }), liveText, h('span', { class: 'go' }, 'Ir al marcador'), P.icon('chev', 'var(--blue-text)', 18));
    const nav = h('nav', { id: 'nav' });
    TABS.forEach(([label, icon], i) => {
      const b = h('button', { 'aria-label': label, onclick: () => P.selectTab(i) }, h('span', { class: 'pill' }, P.icon(icon, null, 23)), h('span', { class: 'lab' }, label));
      navBtns.push(b);
      nav.appendChild(b);
    });
    app.replaceChildren(content, live, nav);
    P.gameTab = P.GameTab();
    stacks.push([P.gameTab], [P.History()], [P.Players()], [P.Stats()], [P.Settings()]);
    history.replaceState({ d: 1 }, '');
    history.pushState({ d: 2 }, '');
    show();
    // Invitación a un grupo abierta desde el enlace (al arrancar o con la app ya abierta)
    const checkInvite = () => {
      const m = location.hash.match(/unirse=([A-Za-z0-9_-]+)/);
      if (!m) return;
      history.replaceState(history.state, '', location.pathname + location.search);
      P.openIn(4, P.JoinScreen('unirse=' + m[1]));
    };
    checkInvite();
    window.addEventListener('hashchange', checkInvite);
    // Al abrir la app se bajan las novedades de los grupos
    setTimeout(() => { P.syncQuiet(false).catch(() => {}); }, 400);
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
    if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => {});
    // Con el teclado abierto se oculta la barra de pestañas para dejar sitio
    if (window.visualViewport) window.visualViewport.addEventListener('resize', () => {
      const kb = window.innerHeight - window.visualViewport.height > 150;
      nav.style.display = kb ? 'none' : 'flex';
      if (kb) live.style.display = 'none'; else P.updateChrome();
    });
  }
  document.addEventListener('DOMContentLoaded', boot);
})();
