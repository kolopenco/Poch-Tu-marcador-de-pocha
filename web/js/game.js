/* Poch! web · partida: marcador, carrusel de rondas, edición, menú, clasificación final y asistente. */
(function () {
  'use strict';
  const P = window.P, E = window.Engine, h = P.h, st = P.st;

  // ------------------------------------------------------------------ textos

  function readText(g, x) {
    if (!x) return 'Sin dato';
    if (g.mode === E.FULL) {
      if (x.b == null || x.m == null) return 'Sin dato';
      return 'Pedidas: ' + x.b + ' · Hechas: ' + x.m;
    }
    if (x.v == null) return 'Sin dato';
    return x.v >= 0 ? 'Acierto: ' + x.v : 'Fallo: ' + P.num(x.v);
  }
  P.readText = readText;

  P.gameTitle = (g) => {
    if (g.status === E.ACTIVE) return 'Partida en curso';
    if (g.status === E.EARLY) return 'Finalizada (sin victoria)';
    const w = E.winners(g);
    if (w.length === 1) return 'Ganada por ' + g.names[w[0]];
    return 'Empate (' + P.joinNames(w.map((p) => g.names[p])) + ')';
  };
  P.gameSub = (g) => {
    if (g.status === E.ACTIVE) {
      const cur = Math.min(E.current(g), g.rounds.length - 1);
      return g.order.length + ' jugadores · Ronda ' + (cur + 1) + ' de ' + E.regularCount(g);
    }
    if (g.status === E.EARLY) return g.order.length + ' jugadores · ' + E.savedRegular(g) + ' de ' + E.regularCount(g) + ' rondas';
    return g.order.length + ' jugadores · ' + P.plural(E.savedCount(g), 'ronda', 'rondas');
  };

  // ------------------------------------------------------------------ carrusel de rondas

  function RoundStrip(onSelect) {
    let cards = [], edited = [], maxSel = 0, sel = 0, pos = 0;
    const vp = h('div', { class: 'vp' });
    const left = h('button', { class: 'arr l', 'aria-label': 'Ronda anterior' }, P.icon('chevL', '#E6ECF5', 24));
    const right = h('button', { class: 'arr r', 'aria-label': 'Ronda siguiente' }, P.icon('chev', '#E6ECF5', 24));
    const el = h('div', { class: 'strip' }, left, vp, right);
    let items = [];
    const off = (d) => { const a = Math.abs(d); const o = a <= 1 ? a * 48 : 48 + (a - 1) * 31; return d < 0 ? -o : o; };

    function layout() {
      items.forEach((it, i) => {
        const d = i - pos, near = Math.max(0, 1 - Math.abs(d));
        const size = 28 + 20 * near;
        const isCur = near > 0.5;
        it.style.transform = `translateX(${off(d)}px)`;
        it.style.width = it.style.height = size + 'px';
        it.style.margin = (-size / 2) + 'px 0 0 ' + (-size / 2) + 'px';
        it.style.fontSize = (18 + 8 * near) + 'px';
        it.className = 'it' + (isCur ? ' cur' : '') + (edited[i] && !isCur ? ' ed' : '') + (edited[i] && isCur ? ' ed' : '') + (i > maxSel ? ' fut' : '');
        it.style.opacity = Math.abs(off(d)) > 160 ? '0' : '1';
      });
      left.classList.toggle('off', sel <= 0);
      right.classList.toggle('off', sel >= maxSel);
    }
    function go(t) {
      t = Math.max(0, Math.min(maxSel, Math.min(cards.length - 1, t)));
      const changed = t !== sel;
      sel = t; pos = t;
      el.classList.remove('drag');
      layout();
      if (changed) { P.vibrate(5); onSelect(t); }
    }
    left.onclick = () => go(sel - 1);
    right.onclick = () => go(sel + 1);

    let downX = 0, downPos = 0, dragging = false, pid = null;
    vp.addEventListener('pointerdown', (e) => { pid = e.pointerId; downX = e.clientX; downPos = pos; dragging = false; vp.setPointerCapture(pid); });
    vp.addEventListener('pointermove', (e) => {
      if (e.pointerId !== pid) return;
      const dx = e.clientX - downX;
      if (!dragging && Math.abs(dx) > 8) { dragging = true; el.classList.add('drag'); }
      if (dragging) { pos = Math.max(-0.3, Math.min(maxSel + 0.3, downPos - dx / 31)); layout(); }
    });
    const end = (e) => {
      if (e.pointerId !== pid) return;
      pid = null;
      if (dragging) { go(Math.round(pos)); return; }
      const r = vp.getBoundingClientRect(), x = e.clientX - r.left - r.width / 2;
      let best = sel, bd = 1e9;
      items.forEach((it, i) => { const d = Math.abs(off(i - pos) - x); if (d < bd) { bd = d; best = i; } });
      go(best);
    };
    vp.addEventListener('pointerup', end);
    vp.addEventListener('pointercancel', (e) => { if (e.pointerId === pid) { pid = null; pos = sel; el.classList.remove('drag'); layout(); } });

    return {
      el,
      set(c, ed, max, s) {
        if (c.length !== cards.length) {
          vp.innerHTML = '';
          items = c.map((n) => vp.appendChild(h('div', { class: 'it' }, String(n))));
        } else items.forEach((it, i) => { it.textContent = String(c[i]); });
        cards = c; edited = ed; maxSel = max; sel = s; pos = s;
        layout();
      },
    };
  }

  // ------------------------------------------------------------------ tarjeta de jugador

  function playerCard(onAction) {
    const el = h('div', { class: 'pc' });
    let longTimer = null, longFired = false;
    el.addEventListener('pointerdown', (e) => {
      const v = e.target.closest('.val');
      if (!v) return;
      longFired = false;
      longTimer = setTimeout(() => { longFired = true; P.vibrate(20); onAction('clear', +v.dataset.w); }, 450);
    });
    const cancel = () => { clearTimeout(longTimer); };
    el.addEventListener('pointerup', cancel);
    el.addEventListener('pointerleave', cancel);
    el.addEventListener('pointercancel', cancel);
    el.addEventListener('contextmenu', (e) => e.preventDefault());
    el.addEventListener('click', (e) => {
      const b = e.target.closest('[data-a]');
      if (!b) return;
      if (b.classList.contains('val')) { if (!longFired) P.toast('Mantén pulsado el número para borrarlo'); longFired = false; return; }
      P.vibrate(6);
      onAction(b.dataset.a, +(b.dataset.w || 0));
    });

    return {
      el,
      render(c) {
        const k = c.k || 1, kb = Math.min(k, 1.3), H = c.height || 84;
        el.style.setProperty('--k', k);
        el.style.background = `linear-gradient(${P.mix(c.color, 0.1)}, ${P.mix(c.color, -0.2)})`;
        el.classList.toggle('out', !!c.out);
        const parts = [];
        parts.push(h('div', { class: 'nm' }, h('span', null, c.name), c.dealer ? P.icon('deck', null, Math.round(19 * k)) : null));
        parts.push(h('div', { class: 'af' }, 'A: ' + c.hits + '   F: ' + c.misses));
        parts.push(h('div', { class: 'sc' + (c.yellow ? ' y' : '') }, P.num(c.score)));
        if (c.delta != null) parts.push(h('div', { class: 'dl ' + (c.yellow ? 'y' : c.delta >= 0 ? 'pos' : 'neg') }, P.signed(c.delta)));
        if (c.auction && !c.out) {
          const bh = 26 * kb, aucH = 31 * k;
          let top = (c.full && c.editable ? 31 : 36) * k;
          if (c.editable) top = Math.max(26 * k, Math.min(top, H - bh - 4 * k - 4 - aucH));
          const a = h('button', {
            class: 'auc' + (c.won ? ' won' : '') + (!c.editable && !c.won ? ' faint' : ''), style: { top: top + 'px' },
            'data-a': c.editable ? 'auction' : null, disabled: !c.editable, 'aria-label': 'Se llevó la subasta',
          }, h('i', null, c.won ? P.icon('check', '#fff', Math.round(13 * k)) : null), 'Subasta');
          parts.push(a);
        }
        if (c.out) parts.push(h('div', { class: 'pill' }, 'No juega el desempate'));
        else if (!c.editable) { if (c.readText) parts.push(h('div', { class: 'pill' }, c.readText)); }
        else if (!c.full) parts.push(stepper([[null, c.v, 0]], false));
        else parts.push(stepper([['Pide', c.bid, 0], ['Hace', c.made, 1]], true));
        el.replaceChildren(...parts);

        function stepper(groups, full) {
          const row = h('div', { class: 'ctl' + (full ? ' full' : '') });
          for (const [lb, v, w] of groups) {
            if (lb) row.appendChild(h('span', { class: 'lb' }, lb));
            const ic = Math.round(15 * kb);
            row.appendChild(h('button', { 'data-a': 'minus', 'data-w': w, 'aria-label': 'Menos' }, P.icon('minus', '#fff', ic)));
            const has = v != null && !isNaN(v);
            row.appendChild(h('button', { class: 'val' + (has ? '' : ' e'), 'data-a': 'val', 'data-w': w, 'aria-label': 'Valor' }, has ? (full ? String(v) : P.num(v)) : '—'));
            row.appendChild(h('button', { 'data-a': 'plus', 'data-w': w, 'aria-label': 'Más' }, P.icon('plus', '#fff', ic)));
          }
          return row;
        }
      },
    };
  }

  // ------------------------------------------------------------------ pantalla de partida

  /** review = revisión de una partida cerrada desde el historial. */
  function GameScreen(g, review, startIdx, startEditing) {
    const S = { g, review, vi: startIdx || 0, editing: false, editSnap: null };
    let strip, label, mults, banner, cardsBox, footer, cards = [], ro;

    const maxSel = () => review ? Math.max(0, E.savedCount(g) - 1) : Math.max(0, Math.min(E.current(g), g.rounds.length - 1));
    const rd = () => g.rounds[S.vi];
    const editable = () => S.editing || (!review && !rd().saved && S.vi === E.current(g));
    if (startEditing) { S.editing = true; S.editSnap = E.snap(g.rounds[S.vi]); }

    S.build = () => {
      S.vi = Math.max(0, Math.min(S.vi, maxSel()));
      const root = h('div', { class: 'screen' });
      if (!review) {
        const sub = 'Partida en curso · ' + g.order.length + ' jugadores' + (g.mode === E.FULL ? ' · completo' : '');
        root.appendChild(P.header(h('img', { class: 'logo', src: 'icons/poch-256.png', alt: '' }), 'Pocha', sub, P.ibtn('dots', '#fff', 22, openMenu, 'Menú')));
      } else {
        root.appendChild(P.header(P.ibtn('back', '#fff', 24, leave, 'Atrás'), 'Rondas de la partida', P.gameTitle(g) + ' · ' + P.date(g.created)));
      }
      strip = RoundStrip((i) => {
        if (S.editing) { P.toast('Guarda o cancela la corrección antes de cambiar de ronda'); render(); return; }
        S.vi = i; render();
      });
      root.appendChild(strip.el);
      label = h('div', { class: 'rlabel' });
      mults = h('div', { class: 'mults' });
      banner = h('div', { class: 'banner gbanner' });
      cardsBox = h('div', { class: 'cards' });
      footer = h('div', { class: 'foot' });
      root.append(label, mults, banner, cardsBox, footer);
      cards = g.order.map((pid) => {
        const c = playerCard((act, w) => onCard(pid, act, w));
        cardsBox.appendChild(c.el);
        return c;
      });
      ro = new ResizeObserver(() => { sizeCards(); renderCards(); });
      ro.observe(cardsBox);
      render();
      return root;
    };
    S.onBack = () => { if (S.editing) { cancelEdit(); return true; } return false; };
    S.leave = () => { if (ro) ro.disconnect(); };

    function leave() { if (S.editing) cancelEdit(); else P.pop(); }

    let cardH = 84, cardK = 1;
    function sizeCards() {
      const n = cards.length, full = g.mode === E.FULL;
      const gap = n <= 4 ? 10 : n <= 6 ? 8 : 6;
      const avail = cardsBox.clientHeight - 4;
      const per = (avail - gap * (n - 1)) / n;
      cardH = Math.max(full ? 70 : 62, Math.min(full ? 150 : 140, per));
      cardK = Math.max(0.7, Math.min(1.6, cardH / (full ? 92 : 84)));
      cards.forEach((c, i) => { c.el.style.height = cardH + 'px'; c.el.style.marginBottom = (i < n - 1 ? gap : 0) + 'px'; });
    }

    function render() {
      const r = rd();
      strip.set(g.rounds.map((x) => x.cards), g.rounds.map(E.edited), maxSel(), S.vi);
      label.replaceChildren();
      if (r.tiebreak) {
        const k = g.rounds.slice(0, S.vi + 1).filter((x) => x.tiebreak).length;
        label.append('Desempate ' + k + ' · 1 carta');
      } else label.append('Ronda ' + (S.vi + 1) + ' de ' + E.regularCount(g) + ' · ' + P.plural(r.cards, 'carta', 'cartas'));
      if (S.editing) label.append(h('span', { class: 'y' }, '  · corrigiendo'));
      else if (E.edited(r)) label.append(h('span', { class: 'y' }, '  · corregida'));
      renderMults();
      if (r.tiebreak) {
        banner.style.display = 'flex';
        banner.replaceChildren(P.icon('crown', '#F2B41F', 19), 'Desempate: ' + P.joinNames(E.players(g, r).map((p) => g.names[p])));
      } else if (r.auction) {
        banner.style.display = 'flex';
        banner.replaceChildren(P.icon('gavel', null, 19), editable() ? 'Ronda subastada · el ganador elige triunfo'
          : r.winner ? 'Ronda subastada · se la llevó ' + g.names[r.winner] : 'Ronda subastada');
      } else banner.style.display = 'none';
      renderCards();
      renderFooter();
    }

    function renderMults() {
      mults.replaceChildren();
      const r = rd();
      E.SUITS.forEach((suit, i) => {
        if (!g.rules.multOn[i]) return;
        const on = r.mult === suit;
        const color = P.SUIT_COLOR[suit];
        const b = h('button', {
          class: 'mult' + (on ? ' on' : '') + (!editable() && !on ? ' faded' : ''),
          style: on ? { borderColor: color, background: 'rgba(60,45,10,.55)' } : null,
          onclick: () => onMult(suit),
        }, P.icon(P.SUIT_ICON[suit], color, 22), E.SUIT_NAMES[i] + ' ×' + g.rules.multF[i], h('span', { class: 'sw' }));
        mults.appendChild(b);
      });
      mults.style.display = mults.children.length ? 'flex' : 'none';
    }

    function renderCards() {
      const r = rd(), full = g.mode === E.FULL, ed = editable(), dealer = E.dealer(g, S.vi);
      const changed = E.editedPlayers(g, r);
      g.order.forEach((p, i) => {
        const x = r.res && r.res[p];
        const af = E.af(g, S.vi, p);
        cards[i].render({
          k: cardK, height: cardH, name: g.names[p], color: g.colors[p], score: E.total(g, S.vi, p), delta: E.points(g, r, p),
          hits: af[0], misses: af[1], dealer: p === dealer, full, auction: r.auction, won: p === r.winner,
          v: x && x.v, bid: x && x.b, made: x && x.m, out: !E.plays(r, p), editable: ed,
          yellow: !S.editing && changed.has(p), readText: readText(g, x),
        });
      });
    }

    function renderFooter() {
      footer.replaceChildren();
      const r = rd();
      if (S.editing) {
        const pr = E.problems(g, r);
        footer.appendChild(h('div', { class: 'hint' }, pr.length ? pr[0] : 'Revisa los cambios y guárdalos'));
        footer.appendChild(h('div', { class: 'row' }, h('div', { class: 'grow' }, P.secondary('Cancelar', '#B4BFD2', cancelEdit)),
          h('div', { style: { flex: '1.4' } }, P.primary('Guardar cambios', commitEdit, pr.length === 0))));
        return;
      }
      if (editable()) {
        const pr = E.problems(g, r);
        footer.appendChild(h('div', { class: 'hint' }, pr.length ? pr[0] : ''));
        footer.appendChild(P.primary('Guardar ronda', saveRound, pr.length === 0));
        return;
      }
      let info = '';
      if (E.edited(r)) info = 'Corregida el ' + P.dateTime(Math.max.apply(null, r.revs.map((x) => x.at)));
      else if (r.savedAt) info = 'Guardada el ' + P.dateTime(r.savedAt);
      footer.appendChild(h('div', { class: 'hint m' }, info));
      const row = h('div', { class: 'row' });
      row.appendChild(h('div', { class: 'grow' }, P.secondary('Editar ronda', '#FFC83A', startEdit, 'edit')));
      if (E.canUndo(r)) row.appendChild(h('div', { class: 'grow' }, P.secondary('Deshacer', '#B4BFD2', undo, 'undo')));
      if (!review) {
        let go;
        if (E.tieAtEnd(g) && g.rules.tie !== E.TIE_SHARED) go = P.primary('Resolver empate', askTie);
        else if (E.finished(g)) go = P.primary('Clasificación', () => P.push(FinalScreen(g)));
        else go = P.primary('Ronda actual', () => { S.vi = E.current(g); render(); });
        row.appendChild(h('div', { class: 'grow' }, go));
      }
      footer.appendChild(row);
    }

    // -------------------------------------------------------------- acciones

    function changed() {
      if (!S.editing && !review) P.save();
      renderCards();
      renderFooter();
    }
    function guard() {
      if (editable()) return true;
      P.toast('Pulsa «Editar ronda» para corregir esta ronda');
      return false;
    }
    function onCard(p, act, w) {
      if (!guard()) return;
      const r = rd(), x = E.res(r, p);
      if (act === 'auction') { if (r.winner === p) delete r.winner; else r.winner = p; }
      else if (act === 'clear') { if (g.mode === E.FULL) { if (w === 0) delete x.b; else delete x.m; } else delete x.v; }
      else {
        const d = act === 'minus' ? -1 : 1;
        if (g.mode === E.FULL) { if (w === 0) x.b = E.stepFull(x.b, d, r.cards); else x.m = E.stepFull(x.m, d, r.cards); }
        else x.v = E.stepSimple(x.v, d, r.cards);
      }
      changed();
    }
    function onMult(suit) {
      if (!guard()) return;
      const r = rd();
      if (r.mult === suit) delete r.mult; else r.mult = suit;
      renderMults();
      changed();
    }
    function saveRound() {
      const r = rd();
      if (!E.canSave(g, r)) return;
      E.save(g, r, Date.now(), false);
      P.save();
      P.updateChrome();
      if (E.tieAtEnd(g)) { S.vi = Math.min(E.current(g), g.rounds.length - 1); render(); askTie(); return; }
      if (E.finished(g)) { P.push(FinalScreen(g)); return; }
      S.vi = E.current(g);
      render();
      cardsBox.scrollTop = 0;
    }
    function askTie() {
      const lead = E.leaders(g);
      const again = g.rounds[g.rounds.length - 1].tiebreak;
      P.dialog(again ? 'Sigue el empate' : 'Empate en cabeza',
        P.joinNames(lead.map((p) => g.names[p])) + (again ? ' siguen empatados a ' : ' empatan a ') + E.totalAll(g, lead[0])
        + ' puntos. ¿Qué hacéis?\n\nDesempatar añade una ronda de una carta solo entre ellos.', null, [
          { label: 'Compartir victoria', run: () => { E.setTieRule(g, E.TIE_SHARED); P.save(); P.push(FinalScreen(g)); } },
          { label: 'Desempatar', style: 'main', run: () => { E.setTieRule(g, E.TIE_BREAK); P.save(); S.vi = Math.min(E.current(g), g.rounds.length - 1); render(); P.updateChrome(); } },
        ]);
    }
    function startEdit() { S.editing = true; S.editSnap = E.snap(rd()); render(); }
    function cancelEdit() { E.applySnap(rd(), S.editSnap); S.editing = false; render(); }
    function commitEdit() {
      const r = rd();
      if (!E.canSave(g, r)) return;
      S.editing = false;
      if (E.commitEdit(g, r, S.editSnap, Date.now())) {
        P.save(); P.toast('Ronda corregida · totales recalculados');
        if (g.status !== E.ACTIVE) { P.markGame(g); P.syncQuiet(true); }
      }
      else P.toast('No había cambios');
      render();
      P.updateChrome();
    }
    function undo() {
      if (E.undo(g, rd(), Date.now())) {
        P.save(); P.toast('Corrección deshecha'); render();
        if (g.status !== E.ACTIVE) { P.markGame(g); P.syncQuiet(true); }
      }
    }

    function openMenu() {
      const left = g.rounds.length - E.savedCount(g), done = E.finished(g);
      P.menu([
        { icon: 'play', color: '#3A92FF', label: 'Continuar partida', run: () => {} },
        {
          icon: 'flag', color: '#F28B24', label: 'Finalizar partida', sub: done ? 'Ver la clasificación final' : 'Faltan ' + P.plural(left, 'ronda', 'rondas'), enabled: done,
          run: () => done ? P.push(FinalScreen(g)) : P.info('Aún quedan rondas', 'Faltan ' + P.plural(left, 'ronda', 'rondas') + '. Si no podéis terminar, usa «Finalizar anticipadamente»: se guardan las rondas jugadas, sin ganador.'),
        },
        { icon: 'pause', color: '#F2B41F', label: 'Finalizar anticipadamente', sub: 'Sin victoria para nadie', run: finishEarly },
        { icon: 'sliders', color: '#B4BFD2', label: 'Reglas de esta partida', run: () => P.push(P.RulesView(g)) },
        { icon: 'trash', color: 'var(--red)', label: 'Descartar partida', run: discard },
      ]);
    }
    function finishEarly() {
      P.confirm('Finalizar anticipadamente', 'Se guardan las ' + P.plural(E.savedCount(g), 'ronda jugada', 'rondas jugadas')
        + ' y cuentan para las estadísticas, pero nadie gana la partida.\n\nLa ronda que estaba a medias no se guarda.', 'Finalizar', false, () => {
        for (const r of g.rounds) if (!r.saved) { r.res = {}; delete r.winner; delete r.mult; }
        g.status = E.EARLY; g.closed = true; g.ended = Date.now();
        st.activeGame = null;
        P.save();
        P.markGame(g); P.syncQuiet(true);
        P.popToRoot(0);
        P.openIn(1, P.HistoryDetail(g.id));
        P.toast('Partida guardada sin victoria');
      });
    }
    function discard() {
      P.confirm('Descartar partida', 'Se borrará la partida en curso con todas sus rondas. No contará en el historial ni en las estadísticas.', 'Descartar', true, () => {
        st.games = st.games.filter((x) => x !== g);
        st.activeGame = null;
        P.save();
        P.popToRoot(0);
        P.refresh();
        P.toast('Partida descartada');
      });
    }
    S.game = g;
    return S;
  }
  P.GameScreen = GameScreen;

  /** Raíz de la pestaña Partida: el marcador si hay partida, o la portada si no. */
  P.GameTab = function () {
    const S = { inner: null, jump: false };
    S.build = () => {
      const g = P.active();
      if (g) {
        if (!S.inner || S.inner.game !== g) S.inner = GameScreen(g, false, E.current(g));
        else if (!S.inner.editing) S.inner.vi = S.jump ? Math.min(E.current(g), g.rounds.length - 1) : Math.min(S.inner.vi, g.rounds.length - 1);
        S.jump = false;
        return S.inner.build();
      }
      S.inner = null;
      return home();
    };
    S.onBack = () => !!(S.inner && S.inner.onBack());
    S.leave = () => { if (S.inner) S.inner.leave(); };

    function home() {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.header(h('img', { class: 'logo', src: 'icons/poch-256.png', alt: '' }), 'Pocha', 'Sin partida en curso'));
      const body = h('div', { class: 'body' });
      body.appendChild(h('div', { class: 'hero' }, h('img', { src: 'icons/poch-256.png', alt: 'Poch!' }),
        h('h2', null, 'Marcador de La Pocha'),
        h('p', null, 'Apunta cada ronda con pocos toques: la app calcula los puntos, los repartos y las subastas.'),
        P.primary('Nueva partida', () => P.push(Setup())),
        (st.groups || []).length ? null : h('button', { class: 'joinlink', onclick: () => P.push(P.JoinScreen('')) },
          'No estás en ningún grupo.', h('br'), h('b', null, '¿Quieres unirte?'))));
      const recent = P.byDate();
      if (recent.length) {
        body.appendChild(P.section('Últimas partidas'));
        recent.slice(0, 3).forEach((g) => body.appendChild(P.gameRow(g, () => P.openIn(1, P.HistoryDetail(g.id)))));
      } else if (!st.players.length) {
        body.appendChild(h('div', { class: 'note', style: { textAlign: 'center', fontSize: '13.5px', marginTop: '16px' } },
          'Al crear la partida podrás añadir a los jugadores. Se guardan para las siguientes y para sus estadísticas.'));
      }
      if (!P.standalone()) body.appendChild(P.installHint());
      root.appendChild(h('div', { class: 'scroll' }, body));
      return root;
    }
    return S;
  };

  // ------------------------------------------------------------------ clasificación final

  const MEDALS = ['#F2B41F', '#C9D2DE', '#D3874A'];
  P.standings = (g) => {
    const box = h('div');
    const early = g.status === E.EARLY;
    for (const r of E.standings(g)) {
      const medal = !early && r.pos <= 3 ? MEDALS[r.pos - 1] : null;
      const af = E.af(g, g.rounds.length - 1, r.pid);
      box.appendChild(h('div', {
        class: 'rank', style: medal ? { background: `linear-gradient(90deg, ${medal}6b, ${medal}1a)`, borderColor: medal + '8c' } : null,
      }, h('div', { class: 'medal', style: medal ? { background: `linear-gradient(${P.mix(parseInt('ff' + medal.slice(1), 16) | 0, 0.3)}, ${medal})` } : null }, String(r.pos)),
      P.dot(g.colors[r.pid], 12),
      h('div', { style: { flex: '1', minWidth: '0' } }, h('div', { class: 'ell', style: { fontSize: '16.5px', fontWeight: '600' } }, g.names[r.pid]),
        h('div', { class: 'ell text2', style: { fontSize: '12.5px', marginTop: '3px' } }, P.plural(af[0], 'acierto', 'aciertos') + ' · ' + P.plural(af[1], 'fallo', 'fallos'))),
      h('div', { class: 'pts' }, P.num(r.total))));
    }
    return box;
  };

  function FinalScreen(g) {
    const S = {};
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Clasificación final', P.pop));
      const w = E.winners(g), wn = w.map((p) => g.names[p]);
      const body = h('div', { class: 'body' });
      body.appendChild(h('div', { class: 'card row', style: { padding: '14px', gap: '14px' } },
        h('div', { style: { width: '58px', height: '58px', borderRadius: '50%', background: '#2F2410', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: '0' } }, P.icon('crown', '#F2B41F', 34)),
        h('div', { class: 'grow' }, h('div', { style: { fontSize: '19px', fontWeight: '700' } }, w.length === 1 ? 'Gana ' + wn[0] : 'Victoria compartida'),
          h('div', { class: 'text2', style: { fontSize: '13.5px', marginTop: '4px' } }, (w.length > 1 ? P.joinNames(wn) + ' · ' : '') + g.order.length + ' jugadores · ' + P.plural(E.savedCount(g), 'ronda', 'rondas')),
          h('div', { class: 'muted', style: { fontSize: '13px', marginTop: '3px' } }, P.dateTime(g.created)))));
      body.appendChild(P.standings(g));
      body.appendChild(P.note('Antes de cerrarla puedes revisar y corregir cualquier ronda. Al cerrarla pasa al historial y cuenta para las estadísticas.'));
      root.appendChild(h('div', { class: 'scroll' }, body));
      const foot = h('div', { class: 'foot' });
      if (w.length > 1) foot.appendChild(h('div', { style: { marginBottom: '8px' } }, P.secondary('Mejor desempatar', '#F2B41F', () => {
        E.setTieRule(g, E.TIE_BREAK); P.save(); P.gameTab.jump = true; P.pop(); P.toast('Ronda de desempate a una carta');
      }, 'crown')));
      foot.appendChild(h('div', { class: 'row' }, h('div', { class: 'grow' }, P.secondary('Revisar rondas', '#B4BFD2', P.pop)),
        h('div', { style: { flex: '1.2' } }, P.primary('Cerrar partida', () => {
          g.status = E.COMPLETED; g.closed = true; g.ended = Date.now();
          st.activeGame = null;
          P.save();
          P.markGame(g); P.syncQuiet(true);
          P.popToRoot(0);
          P.openIn(1, P.HistoryDetail(g.id));
          P.toast('Partida guardada en el historial');
        }))));
      root.appendChild(foot);
      return root;
    };
    return S;
  }

  // ------------------------------------------------------------------ asistente de nueva partida

  function Setup() {
    const S = { step: 0, sel: [], mode: E.SIMPLE, customized: false };
    const last = P.byDate()[0];
    if (last) { S.sel = last.order.filter((p) => P.player(p)); S.mode = last.mode; }
    S.rules = E.copy(P.defaultRules());
    S.profileId = S.rules.id;
    S.group = P.groupById(st.lastGroup) ? st.lastGroup : ((st.groups || [])[0] || {}).id || null;

    S.onBack = () => { if (S.step > 0) { S.step--; S.scrollY = 0; P.refresh(); return true; } return false; };

    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Nueva partida', () => { if (!S.onBack()) P.pop(); }));
      root.appendChild(stepsBar());
      const body = h('div', { class: 'body' });
      if (S.step === 0) players(body); else if (S.step === 1) rulesStep(body); else summary(body);
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      const problem = S.step === 0 && S.sel.length < E.MIN_PLAYERS ? 'Elige al menos 2 jugadores' : null;
      root.appendChild(h('div', { class: 'foot' }, problem ? h('div', { class: 'hint' }, problem) : null,
        P.primary(S.step < 2 ? 'Siguiente' : 'Empezar partida', () => {
          if (S.step < 2) { S.step++; S.scrollY = 0; P.refresh(); } else start();
        }, !problem)));
      return root;
    };

    function stepsBar() {
      const bar = h('div', { class: 'steps' });
      ['Jugadores', 'Reglas', 'Resumen'].forEach((l, i) => {
        if (i) bar.appendChild(h('div', { class: 'ln' + (i <= S.step ? ' on' : '') }));
        const done = i < S.step;
        bar.appendChild(h('button', { class: 'st' + (i === S.step ? ' cur' : '') + (done ? ' done' : ''), disabled: !done, onclick: () => { S.step = i; P.refresh(); } },
          h('span', { class: 'n' }, done ? P.icon('check', '#fff', 16) : String(i + 1)), l));
      });
      return bar;
    }

    function players(body) {
      if ((st.groups || []).length) {
        body.appendChild(h('div', { class: 'text2', style: { fontSize: '13.5px', fontWeight: '500', margin: '10px 2px 8px' } }, 'Grupo de la partida'));
        body.appendChild(h('div', { class: 'chips' }, st.groups.map((x) => P.chip(x.name, S.group === x.id, () => { S.group = x.id; P.refresh(); }, 'people')),
          P.chip('Sin grupo', !S.group, () => { S.group = null; P.refresh(); })));
        body.appendChild(h('div', { class: 'note', style: { margin: '6px 2px 0' } }, S.group ? 'Al cerrarla, se compartirá con el grupo.' : 'Solo se guardará en este móvil.'));
      }
      body.appendChild(h('div', { style: { textAlign: 'center', fontSize: '24px', fontWeight: '700', margin: '14px 0 8px' } }, 'Orden de reparto'));
      body.appendChild(h('div', { class: 'text2', style: { textAlign: 'center', fontSize: '14px', lineHeight: '1.5', padding: '0 8px 16px' } },
        'Arrastra ≡ para cambiar el orden.', h('br'), 'Se reparte según estos números: el 1 reparte la primera ronda, luego el 2, y así sucesivamente.'));
      const box = h('div');
      body.appendChild(box);
      const count = h('div', { class: 'text2', style: { fontSize: '13px', margin: '4px 2px 0' } });
      body.appendChild(count);
      const renderSel = () => {
        box.replaceChildren();
        if (!S.sel.length) box.appendChild(h('div', { class: 'card muted', style: { textAlign: 'center', padding: '20px 16px' } }, 'Todavía no hay jugadores en la partida. Añádelos abajo.'));
        S.sel.forEach((pid, i) => box.appendChild(selRow(pid, i, box, renderSel)));
        if (S.sel.length >= E.MIN_PLAYERS) {
          const seq = E.sequence(S.rules, S.sel.length);
          count.textContent = S.sel.length + ' jugadores · máximo ' + P.plural(Math.max.apply(null, seq), 'carta', 'cartas') + ' · ' + seq.length + ' rondas';
        } else count.textContent = '';
      };
      renderSel();
      body.appendChild(P.section('Añadir jugadores'));
      const flow = h('div', { class: 'flow' });
      for (const p of P.sorted()) {
        if (S.sel.indexOf(p.id) >= 0) continue;
        flow.appendChild(h('button', { class: 'pchip', onclick: () => {
          if (S.sel.length >= E.MAX_PLAYERS) { P.toast('Máximo ' + E.MAX_PLAYERS + ' jugadores'); return; }
          S.sel.push(p.id); P.refresh();
        } }, P.dot(p.color, 18), p.name));
      }
      flow.appendChild(h('button', { class: 'pchip add', onclick: () => P.prompt('Nuevo jugador', 'Nombre', '', 'Añadir', (name) => {
        if (!name) return;
        if (P.nameTaken(name)) { P.toast('Ya hay un jugador llamado ' + name); return; }
        const p = { id: P.newId('j'), name, color: P.nextColor(), created: Date.now() };
        st.players.push(p); P.save();
        if (S.sel.length < E.MAX_PLAYERS) S.sel.push(p.id);
        P.refresh();
      }) }, P.icon('plus', 'var(--blue-text)', 18), 'Nuevo jugador'));
      body.appendChild(flow);
    }

    function selRow(pid, i, box, renderSel) {
      const p = P.player(pid);
      const handle = h('div', { class: 'handle', 'aria-label': 'Arrastrar' }, P.icon('drag', 'var(--muted)', 22));
      const row = h('div', { class: 'prow' }, handle,
        h('span', { class: 'muted', style: { fontSize: '13px', fontWeight: '600', minWidth: '16px' } }, String(i + 1)),
        h('span', { style: { width: '6px' } }), P.dot(p ? p.color : 0, 26),
        h('div', { class: 'ell', style: { flex: '1', fontSize: '16px', fontWeight: '600', padding: '0 6px 0 12px' } }, p ? p.name : '?'),
        i === 0 ? h('span', { class: 'row', style: { gap: '4px', background: 'var(--surface3)', borderRadius: '8px', padding: '3px 8px 3px 6px', fontSize: '12px', fontWeight: '600' } }, P.icon('deck', null, 16), 'Reparte') : null,
        P.ibtn('close', 'var(--muted)', 18, () => { S.sel.splice(S.sel.indexOf(pid), 1); P.refresh(); }, 'Quitar'));
      let startY = 0, from = 0, target = 0, slot = 68;
      handle.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        handle.setPointerCapture(e.pointerId);
        startY = e.clientY; from = S.sel.indexOf(pid); target = from;
        slot = row.offsetHeight + 8;
        row.classList.add('dragging');
      });
      handle.addEventListener('pointermove', (e) => {
        if (!row.classList.contains('dragging')) return;
        const n = S.sel.length;
        let dy = Math.max(-from * slot, Math.min((n - 1 - from) * slot, e.clientY - startY));
        row.style.transform = `translateY(${dy}px) scale(1.02)`;
        const t = Math.max(0, Math.min(n - 1, from + Math.round(dy / slot)));
        if (t !== target) { target = t; P.vibrate(4); }
        [...box.children].forEach((o, j) => {
          if (j === from) return;
          let shift = 0;
          if (from < t && j > from && j <= t) shift = -slot;
          if (t < from && j >= t && j < from) shift = slot;
          o.style.transform = shift ? `translateY(${shift}px)` : '';
        });
      });
      const end = () => {
        if (!row.classList.contains('dragging')) return;
        row.classList.remove('dragging');
        if (target !== from) { const id = S.sel.splice(from, 1)[0]; S.sel.splice(target, 0, id); }
        setTimeout(renderSel, 0);
      };
      handle.addEventListener('pointerup', end);
      handle.addEventListener('pointercancel', end);
      return row;
    }

    function rulesStep(body) {
      body.appendChild(P.section('Perfil de reglas'));
      for (const r of st.profiles) {
        body.appendChild(P.option(r.name + (r.id === st.defaultProfile ? '  ★' : ''), P.rulesSummary(r), !S.customized && r.id === S.profileId,
          () => { S.rules = E.copy(r); S.profileId = r.id; S.customized = false; P.refresh(); }));
      }
      if (S.customized) body.appendChild(P.option('Personalizadas para esta partida', P.rulesSummary(S.rules), true, () => {}));
      body.appendChild(P.secondary('Personalizar reglas', '#4AA0FF', () => P.push(P.RulesEditor(E.copy(S.rules), false, (r) => { S.rules = r; S.customized = true; })), 'sliders'));
      body.appendChild(P.section('Modo de anotación'));
      body.appendChild(P.option('Sencillo', 'Un número por jugador: cuántas hizo si acierta (0 también es acierto) o por cuántas falló. Lo más rápido.',
        S.mode === E.SIMPLE, () => { S.mode = E.SIMPLE; P.refresh(); }));
      body.appendChild(P.option('Completo', 'Pedidas y hechas de cada jugador. Comprueba las sumas y da estadísticas de bazas.',
        S.mode === E.FULL, () => { S.mode = E.FULL; P.refresh(); }));
      body.appendChild(h('div', { class: 'banner gold', style: { marginTop: '4px' } }, P.icon('info', '#F2B41F', 20), 'El modo no se puede cambiar una vez empezada la partida.'));
    }

    function summary(body) {
      const n = S.sel.length;
      body.appendChild(P.section('Jugadores y orden'));
      const pc = h('div', { class: 'card' });
      S.sel.forEach((id, i) => {
        const p = P.player(id);
        pc.appendChild(h('div', { class: 'row', style: { padding: '7px 0' } }, h('span', { class: 'muted', style: { minWidth: '24px', fontWeight: '600' } }, (i + 1) + '.'),
          P.dot(p.color, 18), h('div', { class: 'grow ell', style: { fontSize: '15.5px', fontWeight: '500' } }, p.name),
          i === 0 ? h('span', { class: 'row text2', style: { gap: '6px', fontSize: '12.5px', fontWeight: '500' } }, P.icon('deck', null, 18), 'Reparte la 1.ª ronda') : null));
      });
      body.appendChild(pc);
      const seq = E.sequence(S.rules, n), max = Math.max.apply(null, seq);
      body.appendChild(P.section('Rondas'));
      let auc = 0;
      const balls = h('div', { class: 'balls' });
      for (const c of seq) { const a = S.rules.auctionMax && c === max; if (a) auc++; balls.appendChild(h('span', { class: a ? 'a' : '' }, String(c))); }
      body.appendChild(h('div', { class: 'card' }, h('div', { style: { fontSize: '15px', fontWeight: '600' } }, seq.length + ' rondas · máximo ' + P.plural(max, 'carta', 'cartas') + ' por jugador'),
        balls, h('div', { class: 'note', style: { margin: '6px 0 0' } }, auc ? 'En azul, las ' + auc + ' rondas subastadas: quien se lleva la subasta elige el triunfo.' : 'Sin rondas subastadas.')));
      body.appendChild(P.section('Reglas' + (S.customized ? ' (personalizadas)' : ' · ' + S.rules.name)));
      const kc = h('div', { class: 'card', style: { padding: '4px 14px' } });
      for (const [k, v] of P.rulesLines(S.rules, S.mode, n)) kc.appendChild(P.kv(k, v));
      body.appendChild(kc);
    }

    function start() {
      const names = {}, colors = {};
      for (const id of S.sel) { const p = P.player(id); names[id] = p.name; colors[id] = p.color; }
      const r = E.copy(S.rules);
      if (S.customized) r.name = 'Personalizadas';
      const g = E.newGame(P.newId('g'), r, S.mode, S.sel, names, colors, Date.now());
      if (S.group) { g.group = S.group; st.lastGroup = S.group; }
      st.games.push(g);
      st.activeGame = g.id;
      P.save();
      P.popToRoot(0);
      P.refresh();
    }
    return S;
  }
  P.Setup = Setup;
})();
