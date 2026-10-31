/* Poch! web · pantallas de grupos de juego: lista, crear, unirse, detalle. */
(function () {
  'use strict';
  const P = window.P, E = window.Engine, h = P.h, st = P.st;
  const GUIDE = 'https://github.com/kolopenco/Poch-Tu-marcador-de-pocha/blob/main/docs/grupo.md';

  /** Mensaje entendible para los errores de Firebase. */
  P.syncError = (e) => {
    const m = String((e && e.message) || e || '');
    if (e && e.offline) return 'No hay conexión a Internet. Inténtalo cuando la tengas.';
    if (/ADMIN_ONLY_OPERATION|OPERATION_NOT_ALLOWED/.test(m)) return 'En Firebase falta activar el acceso «Anónimo» (Authentication › Sign-in method).';
    if (/API key not valid|API_KEY_INVALID/.test(m)) return 'La configuración de Firebase no es válida: revisa que has copiado bien el apiKey.';
    if (/database \(default\) does not exist|NOT_FOUND/.test(m)) return 'No se encuentra la base de datos: crea «Firestore Database» en tu proyecto de Firebase.';
    if (/PERMISSION_DENIED|Missing or insufficient permissions/.test(m)) return 'Firebase ha denegado el acceso: revisa que has pegado las reglas de seguridad de la guía, o la invitación ya no es válida.';
    return m || 'Error desconocido';
  };

  function help() {
    P.sheet('Grupos de juego', h('div', { class: 'text2', style: { fontSize: '14.5px', lineHeight: '1.55' } },
      h('p', { style: { marginTop: '0' } }, 'Un grupo comparte jugadores, partidas y estadísticas entre vuestros móviles (Android y iPhone).'),
      h('p', null, h('b', { style: { color: '#fff' } }, 'Si te invitan: '), 'abre el enlace que te han mandado (o cópialo y pulsa «Unirse»), di quién eres y listo. No hace falta cuenta.'),
      h('p', null, h('b', { style: { color: '#fff' } }, 'Para crear un grupo: '), 'una sola persona del grupo crea un proyecto gratuito de Firebase (unos 10 minutos con la guía) y pega aquí su configuración. Los datos quedan en ese proyecto: solo entra quien tenga la invitación.'),
      h('p', null, h('b', { style: { color: '#fff' } }, 'Cuándo se sincroniza: '), 'al abrir la app baja lo nuevo, y cada partida se sube al cerrarla (o al corregirla después). La partida en curso no se comparte.'),
      h('a', { class: 'secondary', href: GUIDE, target: '_blank', rel: 'noopener', style: { marginTop: '14px', color: '#4AA0FF', borderColor: '#4AA0FF99', textDecoration: 'none' } }, P.icon('info', '#4AA0FF', 20), 'Guía para crear un grupo')));
  }

  // ------------------------------------------------------------------ lista

  P.Groups = function () {
    const S = {};
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Grupos de juego', P.pop, P.ibtn('info', '#fff', 22, help, 'Cómo funciona')));
      const body = h('div', { class: 'body' });
      const gs = st.groups || [];
      if (!gs.length) body.appendChild(h('div', { class: 'card', style: { textAlign: 'center', padding: '20px 16px' } },
        P.icon('people', 'var(--blue-text)', 36), h('div', { style: { fontSize: '17px', fontWeight: '700', marginTop: '10px' } }, 'Jugad en grupo'),
        h('div', { class: 'text2', style: { fontSize: '14px', lineHeight: '1.5', marginTop: '6px' } }, 'Compartid partidas y estadísticas entre vuestros móviles, sin pasaros archivos.')));
      for (const g of gs) {
        const me = g.me && P.player(g.me);
        body.appendChild(P.lrow(h('span', { style: { width: '40px', height: '40px', borderRadius: '11px', background: 'rgba(30,123,242,.16)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: '0' } }, P.icon('people', '#3A92FF', 22)),
          g.name, (me ? 'Tú: ' + me.name + ' · ' : '') + (g.lastSync ? 'Sincronizado ' + P.dateTime(g.lastSync) : 'Sin sincronizar'), () => P.push(Detail(g.id))));
      }
      body.appendChild(h('div', { style: { height: '6px' } }));
      body.appendChild(P.primary('Unirse con una invitación', () => P.push(Join(''))));
      body.appendChild(h('div', { style: { height: '10px' } }));
      body.appendChild(P.secondary('Crear un grupo nuevo', '#4AA0FF', () => P.push(Create()), 'plus'));
      body.appendChild(P.note('Para crear un grupo hace falta un proyecto gratuito de Firebase. Para unirse solo hace falta la invitación.'));
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  };

  // ------------------------------------------------------------------ elegir «quién eres tú»

  /** Lista de opciones: jugadores (id, nombre, color) + «Soy nuevo». Devuelve { el, value() }. */
  function whoAmI(players, preset, presetName, onChange, sub) {
    let sel = preset || null;
    const name = P.input('Tu nombre', presetName || '');
    name.oninput = () => { if (onChange) onChange(sel, name.value); };
    const box = h('div');
    const paint = () => {
      box.replaceChildren();
      for (const p of players) box.appendChild(P.option(p.name, sub ? sub(p) : null, sel === p.id, () => { sel = p.id; paint(); if (onChange) onChange(sel, name.value); }));
      box.appendChild(P.option('Soy nuevo', 'No estoy en la lista', sel === '__new', () => { sel = '__new'; paint(); if (onChange) onChange(sel, name.value); setTimeout(() => name.focus(), 50); }));
      if (sel === '__new') box.appendChild(h('div', { style: { margin: '0 0 8px' } }, name));
    };
    paint();
    return { el: box, value: () => sel === '__new' ? { newName: name.value.trim() } : sel ? { id: sel } : null };
  }

  // ------------------------------------------------------------------ crear

  function Create() {
    const S = { cfgText: '', name: '' };
    let who;
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Crear grupo', P.pop));
      const body = h('div', { class: 'body' });
      body.appendChild(h('div', { class: 'banner', style: { marginBottom: '6px' } }, P.icon('info', 'var(--blue-text)', 20),
        h('div', { class: 'grow', style: { fontWeight: '400' } }, 'Antes necesitas un proyecto gratuito de Firebase preparado. ', h('a', { href: GUIDE, target: '_blank', rel: 'noopener', style: { fontWeight: '600', color: '#fff' } }, 'Ver la guía paso a paso'))));
      body.appendChild(P.section('1. Configuración de Firebase'));
      const ta = h('textarea', { class: 'input', rows: 6, placeholder: 'Pega aquí el bloque firebaseConfig = { apiKey: "...", projectId: "...", ... }', style: { fontFamily: 'monospace', fontSize: '13px', resize: 'vertical' } });
      ta.value = S.cfgText;
      ta.oninput = () => { S.cfgText = ta.value; };
      body.appendChild(ta);
      body.appendChild(P.section('2. Nombre del grupo'));
      const nm = P.input('Ej.: La pocha de los viernes', S.name);
      nm.oninput = () => { S.name = nm.value; };
      body.appendChild(nm);
      body.appendChild(P.section('3. ¿Quién eres tú?'));
      who = whoAmI(P.sorted(), S.me, S.meName, (v, n) => { S.me = v; S.meName = n; });
      body.appendChild(who.el);
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.append(S.scrollEl, h('div', { class: 'foot' }, P.primary('Crear grupo', go)));
      return root;
    };
    async function go() {
      const cfg = P.parseFirebaseConfig(S.cfgText);
      if (!cfg) { P.info('Falta la configuración', 'No encuentro el apiKey y el projectId en lo que has pegado. Copia el bloque firebaseConfig entero desde Firebase (Configuración del proyecto › Tus apps).'); return; }
      const name = S.name.trim();
      if (!name) { P.toast('Ponle un nombre al grupo'); return; }
      const w = who.value();
      if (!w || (w.newName !== undefined && !w.newName)) { P.toast('Dinos quién eres'); return; }
      let me = w.id;
      if (w.newName !== undefined) {
        if (P.nameTaken(w.newName)) { P.toast('Ya hay un jugador llamado ' + w.newName); return; }
        const p = { id: P.newId('j'), name: w.newName, color: P.nextColor(), created: Date.now(), updated: Date.now() };
        st.players.push(p); P.save(); me = p.id;
      }
      P.toast('Creando el grupo…');
      try {
        const g = await P.createGroup(cfg, name, me);
        P.pop();
        P.push(Detail(g.id));
        invite(g, true);
      } catch (e) { P.info('No se pudo crear el grupo', P.syncError(e)); }
    }
    return S;
  }

  // ------------------------------------------------------------------ unirse

  function Join(prefill) {
    const S = { text: prefill || '', fetched: null, map: null, busy: false };
    let who;
    S.build = () => {
      const root = h('div', { class: 'screen' });
      root.appendChild(P.cheader('Unirse a un grupo', P.pop));
      const body = h('div', { class: 'body' });
      if (!S.fetched) {
        body.appendChild(h('div', { class: 'text2', style: { fontSize: '14px', lineHeight: '1.5', margin: '0 2px 12px' } }, 'Pega la invitación que te han mandado (el enlace o el mensaje entero).'));
        const ta = h('textarea', { class: 'input', rows: 5, placeholder: 'https://…#unirse=…', style: { fontSize: '14px', resize: 'vertical' } });
        ta.value = S.text;
        ta.oninput = () => { S.text = ta.value; };
        body.appendChild(ta);
        if (navigator.clipboard && navigator.clipboard.readText) body.appendChild(h('div', { style: { marginTop: '8px' } }, P.chip('Pegar', false, () => navigator.clipboard.readText().then((t) => { S.text = t; ta.value = t; }).catch(() => {}))));
        S.scrollEl = h('div', { class: 'scroll' }, body);
        root.append(S.scrollEl, h('div', { class: 'foot' }, P.primary(S.busy ? 'Entrando…' : 'Continuar', fetch1, !S.busy)));
        return root;
      }
      const f = S.fetched;
      body.appendChild(h('div', { class: 'card row', style: { gap: '12px' } }, P.icon('people', 'var(--blue-text)', 28),
        h('div', { class: 'grow' }, h('div', { style: { fontSize: '17px', fontWeight: '700' } }, f.g.name),
          h('div', { class: 'text2', style: { fontSize: '13px', marginTop: '3px' } }, P.plural(f.players.length, 'jugador', 'jugadores') + ' · ' + P.plural(f.games.length, 'partida', 'partidas')))));
      body.appendChild(P.section('¿Quién eres tú?'));
      who = whoAmI(f.players.slice().sort((a, b) => a.name.localeCompare(b.name, 'es')), S.me, S.meName, (v, n) => { S.me = v; S.meName = n; },
        (p) => { const c = (f.claimed || {})[p.id]; return !c ? null : c === 1 ? 'Ya lo usa otro móvil (puedes ser tú en otro dispositivo)' : 'Ya lo usan ' + c + ' móviles'; });
      body.appendChild(who.el);
      body.appendChild(h('div', { class: 'note', style: { margin: '2px 2px 8px' } }, 'Si ya entraste desde otro móvil, elígete en la lista: no te crees de nuevo.'));
      if (st.players.length && f.players.length) {
        body.appendChild(P.section('Une los jugadores con los tuyos'));
        body.appendChild(h('div', { class: 'note', style: { margin: '-4px 2px 10px', fontSize: '13px' } }, 'Si ya tenías a alguno en tu móvil, únelo para que sus partidas y estadísticas se sumen.'));
        for (const p of f.players) {
          const t = S.map[p.id], mine = t ? P.player(t) : null;
          body.appendChild(h('button', { class: 'lrow', style: mine ? { borderColor: 'rgba(43,196,106,.6)' } : null, onclick: () => choose(p) }, P.dot(p.color, 26),
            h('div', { style: { flex: '1', minWidth: '0' } }, h('div', { class: 't ell' }, p.name),
              h('div', { class: 'ell', style: { fontSize: '13px', marginTop: '4px', fontWeight: '500', color: mine ? 'var(--green)' : 'var(--blue-text)' } }, mine ? 'Es tu jugador ' + mine.name : 'Se añade a tus jugadores')),
            P.icon('chevD', 'var(--text2)', 20)));
        }
      }
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.append(S.scrollEl, h('div', { class: 'foot' }, P.primary(S.busy ? 'Uniendo…' : 'Unirme al grupo', apply, !S.busy)));
      return root;
    };
    async function fetch1() {
      const inv = P.parseInvite(S.text);
      if (!inv) { P.info('Invitación no válida', 'No reconozco la invitación. Copia el enlace completo que te mandaron.'); return; }
      if (P.groupById(inv.id)) { P.info('Ya estás en este grupo', '«' + P.groupById(inv.id).name + '» ya está en tu lista de grupos.'); return; }
      S.busy = true; P.refresh();
      try {
        S.fetched = await P.joinFetch(inv);
        const mine = P.snapshot();
        S.map = E.suggest(mine, { players: S.fetched.players, games: S.fetched.games });
      } catch (e) { P.info('No se pudo entrar', P.syncError(e)); }
      S.busy = false; P.refresh();
    }
    function choose(p) {
      const cur = S.map[p.id];
      const body = h('div');
      body.appendChild(P.option('Añadir como jugador nuevo', null, !cur, () => { S.map[p.id] = null; P.closeOverlay(); P.refresh(); }));
      for (const q of P.sorted()) {
        const other = S.fetched.players.find((x) => x.id !== p.id && S.map[x.id] === q.id);
        const o = P.option('Es ' + q.name, other ? 'Ya unido con «' + other.name + '»' : null, q.id === cur, () => {
          if (other) { P.toast(q.name + ' ya está unido con otro jugador'); return; }
          S.map[p.id] = q.id; P.closeOverlay(); P.refresh();
        });
        if (other) o.style.opacity = '.5';
        body.appendChild(o);
      }
      P.sheet('¿Quién es ' + p.name + '?', body);
    }
    async function apply() {
      const w = who.value();
      if (!w || (w.newName !== undefined && !w.newName)) { P.toast('Dinos quién eres'); return; }
      if (w.newName !== undefined && (P.nameTaken(w.newName) || S.fetched.players.some((p) => E.norm(p.name) === E.norm(w.newName)))) { P.toast('Ese nombre ya existe'); return; }
      S.busy = true; P.refresh();
      try {
        const r = await P.joinApply(S.fetched.g, S.map, w.id, w.newName);
        P.popToRoot(P.tab());
        if (P.tab() === 4) P.push(P.Groups()); else P.refresh();
        if (r.error) { P.info('Ya estás en «' + S.fetched.g.name + '»', 'Pero no se ha podido terminar de sincronizar: ' + P.syncError(r.error) + '\n\nSe volverá a intentar al abrir la app. Si sigue fallando, avisa a quien creó el grupo.'); return; }
        P.info('¡Ya estás en «' + S.fetched.g.name + '»!', 'Se han descargado ' + P.plural(r.newGames, 'partida', 'partidas') + '. A partir de ahora, al abrir la app se bajan las novedades y cada partida se sube al cerrarla.\n\nAl crear una partida, elige el grupo para que se comparta.');
      } catch (e) { S.busy = false; P.refresh(); P.info('No se pudo unir', P.syncError(e)); }
    }
    return S;
  }
  P.JoinScreen = Join;

  // ------------------------------------------------------------------ detalle

  function invite(g, fresh) {
    const txt = P.inviteText(g);
    const share = () => {
      if (navigator.share) navigator.share({ text: txt }).catch(() => {});
      else if (navigator.clipboard) navigator.clipboard.writeText(txt).then(() => P.toast('Invitación copiada'));
    };
    P.dialog(fresh ? 'Grupo creado' : 'Invitar al grupo', (fresh ? 'Ya está creado «' + g.name + '». ' : '') + 'Manda la invitación a tus amigos (WhatsApp, correo…). Quien la tenga podrá entrar en el grupo, así que no la publiques.', null, [
      { label: 'Copiar', run: () => navigator.clipboard && navigator.clipboard.writeText(txt).then(() => P.toast('Invitación copiada')) },
      { label: 'Compartir', style: 'main', run: share },
    ]);
  }

  function Detail(id) {
    const S = { members: null };
    S.build = () => {
      const g = P.groupById(id);
      const root = h('div', { class: 'screen' });
      if (!g) { root.append(P.cheader('Grupo', P.pop), h('div', { class: 'empty' }, 'Ya no estás en este grupo.')); return root; }
      root.appendChild(P.cheader(g.name, P.pop));
      const body = h('div', { class: 'body' });
      const me = g.me && P.player(g.me);
      const games = st.games.filter((x) => x.group === g.id && E.counts(x)).length;
      body.appendChild(h('div', { class: 'card', style: { padding: '4px 14px' } },
        P.kv('Tú eres', me ? me.name : '—'), P.kv('Partidas del grupo', String(games)),
        P.kv('Última sincronización', g.lastSync ? P.dateTime(g.lastSync) : 'Nunca'), P.kv('Papel', g.owner ? 'Creador del grupo' : 'Miembro')));
      body.appendChild(P.primary('Invitar amigos', () => invite(g)));
      body.appendChild(h('div', { style: { height: '10px' } }));
      body.appendChild(P.secondary(P.syncing() ? 'Sincronizando…' : 'Sincronizar ahora', '#4AA0FF', async () => {
        try { const r = await P.syncGroup(g); P.refresh(); P.toast(r.newGames + r.updatedGames ? P.plural(r.newGames + r.updatedGames, 'partida nueva', 'partidas nuevas') : 'Todo al día'); }
        catch (e) { P.info('No se pudo sincronizar', P.syncError(e)); }
      }, 'sync'));
      body.appendChild(P.section('Jugadores del grupo'));
      const inG = [], outG = [];
      for (const p of P.sorted()) (P.groupsOf(p.id).indexOf(g) >= 0 ? inG : outG).push(p);
      body.appendChild(h('div', { class: 'card' }, inG.length ? inG.map((p, i) => [i ? h('div', { class: 'hr' }) : null,
        h('div', { class: 'row', style: { padding: '8px 0', gap: '10px' } }, P.dot(p.color, 18), h('div', { class: 'grow ell', style: { fontWeight: '500' } }, p.name + (p.id === g.me ? ' (tú)' : '')))])
        : h('div', { class: 'muted' }, 'Aún no hay jugadores en el grupo.')));
      if (outG.length) {
        body.appendChild(h('div', { style: { height: '10px' } }));
        body.appendChild(P.secondary('Añadir jugadores al grupo', '#4AA0FF', () => P.multiPick('Añadir al grupo',
          'Elige jugadores de tu móvil. Aparecerán en todos los móviles del grupo, aunque no tengan la app, y al unirse podrán elegirse.',
          outG, [], 'Añadir', (ids) => {
            if (!ids.length) return false;
            P.addPlayersToGroup(g, ids).then(() => { P.refresh(); P.toast(P.plural(ids.length, 'jugador añadido', 'jugadores añadidos') + ' al grupo'); })
              .catch((e) => { P.refresh(); P.info('No se pudieron añadir', P.syncError(e) + '\n\nSe volverá a intentar al sincronizar.'); });
            return true;
          }), 'plus'));
      }
      body.appendChild(h('div', { class: 'note', style: { margin: '8px 2px 0' } }, 'Pueden estar aunque no tengan la app: las partidas en las que jueguen se comparten igual. Si se unen, se eligen en «¿Quién eres tú?».'));
      body.appendChild(P.section('Miembros'));
      const mb = h('div', { class: 'card' }, h('div', { class: 'muted' }, 'Cargando…'));
      body.appendChild(mb);
      P.groupMembers(g).then((ms) => {
        mb.replaceChildren();
        if (!ms.length) mb.appendChild(h('div', { class: 'muted' }, 'Nadie todavía.'));
        ms.forEach((m, i) => {
          if (i) mb.appendChild(h('div', { class: 'hr' }));
          const lp = P.player((g.ids && g.ids[m.player]) || m.player);
          mb.appendChild(h('div', { class: 'row', style: { padding: '8px 0' } }, lp ? P.dot(lp.color, 18) : P.icon('people', 'var(--muted)', 18),
            h('div', { class: 'grow ell', style: { fontWeight: '500' } }, (lp ? lp.name : m.name || 'Sin nombre') + (m.uid === g.uid ? ' (este móvil)' : '')),
            g.owner && m.uid !== g.uid ? h('button', { class: 'chip', onclick: () => P.confirm('Quitar del grupo', 'Este móvil dejará de ver las partidas del grupo. Si tiene la invitación podría volver a entrar: cambia la invitación para evitarlo.', 'Quitar', true, async () => {
              try { await P.removeMember(g, m.uid); P.refresh(); } catch (e) { P.info('No se pudo quitar', P.syncError(e)); }
            }) }, 'Quitar') : null));
        });
      }).catch((e) => { mb.replaceChildren(h('div', { class: 'muted' }, P.syncError(e))); });
      if (g.owner) {
        body.appendChild(P.section('Como creador'));
        body.appendChild(P.secondary('Cambiar el nombre', '#B4BFD2', () => P.prompt('Nombre del grupo', 'Nombre', g.name, 'Guardar', async (n) => {
          if (!n) return;
          try { await P.renameGroup(g, n); P.refresh(); } catch (e) { P.info('No se pudo cambiar', P.syncError(e)); }
        }), 'edit'));
        body.appendChild(h('div', { style: { height: '10px' } }));
        body.appendChild(P.secondary('Cambiar la invitación', '#F2B41F', () => P.confirm('Cambiar la invitación', 'La invitación antigua dejará de servir para entrar. Quien ya está en el grupo sigue dentro.', 'Cambiar', false, async () => {
          try { await P.rotateKey(g); invite(g); } catch (e) { P.info('No se pudo cambiar', P.syncError(e)); }
        }), 'undo'));
      }
      body.appendChild(P.section('Salir'));
      body.appendChild(P.secondary('Salir del grupo', '#FF6A5C', () => P.confirm('Salir del grupo', 'Dejarás de recibir sus partidas. Las que ya tienes se quedan en tu móvil.', 'Salir', true, async () => {
        await P.leaveGroup(g); P.pop(); P.toast('Has salido de «' + g.name + '»');
      }), 'close'));
      S.scrollEl = h('div', { class: 'scroll' }, body);
      root.appendChild(S.scrollEl);
      return root;
    };
    return S;
  }
})();
