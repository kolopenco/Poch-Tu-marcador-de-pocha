/* Poch! web · historial, jugadores, estadísticas y ajustes. */
(function () {
  'use strict';
  const P = window.P, E = window.Engine, h = P.h, st = P.st;

  // ------------------------------------------------------------------ textos de reglas

  P.rulesMults = (r) => {
    const on = E.SUITS.map((s, i) => r.multOn[i] ? E.SUIT_NAMES[i] + ' ×' + r.multF[i] : null).filter(Boolean);
    return on.length ? on.join(', ') : 'Sin multiplicadores';
  };
  P.rulesSummary = (r) => 'Acierto ' + r.base + ' + ' + r.perTrick + '/baza · Fallo −' + r.penalty + '/baza · ' + P.rulesMults(r);
  P.rulesLines = (r, mode, n) => {
    const l = [];
    if (mode) l.push(['Modo de anotación', mode === E.FULL ? 'Completo (pedidas y hechas)' : 'Sencillo']);
    l.push(['Baraja', 'Española de ' + r.deck + ' cartas']);
    if (n > 0) {
      const seq = E.sequence(r, n), max = Math.max.apply(null, seq);
      const auc = r.auctionMax ? seq.filter((c) => c === max).length : 0;
      l.push(['Rondas', seq.length + ' · máximo ' + P.plural(max, 'carta', 'cartas')]);
      l.push(['Rondas subastadas', auc ? String(auc) : 'Ninguna']);
    }
    l.push(['Secuencia', r.seqType === 'custom' ? r.customSeq : 'Sube, repite el máximo (' + (r.repeatMax > 0 ? P.plural(r.repeatMax, 'vez', 'veces') : 'una por jugador') + ') y baja']);
    l.push(['Puntos base por acierto', String(r.base)], ['Puntos por baza', String(r.perTrick)], ['Penalización por diferencia', String(r.penalty)]);
    E.SUITS.forEach((s, i) => l.push([E.SUIT_NAMES[i], r.multOn[i] ? '×' + r.multF[i] + (r.multBase ? ' (también la base)' : ' (solo bazas y penalizaciones)') : 'Desactivado']));
    l.push(['Prohibir que las pedidas sumen las bazas', r.forbidSum ? 'Sí' : 'No']);
    l.push(['Empates', r.tie === E.TIE_BREAK ? 'Desempate a 1 carta' : 'Victoria compartida']);
    return l;
  };

  // ------------------------------------------------------------------ historial

  function gameIcon(g) {
    let inner, bg = null;
    if (g.status === E.ACTIVE) { inner = P.icon('sync', '#fff', 22); bg = 'var(--surface3)'; }
    else if (g.status === E.EARLY) { inner = P.icon('pause', '#fff', 22); bg = 'var(--surface3)'; }
    else inner = P.icon('trophy', E.winners(g).length > 1 ? '#C9D2DE' : '#F2B41F', 34);
    return h('span', { style: { width: '44px', height: '44px', borderRadius: '50%', background: bg || 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: '0' } }, inner);
  }
  P.gameRow = (g, run) => {
    const live = g.status === E.ACTIVE;
    return h('button', { class: 'lrow' + (live ? ' live' : ''), onclick: run }, gameIcon(g),
      h('div', { style: { flex: '1', minWidth: '0' } },
        live ? h('span', { class: 'tag', style: { background: 'var(--green)', marginBottom: '4px' } }, 'En curso') : null,
        h('div', { class: 't ell' }, live ? 'Partida actual' : P.gameTitle(g)),
        h('div', { class: 'ell text2', style: { fontSize: '13px', marginTop: '3px' } }, P.gameSub(g)),
        h('div', { class: 'ell muted', style: { fontSize: '12.5px', marginTop: '3px' } }, P.dateTime(g.created))),
      P.icon('chev', 'var(--text2)', 20));
  };

  P.History = function () {
    const S = { filter: 0 };
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.header(P.icon('list', '#fff', 26), 'Historial'));
      root.appendChild(h('div', { style: { margin: '0 16px 10px' } }, P.seg(['Todas', 'Finalizadas', 'Incompletas'], S.filter, (i) => { S.filter = i; S.scrollY = 0; P.refresh(); })));
      const body = h('div', { class: 'body', style: { paddingTop: '2px' } });
      let n = 0;
      for (const g of P.byDate()) {
        const done = g.status === E.COMPLETED;
        if ((S.filter === 1 && !done) || (S.filter === 2 && done)) continue;
        n++;
        body.appendChild(P.gameRow(g, () => {
          if (g.status === E.ACTIVE) { P.popToRoot(0); P.selectTab(0); } else P.push(P.HistoryDetail(g.id));
        }));
      }
      if (!n) body.appendChild(h('div', { class: 'empty' }, S.filter === 0 ? 'Aún no hay partidas. Cuando terminéis la primera aparecerá aquí.'
        : S.filter === 1 ? 'No hay partidas finalizadas.' : 'No hay partidas en curso ni terminadas antes de tiempo.'));
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  };

  P.HistoryDetail = function (id) {
    const S = { tab: 0, round: -1 };
    S.build = () => {
      const g = P.game(id);
      const root = h('div', { class: 'screen' });
      if (!g) { root.append(P.cheader('Detalle de partida', P.pop), h('div', { class: 'empty' }, 'La partida ya no existe.')); return root; }
      root.appendChild(P.cheader('Detalle de partida', P.pop, P.ibtn('share', '#fff', 21, () => share(g), 'Compartir')));
      const body = h('div', { class: 'body' });
      body.appendChild(h('div', { class: 'row', style: { gap: '14px', margin: '4px 0 14px' } },
        h('span', { style: { width: '64px', height: '64px', borderRadius: '50%', background: '#16264A', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: '0' } }, gameIcon(g)),
        h('div', { class: 'grow' }, h('div', { style: { fontSize: '19px', fontWeight: '700' } }, P.gameTitle(g)),
          h('div', { class: 'text2', style: { fontSize: '13.5px', marginTop: '4px' } }, P.gameSub(g) + (g.mode === E.FULL ? ' · modo completo' : '')),
          h('div', { class: 'muted', style: { fontSize: '13px', marginTop: '3px' } }, P.dateTime(g.created)))));
      body.appendChild(h('div', { style: { marginBottom: '14px' } }, P.seg(['Resumen', 'Rondas', 'Configuración'], S.tab, (i) => { S.tab = i; P.refresh(); })));
      if (S.tab === 0) summary(g, body); else if (S.tab === 1) rounds(g, body); else config(g, body);
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };

    function summary(g, body) {
      if (g.status === E.EARLY) body.appendChild(h('div', { class: 'banner gold', style: { marginBottom: '12px' } }, P.icon('pause', '#F2B41F', 18),
        'Finalizada anticipadamente · Sin victoria. Las rondas jugadas cuentan para las estadísticas.'));
      body.appendChild(P.section('Clasificación'));
      body.appendChild(P.standings(g));
      const auc = g.rounds.filter((r) => r.saved && r.auction);
      if (auc.length) {
        body.appendChild(P.section('Subastas'));
        const c = h('div', { class: 'card' });
        for (const p of g.order) {
          const won = auc.filter((r) => r.winner === p);
          if (!won.length) continue;
          const hit = won.filter((r) => E.hit(g, r, p)).length;
          c.appendChild(h('div', { class: 'row', style: { padding: '6px 0' } }, P.dot(g.colors[p], 14), h('div', { class: 'grow', style: { fontWeight: '500' } }, g.names[p]),
            h('span', { class: 'text2', style: { fontSize: '13.5px' } }, P.plural(won.length, 'ganada', 'ganadas') + ' · ' + hit + ' acertada' + (hit === 1 ? '' : 's'))));
        }
        if (!c.children.length) c.appendChild(h('div', { class: 'muted' }, 'Sin adjudicatario registrado.'));
        body.appendChild(c);
      }
      body.appendChild(h('div', { class: 'row', style: { marginTop: '10px' } },
        h('div', { class: 'grow' }, P.secondary('Revisar rondas', '#FFC83A', () => P.push(P.GameScreen(g, true, 0)), 'edit')),
        h('div', null, P.secondary('Eliminar', '#FF6A5C', () => P.confirm('Eliminar partida', 'Se borrará del historial y dejará de contar en las estadísticas. No se puede deshacer.', 'Eliminar', true, () => {
          P.markDeleted(g);
          st.games = st.games.filter((x) => x !== g); P.save(); P.pop(); P.toast('Partida eliminada');
          P.syncQuiet(false);
        }), 'trash'))));
    }

    function rounds(g, body) {
      const saved = E.savedCount(g);
      if (!saved) { body.appendChild(h('div', { class: 'muted' }, 'No hay rondas guardadas.')); return; }
      if (S.round < 0 || S.round >= saved) S.round = 0;
      const legend = (c, t) => h('span', { class: 'row', style: { gap: '6px', marginRight: '16px', fontSize: '13px', color: 'var(--text2)', fontWeight: '500' } }, h('span', { class: 'dot', style: { width: '12px', height: '12px', background: c } }), t);
      body.appendChild(h('div', { class: 'row', style: { gap: '0', marginBottom: '10px' } }, legend('var(--surface3)', 'Normal'), legend('var(--blue)', 'Subastada'), legend('var(--yellow)', 'Editada')));
      const circ = h('div', { class: 'rcircles' });
      for (let i = 0; i < saved; i++) {
        const r = g.rounds[i], ed = E.edited(r);
        circ.appendChild(h('button', { class: (ed ? 'e' : r.auction ? 'a' : r.tiebreak ? 't' : '') + (i === S.round ? ' sel' : ''), onclick: () => { S.round = i; P.refresh(); } }, String(r.cards)));
      }
      body.appendChild(circ);
      setTimeout(() => { circ.scrollLeft = Math.max(0, 43 * S.round - 120); }, 0);
      const r = g.rounds[S.round];
      body.appendChild(h('div', { style: { textAlign: 'center', fontSize: '16.5px', fontWeight: '600', margin: '12px 0 4px' } },
        r.tiebreak ? 'Desempate · 1 carta' : 'Ronda ' + (S.round + 1) + ' · ' + P.plural(r.cards, 'carta', 'cartas')));
      const extras = ['Reparte ' + g.names[E.dealer(g, S.round)]];
      if (r.mult) extras.push(E.suitName(r.mult) + ' ×' + E.factor(g.rules, r.mult));
      if (r.auction) extras.push(r.winner ? 'Subasta: ' + g.names[r.winner] : 'Subastada');
      body.appendChild(h('div', { class: 'text2', style: { textAlign: 'center', fontSize: '13px', marginBottom: '10px' } }, extras.join(' · ')));
      const t = h('div', { class: 'tbl' });
      t.appendChild(h('div', { class: 'tr h' }, h('div', { style: { flex: '1.15' } }, 'Jugador'), h('div', { style: { flex: '1.35' } }, 'Resultado'), h('div', { class: 'c', style: { flex: '.65' } }, 'Puntos'), h('div', { class: 'c', style: { flex: '.7' } }, 'Total')));
      const changed = E.editedPlayers(g, r);
      for (const p of g.order) {
        const pts = E.points(g, r, p), yel = changed.has(p);
        const res = !E.plays(r, p) ? 'No juega' : P.readText(g, r.res[p]).replace('Pedidas: ', 'P ').replace(' · Hechas: ', ' · H ');
        t.appendChild(h('div', { class: 'tr', style: yel ? { background: 'rgba(255,200,58,.08)', borderRadius: '8px' } : null },
          h('div', { class: 'row ell', style: { flex: '1.15', gap: '7px' } }, P.dot(g.colors[p], 11), h('span', { class: 'ell', style: { fontWeight: '500' } }, g.names[p])),
          h('div', { class: 'ell text2', style: { flex: '1.35', fontSize: '13px' } }, res),
          h('div', { class: 'c', style: { flex: '.65', fontWeight: '700', color: yel ? 'var(--yellow)' : pts == null ? 'var(--muted)' : pts >= 0 ? 'var(--green-hi)' : 'var(--red-hi)' } }, pts == null ? '—' : P.signed(pts)),
          h('div', { class: 'c', style: { flex: '.7', color: '#fff', fontWeight: '600' } }, P.num(E.total(g, S.round, p)))));
      }
      body.appendChild(t);
      const acts = h('div', { class: 'row', style: { marginTop: '12px' } },
        h('div', { class: 'grow' }, P.secondary('Editar ronda', '#FFC83A', () => P.push(P.GameScreen(g, true, S.round, true)), 'edit')));
      if (E.canUndo(r)) acts.appendChild(h('div', { class: 'grow' }, P.secondary('Deshacer', '#B4BFD2', () => { E.undo(g, r, Date.now()); P.save(); P.markGame(g); P.syncQuiet(true); P.toast('Corrección deshecha'); P.refresh(); }, 'undo')));
      body.appendChild(acts);
      if ((r.revs || []).length) {
        body.appendChild(P.section('Correcciones de esta ronda'));
        const c = h('div', { class: 'card' });
        r.revs.slice().reverse().forEach((rv, i) => {
          if (i) c.appendChild(h('div', { class: 'hr' }));
          const what = rv.isUndo ? 'Deshecha una corrección' : rv.undone ? 'Corrección (deshecha)' : 'Corrección';
          const e = h('div', { style: { padding: '8px 0' } }, h('div', { style: { fontSize: '13.5px', fontWeight: '600', color: rv.isUndo || rv.undone ? 'var(--muted)' : 'var(--yellow)' } }, what + ' · ' + P.dateTime(rv.at)));
          for (const line of diff(g, rv)) e.appendChild(h('div', { class: 'text2', style: { fontSize: '13px', marginTop: '4px' } }, line));
          c.appendChild(e);
        });
        body.appendChild(c);
      }
    }

    function diff(g, rv) {
      const out = [];
      for (const p of g.order) {
        const a = rv.before.res[p], b = rv.after.res[p];
        if (JSON.stringify(a || {}) !== JSON.stringify(b || {})) out.push(g.names[p] + ': ' + P.readText(g, a) + ' → ' + P.readText(g, b));
      }
      if ((rv.before.winner || null) !== (rv.after.winner || null)) out.push('Subasta: ' + (rv.before.winner ? g.names[rv.before.winner] : 'nadie') + ' → ' + (rv.after.winner ? g.names[rv.after.winner] : 'nadie'));
      if ((rv.before.mult || null) !== (rv.after.mult || null)) out.push('Multiplicador: ' + (rv.before.mult ? E.suitName(rv.before.mult) : 'ninguno') + ' → ' + (rv.after.mult ? E.suitName(rv.after.mult) : 'ninguno'));
      return out;
    }

    function config(g, body) {
      body.appendChild(h('div', { class: 'note', style: { margin: '0 2px 10px', fontSize: '13px' } }, 'Reglas con las que se jugó esta partida. Si cambias un perfil más adelante, esta partida no cambia.'));
      body.appendChild(rulesCard(g));
    }

    function share(g) {
      let s = 'Poch! · ' + P.gameTitle(g) + '\n' + P.dateTime(g.created) + ' · ' + P.gameSub(g) + '\n\n';
      for (const r of E.standings(g)) s += (g.status === E.EARLY ? '• ' : r.pos + '. ') + g.names[r.pid] + ': ' + P.num(r.total) + '\n';
      if (navigator.share) navigator.share({ text: s }).catch(() => {});
      else if (navigator.clipboard) navigator.clipboard.writeText(s).then(() => P.toast('Resultado copiado'));
    }
    return S;
  };

  function rulesCard(g) {
    const c = h('div', { class: 'card', style: { padding: '4px 14px' } });
    c.appendChild(P.kv('Jugadores', String(g.order.length)));
    for (const [k, v] of P.rulesLines(g.rules, g.mode, g.order.length)) c.appendChild(P.kv(k, v));
    return c;
  }

  P.RulesView = function (g) {
    const S = {};
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Configuración de la partida', P.pop));
      const body = h('div', { class: 'body' });
      body.appendChild(h('div', { class: 'text2', style: { fontSize: '14px', margin: '0 2px 10px' } }, g.order.length + ' jugadores · Modo ' + (g.mode === E.FULL ? 'completo' : 'sencillo')));
      if (g.status === E.ACTIVE) {
        body.appendChild(h('div', { class: 'card' }, P.setRow('Si hay empate en cabeza', 'Se puede cambiar en cualquier momento de la partida'),
          P.seg(['Victoria compartida', 'Desempate'], g.rules.tie === E.TIE_BREAK ? 1 : 0, (i) => {
            E.setTieRule(g, i === 1 ? E.TIE_BREAK : E.TIE_SHARED); P.save();
            P.toast(i === 1 ? 'Si acabáis empatados, se desempata' : 'Si acabáis empatados, se comparte la victoria'); P.refresh();
          })));
      }
      body.appendChild(rulesCard(g));
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  };

  // ------------------------------------------------------------------ jugadores

  const winsText = (s) => P.plural(s.games, 'partida', 'partidas') + ' · ' + P.fmt1(s.wins) + (s.wins === 1 ? ' victoria' : ' victorias');

  P.Players = function () {
    const S = { q: '' };
    S.build = () => {
      const root = h('div', { class: 'screen' });
      const add = h('button', { 'aria-label': 'Añadir jugador', style: { width: '40px', height: '40px', borderRadius: '20px', background: 'var(--grad)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '6px' }, onclick: () => editPlayer(null) }, P.icon('plus', '#fff', 22));
      root.appendChild(P.header(P.icon('people', '#fff', 26), 'Jugadores', null, add));
      const q = h('input', { class: 'input', type: 'search', placeholder: 'Buscar jugador…', value: S.q, style: { border: '0', background: 'transparent', padding: '12px 4px 12px 10px' } });
      root.appendChild(h('div', { class: 'row', style: { margin: '0 16px 12px', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: '12px', padding: '0 8px 0 12px', gap: '0' } }, P.icon('search', 'var(--muted)', 19), q));
      const list = h('div', { class: 'body', style: { paddingTop: '0' } });
      const fill = () => {
        list.replaceChildren();
        const stats = E.statsAll(P.closedGames(), st.players.map((p) => p.id));
        const ps = st.players.slice().sort((a, b) => (stats[b.id].games - stats[a.id].games) || a.name.localeCompare(b.name, 'es'));
        const nq = E.norm(S.q);
        let n = 0;
        for (const p of ps) {
          if (nq && E.norm(p.name).indexOf(nq) < 0) continue;
          n++;
          const isMe = (st.groups || []).some((g) => g.me === p.id);
          list.appendChild(P.lrow(P.dot(p.color, 32), p.name + (isMe ? ' (tú)' : ''), winsText(stats[p.id]) + (P.groupNames(p.id) ? ' · ' + P.groupNames(p.id) : ''), () => P.push(PlayerDetail(p.id))));
        }
        if (!n) list.appendChild(h('div', { class: 'empty' }, st.players.length ? 'Ningún jugador coincide con la búsqueda.' : 'Aún no hay jugadores. Pulsa + para añadir el primero.'));
      };
      q.oninput = () => { S.q = q.value; fill(); };
      fill();
      S.scrollEl = h('div', { class: 'scroll' }, list);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  };

  function editPlayer(p) {
    const name = P.input('Nombre', p ? p.name : '');
    let color = p ? p.color : P.nextColor();
    const sw = h('div', { class: 'flow', style: { gap: '10px' } });
    const paint = () => [...sw.children].forEach((b) => b.classList.toggle('on', +b.dataset.c === color));
    for (const c of P.PLAYER_COLORS) sw.appendChild(h('button', { class: 'swatch', 'data-c': c, 'aria-label': 'Color', onclick: () => { color = c; paint(); } }, P.dot(c, 38)));
    paint();
    const body = h('div', null, name, P.section('Color'), sw, h('div', { style: { height: '18px' } }),
      P.primary(p ? 'Guardar' : 'Añadir jugador', () => {
        const n = name.value.trim();
        if (!n) { P.toast('Escribe un nombre'); return; }
        if (P.nameTaken(n, p && p.id)) { P.toast('Ya hay un jugador llamado ' + n); return; }
        if (!p) st.players.push({ id: P.newId('j'), name: n, color, created: Date.now() });
        else {
          p.name = n; p.color = color;
          for (const g of st.games) if (g.order.indexOf(p.id) >= 0) { g.names[p.id] = n; g.colors[p.id] = color; }
          P.markPlayer(p);
        }
        P.save(); P.closeOverlay(); P.refresh();
      }));
    if (p) body.append(h('div', { style: { height: '10px' } }), P.secondary('Eliminar jugador', '#FF6A5C', () => {
      if (st.games.some((g) => g.order.indexOf(p.id) >= 0)) { P.info('No se puede eliminar', p.name + ' tiene partidas en el historial. Sus estadísticas dependen de ellas.'); return; }
      P.confirm('Eliminar jugador', 'Se eliminará a ' + p.name + '.', 'Eliminar', true, () => {
        st.players = st.players.filter((x) => x !== p);
        st.statsSel = st.statsSel.filter((x) => x !== p.id); st.statsCmp = st.statsCmp.filter((x) => x !== p.id);
        P.save(); P.closeAll(); P.popToRoot(P.tab()); P.refresh();
      });
    }));
    P.sheet(p ? 'Editar jugador' : 'Nuevo jugador', body);
    if (!p) setTimeout(() => name.focus(), 250);
  }

  function PlayerDetail(pid) {
    const S = { with: [] };
    S.build = () => {
      const p = P.player(pid);
      const root = h('div', { class: 'screen' });
      if (!p) { root.appendChild(P.cheader('Jugador', P.pop)); return root; }
      root.appendChild(P.cheader(p.name, P.pop, P.ibtn('edit', '#fff', 21, () => editPlayer(p), 'Editar')));
      S.with = S.with.filter((id) => P.player(id));
      let pool = P.closedGames();
      if (S.with.length) pool = E.withAll(pool, S.with.concat([pid]));
      const s = E.stats(pool, pid), D = E.derived;
      const withNames = S.with.map((id) => P.player(id).name);
      const body = h('div', { class: 'body' });
      const on = S.with.length > 0;
      body.appendChild(h('div', { class: 'row', style: { gap: '14px', margin: '4px 0 14px 2px' } }, P.dot(p.color, 50),
        h('div', { class: 'grow' }, h('div', { class: 'ell', style: { fontSize: '22px', fontWeight: '700' } }, p.name),
          h('div', { style: { fontSize: '13.5px', marginTop: '4px', color: on ? 'var(--blue-text)' : 'var(--text2)', fontWeight: on ? '500' : '400' } }, on ? 'Jugando con ' + P.joinNames(withNames) : 'Estadísticas generales'),
          P.groupNames(pid) ? h('div', { class: 'row', style: { gap: '6px', marginTop: '5px', color: 'var(--green)', fontSize: '13px', fontWeight: '500' } }, P.icon('people', 'var(--green)', 15),
            (P.groupsOf(pid).length > 1 ? 'Pertenece a los grupos ' : 'Pertenece al grupo ') + P.groupNames(pid)) : null),
        P.chip(on ? 'Con ' + S.with.length : 'Filtrar', on, () => P.multiPick('Jugando con…', 'Cuentan solo las partidas de ' + p.name + ' en las que también jugaron todos los que elijas.',
          P.sorted().filter((o) => o.id !== pid), S.with, 'Aplicar filtro', (ids) => { S.with = ids; S.scrollY = 0; P.refresh(); return true; }), 'filter')));
      if (on) body.appendChild(h('div', { class: 'banner', style: { marginBottom: '14px', paddingRight: '4px' } }, P.icon('people', 'var(--blue-text)', 19),
        h('div', { class: 'grow', style: { fontSize: '13px' } }, s.games ? 'Solo partidas con ' + P.joinNames(withNames) + ': ' + P.plural(s.games, 'partida', 'partidas') + '.' : 'No hay partidas cerradas de ' + p.name + ' con ' + P.joinNames(withNames) + '.'),
        P.ibtn('close', 'var(--text2)', 18, () => { S.with = []; P.refresh(); }, 'Quitar filtro')));
      body.appendChild(h('div', { class: 'tiles' }, P.tile('trophy', '#F2B41F', P.fmt1(s.wins), 'Victorias'), P.tile('gamepad', '#3A92FF', String(s.games), 'Partidas'),
        P.tile('chart', '#B4BFD2', P.fmt1(D.posAvg(s)), 'Posición media'), P.tile('star', '#F2B41F', s.bestScore == null ? '—' : P.num(s.bestScore), 'Mejor puntuación')));
      if (s.games > s.completed) body.appendChild(P.note(P.plural(s.games - s.completed, 'partida finalizada', 'partidas finalizadas') + ' antes de tiempo: cuentan sus rondas, no victorias ni puestos.'));
      body.appendChild(P.section('Resultados en rondas'));
      body.appendChild(h('div', { class: 'row', style: { alignItems: 'stretch', gap: '8px' } },
        h('div', { class: 'card row', style: { flex: '1.45', margin: '0', gap: '12px' } }, ring(D.hitPct(s)),
          h('div', { style: { fontSize: '13px', fontWeight: '500' } }, h('div', { class: 'text2', style: { fontSize: '12.5px' } }, 'Rondas acertadas'),
            h('div', { style: { color: 'var(--green-hi)', marginTop: '4px' } }, s.hits + ' acertadas'), h('div', { style: { color: 'var(--red-hi)', marginTop: '4px' } }, s.misses + ' falladas'),
            h('div', { class: 'muted', style: { marginTop: '4px' } }, 'Total: ' + s.rounds))),
        h('div', { class: 'card', style: { flex: '1', margin: '0', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center' } },
          h('div', { class: 'text2', style: { fontSize: '12.5px', fontWeight: '500' } }, 'Media por partida'),
          h('div', { style: { fontSize: '28px', fontWeight: '700', margin: '6px 0 4px' } }, s.completed ? String(Math.round(D.pointsAvg(s))) : '—'),
          h('div', { class: 'text2', style: { fontSize: '12.5px' } }, 'puntos'))));
      body.appendChild(P.section('Bazas (modo completo)'));
      if (!s.fullRounds) body.appendChild(P.note('No disponible: solo se registran pedidas y hechas en las partidas en modo completo.'));
      else body.append(h('div', { class: 'tiles' }, P.tile(null, null, P.fmt1(D.bidAvg(s)), 'Bazas pedidas (media)'), P.tile(null, null, P.fmt1(D.madeAvg(s)), 'Bazas hechas (media)'),
        P.tile(null, null, P.pct(D.fullHitPct(s)), 'Acierto en bazas pedidas')), P.note('Sobre ' + P.plural(s.fullRounds, 'ronda', 'rondas') + ' en modo completo.'));
      body.appendChild(P.section('Subastadas'));
      body.appendChild(h('div', { class: 'tiles' }, P.tile('crown', '#F2B41F', String(s.aucPlayed), 'Disputadas'), P.tile('trophy', '#F2B41F', String(s.aucWon), 'Ganadas'),
        P.tile('target', '#FF6A5C', P.pct(D.aucPct(s)), '% adjudicación'), P.tile('check', '#2BC46A', String(s.aucWonHit), 'Aciertos' + (s.aucWon ? ' (' + P.pct(D.aucHitPct(s)) + ')' : ''))));
      body.appendChild(P.section('Más datos'));
      body.appendChild(h('div', { class: 'card', style: { padding: '4px 14px' } }, P.kv('Partidas completas', String(s.completed)), P.kv('Finalizadas antes de tiempo', String(s.games - s.completed)),
        P.kv('Últimos puestos', String(s.lasts)), P.kv('Porcentaje de victorias', s.completed ? P.pct(100 * s.wins / s.completed) : '—')));
      body.appendChild(P.section('Histórico reciente'));
      if (!s.recent.length) body.appendChild(P.note(on ? 'No hay partidas cerradas con esos jugadores.' : 'Todavía no ha jugado ninguna partida cerrada.'));
      for (const rc of s.recent.slice(0, 8)) {
        const g = rc.game;
        const col = rc.pos === 0 ? 'var(--muted)' : ['#F2B41F', '#C9D2DE', '#D3874A'][rc.pos - 1] || 'var(--faint)';
        const t = rc.pos === 0 ? 'Sin victoria vs ' + g.order.length + ' jugadores' : rc.won ? (E.winners(g).length > 1 ? 'Ganó (compartida)' : 'Ganó') + ' vs ' + g.order.length + ' jugadores' : rc.pos + 'º puesto vs ' + g.order.length + ' jugadores';
        body.appendChild(h('button', { class: 'lrow', style: { minHeight: '56px' }, onclick: () => P.push(P.HistoryDetail(g.id)) }, P.icon(rc.pos === 0 ? 'pause' : 'trophy', col, 30),
          h('div', { style: { flex: '1', minWidth: '0' } }, h('div', { class: 'ell', style: { fontSize: '15px', fontWeight: '500' } }, t), h('div', { class: 'muted', style: { fontSize: '12.5px', marginTop: '3px' } }, P.date(g.created))),
          h('span', { style: { fontSize: '16px', fontWeight: '700', color: rc.points >= 0 ? 'var(--green-hi)' : 'var(--red-hi)' } }, P.signed(rc.points))));
      }
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  }
  P.PlayerDetail = PlayerDetail;

  function ring(pct) {
    const r = 28, c = 2 * Math.PI * r, v = isNaN(pct) ? 0 : Math.max(0, Math.min(100, pct));
    const el = h('div', { class: 'ring', style: { position: 'relative' } });
    el.innerHTML = `<svg width="66" height="66" viewBox="0 0 66 66" aria-hidden="true"><circle cx="33" cy="33" r="${r}" fill="none" stroke="#1F2B44" stroke-width="8"/>`
      + (v > 0 ? `<circle cx="33" cy="33" r="${r}" fill="none" stroke="#2BC46A" stroke-width="8" stroke-dasharray="${c * v / 100} ${c}" transform="rotate(-90 33 33)"/>` : '') + '</svg>';
    el.appendChild(h('div', { style: { position: 'absolute', inset: '0', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '17px', fontWeight: '700' } }, isNaN(pct) ? '—' : Math.round(pct) + '%'));
    return el;
  }

  // ------------------------------------------------------------------ estadísticas

  const NOG = '__none';
  P.Stats = function () {
    const S = { section: 0, sort: -1, mode: st.statsSel.length ? 1 : 0 };
    const D = E.derived;
    const cols = () => S.section === 0 ? [
      ['Vict.', 1, false, (s) => s.wins], ['Part.', 0, false, (s) => s.games], ['Pos.', 1, true, D.posAvg], ['Media', 0, false, D.pointsAvg], ['% Ac.', 2, false, D.hitPct],
    ] : S.section === 1 ? [
      ['Rondas', 0, false, (s) => s.rounds], ['Acert.', 0, false, (s) => s.hits], ['Fall.', 0, true, (s) => s.misses], ['% Ac.', 2, false, D.hitPct], ['% Baz.', 2, false, D.fullHitPct],
    ] : [
      ['Disp.', 0, false, (s) => s.aucPlayed], ['Adj.', 0, false, (s) => s.aucWon], ['% Adj.', 2, false, D.aucPct], ['Acert.', 0, false, (s) => s.aucWonHit], ['% Ac.', 2, false, D.aucHitPct],
    ];
    const fmt = (kind, v) => isNaN(v) ? '—' : kind === 2 ? Math.round(v) + '%' : kind === 1 ? P.fmt1(v) : String(Math.round(v));
    const known = (ids) => ids.filter((id) => P.player(id)).length;
    const nz = (v) => isNaN(v) ? -1e9 : v;

    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.header(P.icon('chart', '#fff', 26), 'Estadísticas'));
      const body = h('div', { class: 'body', style: { paddingTop: '0' } });
      body.appendChild(h('div', { style: { marginBottom: '12px' } }, P.seg(['General', 'Rondas', 'Subastadas'], S.section, (i) => { S.section = i; S.sort = -1; P.refresh(); })));
      const groups = st.groups || [];
      if (P.statsGroup && P.statsGroup !== NOG && !P.groupById(P.statsGroup)) P.statsGroup = null;
      if (groups.length) {
        body.appendChild(h('div', { class: 'text2', style: { fontSize: '14px', fontWeight: '500', margin: '0 2px 8px' } }, 'Filtrar por grupo'));
        body.appendChild(h('div', { class: 'chips', style: { marginBottom: '14px' } },
          P.chip('Todas las partidas', !P.statsGroup, () => { P.statsGroup = null; P.refresh(); }),
          groups.map((g) => P.chip(g.name, P.statsGroup === g.id, () => { P.statsGroup = g.id; P.refresh(); })),
          P.chip('Sin grupo', P.statsGroup === NOG, () => { P.statsGroup = NOG; P.refresh(); })));
      }
      const fg = P.statsGroup;
      let pool = P.closedGames().filter((g) => !fg || (fg === NOG ? !g.group || !P.groupById(g.group) : g.group === fg));
      let inPool = null;
      if (fg) {
        inPool = new Set();
        for (const g of pool) for (const id of g.order) inPool.add(id);
        const gr = P.groupById(fg);
        if (gr) for (const p of st.players) if (P.groupsOf(p.id).indexOf(gr) >= 0) inPool.add(p.id);
      }
      body.appendChild(h('div', { class: 'text2', style: { fontSize: '14px', fontWeight: '500', margin: '0 2px 8px' } }, 'Filtrar jugadores'));
      const nsel = known(st.statsSel), ncmp = known(st.statsCmp);
      body.appendChild(h('div', { class: 'chips', style: { marginBottom: '14px' } },
        P.chip('Todos (' + st.players.filter((p) => !inPool || inPool.has(p.id)).length + ')', S.mode === 0, () => { S.mode = 0; P.refresh(); }),
        P.chip(nsel ? nsel + (nsel === 1 ? ' seleccionado' : ' seleccionados') : 'Seleccionar', S.mode === 1, () => pick(false)),
        P.chip('Comparativa' + (S.mode === 2 ? ' (' + ncmp + ')' : ''), S.mode === 2, () => pick(true), 'people')));
      const src = S.mode === 1 ? st.statsSel : S.mode === 2 ? st.statsCmp : null;
      const ps = P.sorted().filter((p) => (!src || src.indexOf(p.id) >= 0) && (!inPool || inPool.has(p.id)));
      const ids = ps.map((p) => p.id);
      if (fg) body.appendChild(h('div', { style: { fontSize: '13px', fontWeight: '500', color: 'var(--blue-text)', margin: '0 2px 8px' } },
        P.plural(pool.length, 'partida', 'partidas') + (P.groupById(fg) ? ' del grupo «' + P.groupById(fg).name + '»' : ' sin grupo')));
      if (S.mode === 2) {
        pool = E.withAll(pool, ids);
        const names = P.joinNames(ps.map((p) => p.name));
        body.appendChild(h('div', { class: 'banner', style: { marginBottom: '4px' } }, P.icon('people', 'var(--blue-text)', 20),
          pool.length ? 'Solo partidas en las que jugaron juntos ' + names + ': ' + P.plural(pool.length, 'partida', 'partidas') + '.' : names + ' aún no han jugado ninguna partida juntos.'));
      }
      const stats = E.statsAll(pool, ids);
      if (!ps.length) body.appendChild(h('div', { class: 'empty' }, st.players.length ? 'No hay jugadores seleccionados.' : 'Aún no hay jugadores.'));
      else {
        const cs = cols(), key = S.sort < 0 ? 0 : S.sort, sc = cs[key];
        const sorted = ps.slice().sort((x, y) => {
          const vx = sc[3](stats[x.id]), vy = sc[3](stats[y.id]);
          if (isNaN(vx) || isNaN(vy)) return isNaN(vx) === isNaN(vy) ? x.name.localeCompare(y.name, 'es') : isNaN(vx) ? 1 : -1;
          return (sc[2] ? vx - vy : vy - vx) || x.name.localeCompare(y.name, 'es');
        });
        body.appendChild(P.section(S.section === 0 ? 'Comparativa general' : S.section === 1 ? 'Comparativa de rondas' : 'Comparativa de subastas'));
        const t = h('div', { class: 'tbl' });
        t.appendChild(h('div', { class: 'tr h' }, h('div', { style: { width: '26px', textAlign: 'center' } }, '#'), h('div', { class: 'nmc' }, 'Jugador'),
          cs.map((c, i) => h('button', { class: 'c' + (i === key ? ' on' : ''), style: { minHeight: '28px', fontSize: '11.5px' }, onclick: () => { S.sort = i; P.refresh(); } }, c[0] + (i === key ? (c[2] ? ' ▲' : ' ▼') : '')))));
        let rank = 0, prev = null;
        sorted.forEach((p, i) => {
          const v = sc[3](stats[p.id]);
          if (i === 0 || isNaN(v) || v !== prev) rank = i + 1;
          prev = v;
          t.appendChild(h('button', { class: 'tr', style: { width: '100%' }, onclick: () => P.push(PlayerDetail(p.id)) },
            h('span', { class: 'badge', style: { background: rank === 1 ? '#1F9D55' : 'var(--surface3)', marginRight: '4px' } }, String(rank)),
            h('div', { class: 'nmc' }, P.dot(p.color, 11), h('span', { class: 'ell', style: { fontWeight: '500' } }, p.name)),
            cs.map((c, j) => h('div', { class: 'c' + (j === key ? ' on' : '') }, fmt(c[1], c[3](stats[p.id]))))));
        });
        body.appendChild(t);
        body.appendChild(P.note(S.section === 0 ? 'Toca una columna para ordenar. Media y posición sobre partidas completas; % de rondas acertadas sobre todas las rondas jugadas.'
          : S.section === 1 ? '% Baz.: acierto exacto en las rondas en modo completo (pedidas = hechas).'
            : 'Disp.: subastas jugadas. Adj.: subastas que se llevó. Acert.: aciertos en las que se llevó.'));
        charts(body, ps, stats);
      }
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };

    function bar(p, frac, value) {
      return h('div', { class: 'bar' }, P.dot(p.color, 12), h('span', { class: 'nm ell' }, p.name),
        h('div', { class: 'trk' }, frac > 0 ? h('div', { style: { width: Math.max(6, Math.min(100, frac * 100)) + '%', background: `linear-gradient(${P.mix(p.color, 0.18)}, ${P.mix(p.color, -0.12)})` } }) : null),
        h('span', { class: 'v' }, value));
    }
    function split(p, pct) {
      const s = isNaN(pct)
        ? h('div', { class: 'split none' }, 'Sin datos')
        : h('div', { class: 'split' }, h('div', { class: 'g', style: { width: pct + '%' } }, pct >= 12 ? Math.round(pct) + '%' : ''),
          h('div', { class: 'r', style: { width: (100 - pct) + '%' } }, 100 - pct >= 12 ? Math.round(100 - pct) + '%' : ''));
      return h('div', { class: 'bar' }, P.dot(p.color, 12), h('span', { class: 'nm ell' }, p.name), s);
    }
    const by = (ps, stats, f) => ps.slice().sort((x, y) => nz(f(stats[y.id])) - nz(f(stats[x.id])));

    function charts(body, ps, stats) {
      if (S.section === 0) {
        body.appendChild(P.section('Puntuación media por partida'));
        const max = Math.max(1, ...ps.map((p) => nz(D.pointsAvg(stats[p.id]))));
        body.appendChild(h('div', { class: 'card' }, by(ps, stats, D.pointsAvg).map((p) => { const v = D.pointsAvg(stats[p.id]); return bar(p, isNaN(v) ? 0 : v / max, isNaN(v) ? '—' : String(Math.round(v))); })));
      }
      if (S.section <= 1) {
        body.appendChild(P.section('Rondas acertadas vs falladas'));
        body.appendChild(h('div', { class: 'card' }, by(ps, stats, D.hitPct).map((p) => split(p, D.hitPct(stats[p.id])))));
      }
      if (S.section === 1) {
        body.appendChild(P.section('Bazas: media de pedidas y hechas'));
        const rows = ps.filter((p) => stats[p.id].fullRounds).map((p) => { const s = stats[p.id]; return P.kv(p.name, 'pide ' + P.fmt1(D.bidAvg(s)) + ' · hace ' + P.fmt1(D.madeAvg(s)) + ' · ' + P.plural(s.fullRounds, 'ronda', 'rondas')); });
        body.appendChild(h('div', { class: 'card', style: { padding: '4px 14px' } }, rows.length ? rows : h('div', { class: 'muted', style: { padding: '8px 0' } }, 'No disponible: no hay partidas en modo completo.')));
      }
      if (S.section === 2) {
        body.appendChild(P.section('Porcentaje de adjudicación'));
        body.appendChild(h('div', { class: 'card' }, by(ps, stats, D.aucPct).map((p) => { const v = D.aucPct(stats[p.id]); return bar(p, isNaN(v) ? 0 : v / 100, P.pct(v)); })));
        body.appendChild(P.section('Acierto tras llevarse la subasta'));
        body.appendChild(h('div', { class: 'card' }, by(ps, stats, D.aucHitPct).map((p) => split(p, D.aucHitPct(stats[p.id])))));
      }
    }

    function pick(cmp) {
      P.multiPick(cmp ? 'Comparativa' : 'Elegir jugadores',
        cmp ? 'Elige 2 o más jugadores: solo cuentan las partidas en las que jugaron todos ellos (puede haber más jugadores en la partida).'
          : 'Muestra las estadísticas de los jugadores que elijas, en todas sus partidas.',
        P.sorted(), cmp ? st.statsCmp : st.statsSel, cmp ? 'Comparar' : 'Ver estadísticas', (ids) => {
          if (cmp && ids.length === 1) { P.toast('Elige al menos 2 jugadores'); return false; }
          if (cmp) st.statsCmp = ids; else st.statsSel = ids;
          P.save();
          S.mode = !ids.length ? 0 : cmp ? 2 : 1;
          P.refresh();
          return true;
        });
    }
    return S;
  };

  // ------------------------------------------------------------------ ajustes

  const badge = (icon, color) => h('span', { style: { width: '40px', height: '40px', borderRadius: '11px', background: color + '29', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: '0' } }, P.icon(icon, color, 22));

  P.Settings = function () {
    const S = {};
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.header(P.icon('gear', '#fff', 26), 'Ajustes'));
      const body = h('div', { class: 'body', style: { paddingTop: '2px' } });
      body.appendChild(P.lrow(badge('sliders', '#3A92FF'), 'Perfiles de reglas', P.plural(st.profiles.length, 'perfil', 'perfiles') + ' · por defecto: ' + P.defaultRules().name, () => P.push(Profiles())));
      const ng = (st.groups || []).length;
      body.appendChild(P.lrow(badge('people', '#3A92FF'), 'Grupos de juego', ng ? P.joinNames(st.groups.map((g) => g.name)) : 'Compartid partidas entre vuestros móviles', () => P.push(P.Groups())));
      body.appendChild(P.lrow(badge('backup', '#2BC46A'), 'Copias de seguridad', st.lastBackup ? 'Última copia: ' + P.dateTime(st.lastBackup) : 'Nunca se ha hecho una copia', () => P.push(Backup())));
      body.appendChild(P.lrow(badge('people', '#F28B24'), 'Jugadores', P.plural(st.players.length, 'jugador registrado', 'jugadores registrados'), () => P.selectTab(2)));
      if (!P.standalone()) body.appendChild(P.lrow(badge('download', '#FFC83A'), 'Instalar en el móvil', 'Añádela a la pantalla de inicio', () => P.info('Instalar Poch!', P.installText())));
      body.appendChild(P.lrow(badge('info', '#B4BFD2'), 'Acerca de Poch!', 'Versión web ' + P.VERSION, () => P.info('Poch! ' + P.VERSION,
        'Marcador, historial y estadísticas para partidas de La Pocha.\n\nGratis, sin anuncios y sin Internet: todos los datos se guardan en este móvil. Haz copias de seguridad de vez en cuando; sirven también para pasarse partidas con la app de Android.\n\nTipografía Inter (SIL Open Font License).')));
      body.appendChild(P.lrow(badge('sync', '#2BC46A'), 'Recargar página', 'Vuelve a cargar la app al momento', () => P.reloadApp()));
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  };

  function Profiles() {
    const S = {};
    const upsert = (r) => { const i = st.profiles.findIndex((x) => x.id === r.id); if (i >= 0) st.profiles[i] = r; else st.profiles.push(r); P.save(); };
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Perfiles de reglas', P.pop));
      const body = h('div', { class: 'body' });
      body.appendChild(h('div', { class: 'note', style: { margin: '0 2px 12px', fontSize: '13px' } }, 'Guarda las variantes que jugáis. Al crear una partida se copia el perfil elegido: cambiarlo después no afecta a partidas ya empezadas.'));
      for (const r of st.profiles) {
        const def = r.id === st.defaultProfile;
        body.appendChild(P.lrow(badge(def ? 'star' : 'sliders', def ? '#F2B41F' : '#3A92FF'), r.name + (def ? ' (por defecto)' : ''), P.rulesSummary(r), () => P.push(P.RulesEditor(E.copy(r), true, upsert))));
      }
      body.appendChild(P.secondary('Nuevo perfil', '#4AA0FF', () => { const r = E.copy(P.defaultRules()); r.id = P.newId('r'); r.name = 'Mi variante'; P.push(P.RulesEditor(r, true, upsert)); }, 'plus'));
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  }

  P.RulesEditor = function (r, profile, onSave) {
    const S = { example: 5 };
    let preview, table;
    const update = () => {
      if (!preview) return;
      const seq = E.sequence(r, S.example), max = Math.max.apply(null, seq);
      const auc = r.auctionMax ? seq.filter((c) => c === max).length : 0;
      preview.textContent = seq.length + ' rondas, ' + auc + ' subastadas:\n' + seq.join('-');
      let f = 2, suit = 'Oros';
      for (let i = 0; i < 4; i++) if (r.multOn[i]) { f = r.multF[i]; suit = E.SUIT_NAMES[i]; break; }
      const b = r.base, bx = r.base * (r.multBase ? f : 1);
      table.textContent = 'Ejemplos (normal · ' + suit + ' ×' + f + '):\nAcertar 0: +' + b + ' · +' + bx + '\nAcertar 3: +' + (b + 3 * r.perTrick) + ' · +' + (bx + 3 * r.perTrick * f)
        + '\nFallar por 1: −' + r.penalty + ' · −' + r.penalty * f + '\nFallar por 2: −' + 2 * r.penalty + ' · −' + 2 * r.penalty * f;
    };
    const note = (t) => h('div', { class: 'note', style: { margin: '2px 0 6px' } }, t);
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader(profile ? 'Editar perfil' : 'Reglas de la partida', P.pop));
      const body = h('div', { class: 'body' });
      if (profile) { const n = P.input('Nombre del perfil', r.name); n.oninput = () => { r.name = n.value; }; body.appendChild(h('div', { style: { marginTop: '4px' } }, n)); }
      body.appendChild(P.section('Rondas'));
      const c1 = h('div', { class: 'card' });
      c1.append(P.setRow('Baraja', r.deck === 48 ? 'Española de 48 (con 8 y 9)' : 'Española de 40'), P.seg(['40 cartas', '48 cartas'], r.deck === 48 ? 1 : 0, (i) => { r.deck = i ? 48 : 40; update(); }));
      c1.append(h('div', { class: 'hr', style: { margin: '10px 0 0' } }), P.setRow('Secuencia'));
      const custom = r.seqType === 'custom';
      c1.appendChild(P.seg(['Estándar', 'Personalizada'], custom ? 1 : 0, (i) => {
        r.seqType = i ? 'custom' : 'standard';
        if (i && !String(r.customSeq).trim()) r.customSeq = E.sequence(Object.assign({}, r, { seqType: 'standard' }), S.example).join(', ');
        P.refresh();
      }));
      if (custom) {
        const sq = P.input('1, 2, 3, 4, 4, 3, 2, 1', r.customSeq);
        sq.inputMode = 'numeric';
        sq.oninput = () => { r.customSeq = sq.value; update(); };
        c1.append(h('div', { style: { margin: '8px 0 4px' } }, sq), note('Cartas por jugador en cada ronda, separadas por comas. Si una ronda pide más cartas de las que hay, se limita a la baraja.'),
          P.setRow('Subastadas las rondas con más cartas', null, P.toggle(r.auctionMax, (v) => { r.auctionMax = v; update(); })));
      } else {
        c1.append(P.setRow('Máximo de cartas por jugador', 'Se limita a baraja ÷ jugadores', P.stepper(r.maxCards, 1, 12, '', (v) => { r.maxCards = v; update(); })),
          P.setRow('Repeticiones del máximo', '0 = una por jugador', P.stepper(r.repeatMax, 0, 12, '', (v) => { r.repeatMax = v; update(); })),
          P.setRow('Rondas del máximo subastadas', null, P.toggle(r.auctionMax, (v) => { r.auctionMax = v; update(); })));
      }
      c1.append(P.setRow('Ejemplo con', null, P.stepper(S.example, 2, E.MAX_PLAYERS, ' jug.', (v) => { S.example = v; update(); })));
      preview = h('div', { class: 'text2', style: { fontSize: '13px', whiteSpace: 'pre-line', lineHeight: '1.5', padding: '4px 0' } });
      c1.appendChild(preview);
      body.appendChild(c1);
      body.appendChild(P.section('Puntuación'));
      body.appendChild(h('div', { class: 'card' },
        P.setRow('Base por acertar', null, P.stepper(r.base, 0, 50, '', (v) => { r.base = v; update(); })),
        P.setRow('Puntos por baza acertada', null, P.stepper(r.perTrick, 0, 30, '', (v) => { r.perTrick = v; update(); })),
        P.setRow('Penalización por baza de diferencia', null, P.stepper(r.penalty, 0, 30, '', (v) => { r.penalty = v; update(); }))));
      body.appendChild(P.section('Multiplicadores por palo'));
      const c3 = h('div', { class: 'card' });
      E.SUITS.forEach((s, i) => c3.appendChild(h('div', { class: 'set' }, P.icon(P.SUIT_ICON[s], P.SUIT_COLOR[s], 24), h('div', { class: 'st' }, E.SUIT_NAMES[i]),
        h('div', { style: { width: '100px' } }, P.seg(['×2', '×3'], r.multF[i] === 3 ? 1 : 0, (j) => { r.multF[i] = j ? 3 : 2; update(); })),
        P.toggle(r.multOn[i], (v) => { r.multOn[i] = v; update(); }))));
      c3.appendChild(P.setRow('Multiplicar también la base', 'Por defecto solo se multiplican bazas y penalizaciones', P.toggle(r.multBase, (v) => { r.multBase = v; update(); })));
      table = h('div', { class: 'text2', style: { fontSize: '13px', whiteSpace: 'pre-line', lineHeight: '1.6', padding: '6px 0 4px' } });
      c3.appendChild(table);
      body.appendChild(c3);
      body.appendChild(P.section('Pujas y empates'));
      body.appendChild(h('div', { class: 'card' },
        P.setRow('Prohibir que las pedidas sumen las bazas', 'El último en pedir no puede cuadrar la suma. En el modo sencillo, para guardar alguien tiene que haber fallado.', P.toggle(r.forbidSum, (v) => { r.forbidSum = v; })),
        P.setRow('Si hay empate en cabeza'), P.seg(['Victoria compartida', 'Desempate'], r.tie === E.TIE_BREAK ? 1 : 0, (i) => { r.tie = i ? E.TIE_BREAK : E.TIE_SHARED; }),
        note('Compartida: cada empatado suma 1/k de victoria. Desempate: rondas de una carta solo entre los empatados hasta que haya un ganador. Si acabáis empatados, la app pregunta igualmente qué hacéis.')));
      if (profile) {
        const acts = h('div', { class: 'row', style: { marginTop: '6px' } },
          h('div', { class: 'grow' }, P.secondary(r.id === st.defaultProfile ? 'Es el perfil por defecto' : 'Usar por defecto', '#F2B41F', () => { save(false); st.defaultProfile = r.id; P.save(); P.toast('Perfil por defecto: ' + r.name); P.refresh(); }, 'star')));
        if (st.profiles.length > 1 && st.profiles.some((x) => x.id === r.id)) acts.appendChild(h('div', null, P.secondary('Borrar', '#FF6A5C', () =>
          P.confirm('Borrar perfil', 'Se borrará «' + r.name + '». Las partidas jugadas con él no cambian.', 'Borrar', true, () => {
            st.profiles = st.profiles.filter((x) => x.id !== r.id);
            if (st.defaultProfile === r.id) st.defaultProfile = st.profiles[0].id;
            P.save(); P.pop();
          }), 'trash')));
        body.appendChild(acts);
      }
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.append(S.scrollEl, h('div', { class: 'foot' }, P.primary(profile ? 'Guardar perfil' : 'Usar estas reglas', () => save(true))));
      setTimeout(update, 0);
      return root;
    };
    function save(leave) {
      if (!String(r.name || '').trim()) r.name = 'Sin nombre';
      onSave(E.copy(r));
      if (leave) { P.pop(); P.toast(profile ? 'Perfil guardado' : 'Reglas aplicadas a esta partida'); }
    }
    return S;
  };

  // ------------------------------------------------------------------ copias de seguridad

  function Backup() {
    const S = {};
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Copias de seguridad', P.pop));
      const body = h('div', { class: 'body' });
      body.appendChild(h('div', { class: 'card', style: { padding: '4px 14px' } },
        P.kv('Jugadores', String(st.players.length)), P.kv('Partidas', st.games.length + (P.active() ? ' (1 en curso)' : '')),
        P.kv('Partidas en estadísticas', String(P.closedGames().length)), P.kv('Perfiles de reglas', String(st.profiles.length)),
        P.kv('Última copia', st.lastBackup ? P.dateTime(st.lastBackup) : 'Nunca')));
      body.appendChild(h('div', { class: 'note', style: { margin: '2px 2px 14px', fontSize: '13px' } },
        'La copia es un archivo con jugadores, partidas, reglas y estadísticas. Es compatible con la app de Android: podéis pasaros partidas y sumar estadísticas importándola con «Mezclar».'
        + ((st.groups || []).length ? ' También guarda tus grupos de juego: al restaurarla en otro móvil sigues dentro (y como creador, si lo eres). Por eso no se la pases a nadie: para compartir partidas, que se unan al grupo.' : '')));
      body.appendChild(P.primary('Exportar copia', exportBackup));
      body.appendChild(h('div', { style: { height: '10px' } }));
      const file = h('input', { type: 'file', accept: '.json,application/json,text/plain', style: { display: 'none' } });
      file.onchange = () => {
        const f = file.files && file.files[0];
        if (!f) return;
        f.text().then((txt) => { try { confirmImport(E.parseBackup(txt)); } catch (e) { P.info('No se puede importar', e.message); } file.value = ''; });
      };
      body.append(file, P.secondary('Importar copia', '#4AA0FF', () => file.click(), 'upload'));
      if (st.prevData) {
        body.appendChild(P.section('Volver atrás'));
        body.appendChild(h('div', { class: 'card' }, h('div', { class: 'text2', style: { fontSize: '13.5px', lineHeight: '1.45' } },
          'Antes de la última importación (' + P.dateTime(st.prevTime) + ') se guardaron tus datos de entonces. Puedes recuperarlos si la copia no era la buena.'),
        h('div', { style: { height: '10px' } }), P.secondary('Recuperar datos anteriores', '#FFC83A', restorePrev, 'undo')));
      }
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };

    function exportBackup() {
      const d = new Date();
      const name = 'poch-copia-' + d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + '.json';
      const txt = JSON.stringify(E.buildBackup(P.snapshot(), 'web ' + P.VERSION, Date.now()), null, 1);
      const file = new File([txt], name, { type: 'application/json' });
      const done = () => { st.lastBackup = Date.now(); P.save(); P.refresh(); };
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        navigator.share({ files: [file], title: 'Copia de Poch!' }).then(() => { done(); P.toast('Copia exportada'); }).catch(() => {});
      } else {
        const a = h('a', { href: URL.createObjectURL(file), download: name });
        document.body.appendChild(a); a.click(); a.remove();
        done();
        P.toast('Copia descargada: ' + name);
      }
    }

    function confirmImport(d) {
      const msg = 'Copia' + (d.exported ? ' del ' + P.dateTime(d.exported) : '') + ':\n• ' + P.plural(d.players.length, 'jugador', 'jugadores')
        + '\n• ' + P.plural(d.games.length, 'partida', 'partidas') + (d.activeGame ? ' (1 en curso)' : '') + '\n• ' + P.plural(d.profiles.length, 'perfil de reglas', 'perfiles de reglas')
        + '\n\nMezclar: añade sus jugadores y partidas a los tuyos (podrás unir cada jugador con uno tuyo). Para pasaros partidas entre vosotros.\n\nSustituir: borra tus datos actuales y deja solo los de la copia.';
      P.dialog('Importar copia', msg, null, [
        { label: 'Cancelar' },
        { label: 'Sustituir todo', style: 'danger', run: () => P.confirm('Sustituir todo', 'Se reemplazarán TODOS tus datos (' + P.plural(st.players.length, 'jugador', 'jugadores') + ', ' + P.plural(st.games.length, 'partida', 'partidas') + '). Se guardan aparte por si quieres volver atrás.', 'Sustituir', true, () => apply(d, 'Copia restaurada')) },
        { label: 'Mezclar', style: 'main', run: () => P.push(Merge(d)) },
      ]);
    }
    function apply(d, title) {
      const ok = P.replaceAll(d, true);
      P.syncQuiet(false);
      P.popToRoot(0); P.refresh();
      P.info(ok ? title : 'Revisa los datos', ok ? 'Restauración verificada: ' + P.plural(st.players.length, 'jugador', 'jugadores') + ', ' + P.plural(st.games.length, 'partida', 'partidas') + ' y ' + P.plural(st.profiles.length, 'perfil', 'perfiles') + ((st.groups || []).length ? ' y ' + P.plural(st.groups.length, 'grupo de juego', 'grupos de juego') + ', que se sincronizan ahora' : '') + '. Todo coincide con la copia.'
        : 'Los datos se han restaurado, pero la comprobación final no coincide del todo. Si ves algo raro, usa «Recuperar datos anteriores».');
    }
    function restorePrev() {
      let d;
      try { d = E.parseBackup(JSON.stringify(st.prevData)); } catch (e) { P.info('No disponible', 'No se pudieron leer los datos anteriores.'); st.prevData = null; P.save(); P.refresh(); return; }
      P.confirm('Recuperar datos anteriores', 'Se volverá a los datos que tenías antes de la última importación (' + P.plural(d.players.length, 'jugador', 'jugadores') + ', ' + P.plural(d.games.length, 'partida', 'partidas') + '). Lo actual se guarda aparte.',
        'Recuperar', true, () => apply(d, 'Datos recuperados'));
    }
    return S;
  }

  /** Mezclar una copia: emparejar cada jugador de la copia con uno mío o añadirlo como nuevo. */
  function Merge(inc) {
    const S = { map: E.suggest(P.snapshot(), inc) };
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Mezclar copia', P.pop));
      const body = h('div', { class: 'body' });
      const live = inc.games.filter((g) => g.status === E.ACTIVE).length, games = inc.games.length - live;
      body.appendChild(h('div', { class: 'text2', style: { fontSize: '13.5px', lineHeight: '1.45', margin: '0 2px 6px' } },
        'La copia trae ' + P.plural(games, 'partida', 'partidas') + (live ? ' (y ' + P.plural(live, 'partida en curso', 'partidas en curso') + ', que no se importan)' : '')
        + '. Une cada jugador de la copia con el tuyo si es la misma persona, así sus estadísticas se suman. Si no lo tienes, se añade como jugador nuevo.'));
      body.appendChild(P.section('Jugadores de la copia'));
      for (const p of inc.players) {
        const t = S.map[p.id], mine = t ? P.player(t) : null, same = mine && t === p.id;
        body.appendChild(h('button', { class: 'lrow', style: mine ? { borderColor: 'rgba(43,196,106,.6)' } : null, onclick: () => choose(p) }, P.dot(p.color, 26),
          h('div', { style: { flex: '1', minWidth: '0' } }, h('div', { class: 't ell' }, p.name),
            h('div', { class: 'ell', style: { fontSize: '13px', marginTop: '4px', fontWeight: '500', color: mine ? 'var(--green)' : 'var(--blue-text)' } }, mine ? (same ? 'Es tu jugador ' : 'Se une con ') + mine.name : 'Se añade como jugador nuevo')),
          mine ? P.dot(mine.color, 18) : null, P.icon('chevD', 'var(--text2)', 20)));
      }
      if (!inc.players.length) body.appendChild(h('div', { class: 'muted' }, 'La copia no tiene jugadores.'));
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.append(S.scrollEl, h('div', { class: 'foot' }, P.primary('Mezclar', doMerge)));
      return root;
    };
    function choose(p) {
      const cur = S.map[p.id];
      const body = h('div');
      body.appendChild(P.option('Añadir como jugador nuevo', 'Se crea «' + p.name + '» con sus partidas.', !cur, () => { S.map[p.id] = null; P.closeOverlay(); P.refresh(); }));
      for (const q of P.sorted()) {
        const other = inc.players.find((x) => x.id !== p.id && S.map[x.id] === q.id);
        const o = P.option('Es ' + q.name, other ? 'Ya unido con «' + other.name + '»' : null, q.id === cur, () => {
          if (other) { P.toast(q.name + ' ya está unido con otro jugador de la copia'); return; }
          S.map[p.id] = q.id; P.closeOverlay(); P.refresh();
        });
        if (other) o.style.opacity = '.5';
        body.appendChild(o);
      }
      P.sheet('¿Quién es ' + p.name + '?', body);
    }
    function doMerge() {
      let r;
      try { r = E.merge(P.snapshot(), inc, S.map); } catch (e) { P.info('No se pudo mezclar', e.message); return; }
      const ok = P.replaceAll(r.data, true);
      P.syncQuiet(false);
      P.popToRoot(P.tab()); P.refresh();
      let m = '• ' + P.plural(r.newGames, 'partida nueva', 'partidas nuevas');
      if (r.updatedGames) m += '\n• ' + P.plural(r.updatedGames, 'partida actualizada', 'partidas actualizadas');
      if (r.sameGames) m += '\n• ' + P.plural(r.sameGames, 'partida que ya tenías', 'partidas que ya tenías');
      if (r.skippedActive) m += '\n• ' + P.plural(r.skippedActive, 'partida en curso no importada', 'partidas en curso no importadas');
      m += '\n• ' + P.plural(r.newPlayers, 'jugador nuevo', 'jugadores nuevos') + ', ' + r.mergedPlayers + ' unidos con los tuyos';
      if (r.newProfiles) m += '\n• ' + P.plural(r.newProfiles, 'perfil de reglas nuevo', 'perfiles de reglas nuevos');
      if (r.newGroups) m += '\n• ' + P.plural(r.newGroups, 'grupo de juego', 'grupos de juego');
      m += ok ? '\n\nSi algo no te convence, en Copias de seguridad puedes «Recuperar datos anteriores».' : '\n\nLa comprobación final no coincide del todo. Si ves algo raro, usa «Recuperar datos anteriores».';
      P.info('Copia mezclada', m);
    }
    return S;
  }
})();
