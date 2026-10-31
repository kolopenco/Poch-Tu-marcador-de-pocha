/*
 * Poch! web · grupos de juego sincronizados con Firebase (por REST, sin librerías).
 *
 * Cada grupo vive en el Firebase de quien lo creó. La app pública no lleva ninguna dirección:
 * la invitación trae el proyecto (apiKey + projectId), el id del grupo y su clave secreta.
 *
 * Firestore:
 *   groups/{g}                 { name, owner, key, created }
 *   groups/{g}/members/{uid}   { key, player, name, joined }
 *   groups/{g}/players/{id}    { json, updatedAt }
 *   groups/{g}/games/{id}      { json, updatedAt, by, deleted }
 *
 * Cada móvil guarda, por grupo, un mapa ids { idEnElGrupo: idLocal } para que «Mich» del grupo y
 * «Mich» del móvil sean la misma persona aunque se crearan en móviles distintos.
 */
(function () {
  'use strict';
  const P = window.P, E = window.Engine, st = P.st;

  // Direcciones de Google (se pueden cambiar para las pruebas con un servidor simulado)
  let EP = { auth: 'https://identitytoolkit.googleapis.com/v1', token: 'https://securetoken.googleapis.com/v1', fs: 'https://firestore.googleapis.com/v1' };
  try { const o = JSON.parse(localStorage.getItem('poch.endpoints') || 'null'); if (o) EP = o; } catch (e) { /* normal */ }

  const tokens = {};   // groupId → { idToken, exp }

  // ------------------------------------------------------------------ utilidades

  const rnd = (n) => {
    const a = new Uint8Array(n);
    crypto.getRandomValues(a);
    return Array.from(a, (b) => 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'[b % 57]).join('');
  };
  const b64u = (s) => btoa(unescape(encodeURIComponent(s))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const unb64u = (s) => decodeURIComponent(escape(atob(s.replace(/-/g, '+').replace(/_/g, '/'))));

  async function http(method, url, body, idToken) {
    const headers = { 'Content-Type': 'application/json' };
    if (idToken) headers.Authorization = 'Bearer ' + idToken;
    let r;
    try {
      r = await fetch(url, { method, headers, body: body == null ? undefined : JSON.stringify(body) });
    } catch (e) {
      const err = new Error('Sin conexión'); err.offline = true; throw err;
    }
    const txt = await r.text();
    let data = null;
    try { data = txt ? JSON.parse(txt) : null; } catch (e) { data = txt; }
    if (!r.ok) {
      const msg = (data && data.error && (data.error.message || data.error.status)) || ('HTTP ' + r.status);
      const err = new Error(msg); err.status = r.status; throw err;
    }
    return data;
  }

  /** Lee apiKey y projectId de lo que pega el usuario (el bloque firebaseConfig o un JSON). */
  P.parseFirebaseConfig = (text) => {
    const t = String(text || '');
    const api = (t.match(/apiKey["']?\s*[:=]\s*["']([^"']+)["']/) || [])[1];
    const pid = (t.match(/projectId["']?\s*[:=]\s*["']([^"']+)["']/) || [])[1];
    return api && pid ? { apiKey: api.trim(), projectId: pid.trim() } : null;
  };

  P.WEB_URL = 'https://kolopenco.github.io/Poch-Tu-marcador-de-pocha/';
  P.inviteCode = (g) => b64u(JSON.stringify({ v: 1, p: g.projectId, k: g.apiKey, g: g.id, s: g.key, n: g.name }));
  P.inviteLink = (g) => P.WEB_URL + '#unirse=' + P.inviteCode(g);
  P.inviteText = (g) => 'Únete a mi grupo de Poch! «' + g.name + '»:\n' + P.inviteLink(g)
    + '\n\niPhone: abre el enlace en Safari.\nAndroid: copia este mensaje y en Poch! ve a Ajustes › Grupos de juego › Unirse.';
  /** Acepta el enlace, el mensaje entero o solo el código. */
  P.parseInvite = (text) => {
    const t = String(text || '').trim();
    const m = t.match(/unirse=([A-Za-z0-9_-]+)/) || t.match(/^([A-Za-z0-9_-]{40,})$/);
    if (!m) return null;
    try {
      const o = JSON.parse(unb64u(m[1]));
      if (o.v !== 1 || !o.p || !o.k || !o.g || !o.s) return null;
      return { projectId: o.p, apiKey: o.k, id: o.g, key: o.s, name: o.n || 'Grupo' };
    } catch (e) { return null; }
  };

  // ------------------------------------------------------------------ Firestore

  const docPath = (g, rest) => 'projects/' + g.projectId + '/databases/(default)/documents/groups/' + g.id + (rest ? '/' + rest : '');
  const strF = (v) => ({ stringValue: String(v) });
  const fromF = (f) => {
    if (!f) return null;
    if ('stringValue' in f) return f.stringValue;
    if ('booleanValue' in f) return f.booleanValue;
    if ('integerValue' in f) return Number(f.integerValue);
    if ('timestampValue' in f) return f.timestampValue;
    if ('nullValue' in f) return null;
    return null;
  };
  // Marca de tiempo con 9 decimales siempre, para poder compararlas como texto
  const normTs = (t) => String(t || '').replace(/(\.\d+)?Z$/, (m, f) => '.' + (((f || '.').slice(1)) + '000000000').slice(0, 9) + 'Z');
  const fields = (doc) => { const o = {}; for (const k of Object.keys(doc.fields || {})) o[k] = fromF(doc.fields[k]); if (o.updatedAt) o.updatedAt = normTs(o.updatedAt); return o; };
  const docId = (doc) => doc.name.split('/').pop();

  async function signUp(g) {
    const r = await http('POST', EP.auth + '/accounts:signUp?key=' + encodeURIComponent(g.apiKey), { returnSecureToken: true });
    g.uid = r.localId; g.refresh = r.refreshToken;
    tokens[g.id] = { idToken: r.idToken, exp: Date.now() + (Number(r.expiresIn || 3600) - 120) * 1000 };
  }

  async function token(g) {
    const t = tokens[g.id];
    if (t && t.exp > Date.now()) return t.idToken;
    if (g.refresh) {
      try {
        const r = await http('POST', EP.token + '/token?key=' + encodeURIComponent(g.apiKey), { grant_type: 'refresh_token', refresh_token: g.refresh });
        g.refresh = r.refresh_token; g.uid = r.user_id;
        tokens[g.id] = { idToken: r.id_token, exp: Date.now() + (Number(r.expires_in || 3600) - 120) * 1000 };
        return r.id_token;
      } catch (e) { if (e.offline) throw e; /* identidad perdida: se crea otra y se vuelve a entrar con la clave */ }
    }
    await signUp(g);
    await writeMember(g);
    P.save();
    return tokens[g.id].idToken;
  }

  async function commit(g, writes) {
    const tk = await token(g);
    for (let i = 0; i < writes.length; i += 400) {
      await http('POST', EP.fs + '/projects/' + g.projectId + '/databases/(default)/documents:commit', { writes: writes.slice(i, i + 400) }, tk);
    }
  }

  async function query(g, collection, since) {
    const tk = await token(g);
    const q = { from: [{ collectionId: collection }], orderBy: [{ field: { fieldPath: 'updatedAt' } }] };
    if (since) q.where = { fieldFilter: { field: { fieldPath: 'updatedAt' }, op: 'GREATER_THAN', value: { timestampValue: since } } };
    const r = await http('POST', EP.fs + '/' + docPath(g) + ':runQuery', { structuredQuery: q }, tk);
    return (r || []).filter((x) => x.document).map((x) => x.document);
  }

  function writeMember(g) {
    const me = g.me ? P.player(g.me) : null;
    return http('POST', EP.fs + '/projects/' + g.projectId + '/databases/(default)/documents:commit', {
      writes: [{
        update: {
          name: docPath(g, 'members/' + g.uid),
          fields: { key: strF(g.key), player: strF(remoteId(g, g.me) || ''), name: strF(me ? me.name : ''), joined: strF(Date.now()) },
        },
      }],
    }, tokens[g.id].idToken);
  }

  const upd = (name, f) => ({ update: { name, fields: f }, updateTransforms: [{ fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' }] });

  // ------------------------------------------------------------------ ids grupo ↔ móvil

  const localId = (g, rid) => (g.ids && g.ids[rid]) || rid;
  function remoteId(g, lid) {
    if (!lid) return null;
    for (const r of Object.keys(g.ids || {})) if (g.ids[r] === lid) return r;
    return lid;
  }
  function inverse(g) { const m = {}; for (const r of Object.keys(g.ids || {})) m[g.ids[r]] = r; return m; }

  // ------------------------------------------------------------------ crear, unirse, salir

  P.groupById = (id) => (st.groups || []).find((g) => g.id === id);

  /** Crea un grupo nuevo en el Firebase indicado. me = id local de «quién eres tú». */
  P.createGroup = async (cfg, name, me) => {
    const g = { id: rnd(20), key: rnd(24), name, apiKey: cfg.apiKey, projectId: cfg.projectId, owner: true, me, ids: {}, roster: {}, pending: [], lastPlayers: null, lastGames: null, lastSync: 0 };
    await signUp(g);
    const tk = tokens[g.id].idToken;
    await http('POST', EP.fs + '/projects/' + g.projectId + '/databases/(default)/documents:commit', {
      writes: [{ update: { name: docPath(g), fields: { name: strF(name), owner: strF(g.uid), key: strF(g.key), created: strF(Date.now()) } }, currentDocument: { exists: false } }],
    }, tk);
    await writeMember(g);
    st.groups = (st.groups || []).concat([g]);
    // Sube a «quién eres tú» para que los demás puedan elegirlo
    if (me) g.dirtyPlayers = [me];
    P.save();
    await P.syncGroup(g);
    return g;
  };

  /** Primer paso de unirse: entra con la clave y descarga jugadores y partidas del grupo (sin aplicarlos aún). */
  P.joinFetch = async (inv) => {
    const g = { id: inv.id, key: inv.key, name: inv.name, apiKey: inv.apiKey, projectId: inv.projectId, owner: false, me: null, ids: {}, roster: {}, pending: [], lastPlayers: null, lastGames: null, lastSync: 0 };
    await signUp(g);
    await writeMember(g);
    const tk = tokens[g.id].idToken;
    try { const gd = fields(await http('GET', EP.fs + '/' + docPath(g), null, tk)); if (gd.name) g.name = gd.name; } catch (e) { /* el nombre de la invitación vale */ }
    const players = (await query(g, 'players')).map((d) => JSON.parse(fields(d).json || '{}')).filter((p) => p.id);
    const games = (await query(g, 'games')).map((d) => fields(d)).filter((f) => !f.deleted && f.json).map((f) => E.normGame(JSON.parse(f.json)));
    return { g, players, games };
  };

  /** Segundo paso: aplica el emparejamiento elegido (map: idGrupo → idLocal|null) y queda unido. */
  P.joinApply = async (g, map, meRemote, meNewName) => {
    g.ids = {};
    for (const r of Object.keys(map)) if (map[r]) g.ids[r] = map[r];
    if (meNewName) {
      const p = { id: P.newId('j'), name: meNewName, color: P.nextColor(), created: Date.now(), updated: Date.now() };
      st.players.push(p);
      g.me = p.id;
      g.dirtyPlayers = [p.id];
    } else g.me = localId(g, meRemote);
    st.groups = (st.groups || []).filter((x) => x.id !== g.id).concat([g]);
    P.save();
    try { await writeMember(g); } catch (e) { /* se reintenta al sincronizar */ }
    return P.syncGroup(g);
  };

  P.leaveGroup = async (g) => {
    try {
      const tk = await token(g);
      await http('DELETE', EP.fs + '/' + docPath(g, 'members/' + g.uid), null, tk);
    } catch (e) { /* aunque falle, se sale en este móvil */ }
    st.groups = st.groups.filter((x) => x !== g);
    for (const game of st.games) if (game.group === g.id) delete game.group;
    P.save();
  };

  P.groupMembers = async (g) => {
    const tk = await token(g);
    const r = await http('GET', EP.fs + '/' + docPath(g, 'members') + '?pageSize=300', null, tk);
    return ((r && r.documents) || []).map((d) => Object.assign({ uid: docId(d) }, fields(d)));
  };

  P.removeMember = async (g, uid) => {
    const tk = await token(g);
    await http('DELETE', EP.fs + '/' + docPath(g, 'members/' + uid), null, tk);
  };

  /** Cambia la clave: las invitaciones antiguas dejan de servir (quien ya está, sigue). */
  P.rotateKey = async (g) => {
    const key = rnd(24);
    const tk = await token(g);
    await http('POST', EP.fs + '/projects/' + g.projectId + '/databases/(default)/documents:commit', { writes: [{ update: { name: docPath(g), fields: { key: strF(key) } }, updateMask: { fieldPaths: ['key'] } }] }, tk);
    g.key = key;
    await writeMember(g);
    P.save();
  };

  P.renameGroup = async (g, name) => {
    const tk = await token(g);
    await http('POST', EP.fs + '/projects/' + g.projectId + '/databases/(default)/documents:commit', { writes: [{ update: { name: docPath(g), fields: { name: strF(name) } }, updateMask: { fieldPaths: ['name'] } }] }, tk);
    g.name = name;
    P.save();
  };

  // ------------------------------------------------------------------ marcar cambios

  /** Una partida del grupo ha cambiado (cerrada, corregida o borrada): se sube en la próxima sincronización. */
  P.markGame = (game) => {
    if (!game || !game.group) return;
    const g = P.groupById(game.group);
    if (!g) return;
    game.updated = Date.now();
    if (g.pending.indexOf(game.id) < 0) g.pending.push(game.id);
    P.save();
  };
  P.markDeleted = (game) => {
    const g = game && game.group && P.groupById(game.group);
    if (!g) return;
    (g.deleted = g.deleted || []).push(game.id);
    g.pending = g.pending.filter((x) => x !== game.id);
    P.save();
  };
  P.markPlayer = (p) => {
    p.updated = Date.now();
    for (const g of st.groups || []) {
      const rid = remoteId(g, p.id);
      if (g.roster[rid] || g.me === p.id) { g.dirtyPlayers = g.dirtyPlayers || []; if (g.dirtyPlayers.indexOf(p.id) < 0) g.dirtyPlayers.push(p.id); }
    }
    P.save();
  };

  // ------------------------------------------------------------------ sincronizar

  let running = null;
  P.syncing = () => !!running;

  /** Sincroniza todos los grupos. Devuelve { newGames, updatedGames, players, errors[] }. */
  P.syncAll = async () => {
    if (running) return running;
    running = (async () => {
      const tot = { newGames: 0, updatedGames: 0, players: 0, uploaded: 0, errors: [] };
      for (const g of (st.groups || []).slice()) {
        try {
          const r = await P.syncGroup(g);
          tot.newGames += r.newGames; tot.updatedGames += r.updatedGames; tot.players += r.players; tot.uploaded += r.uploaded;
        } catch (e) { tot.errors.push({ group: g, error: e }); }
      }
      return tot;
    })();
    try { return await running; } finally { running = null; }
  };

  P.syncGroup = async (g) => {
    const res = { newGames: 0, updatedGames: 0, players: 0, uploaded: 0 };
    // 1) Bajar jugadores nuevos o cambiados
    const pd = await query(g, 'players', g.lastPlayers);
    for (const d of pd) {
      const f = fields(d);
      let rp;
      try { rp = JSON.parse(f.json || '{}'); } catch (e) { continue; }
      if (!rp.id) continue;
      g.roster[rp.id] = true;
      if (!g.lastPlayers || f.updatedAt > g.lastPlayers) g.lastPlayers = f.updatedAt;
      const lid = localId(g, rp.id);
      const lp = P.player(lid);
      if (!lp) { st.players.push({ id: lid, name: uniqueName(rp.name), color: rp.color, created: rp.created || Date.now() }); res.players++; }
      else if (lid === rp.id && (rp.updated || 0) > (lp.updated || 0)) { lp.name = rp.name; lp.color = rp.color; lp.updated = rp.updated; }
    }
    // 2) Bajar partidas nuevas o cambiadas
    const gd = await query(g, 'games', g.lastGames);
    const people = {};
    for (const p of st.players) people[p.id] = p;
    for (const d of gd) {
      const f = fields(d);
      if (!g.lastGames || f.updatedAt > g.lastGames) g.lastGames = f.updatedAt;
      const id = docId(d);
      if (g.pending.indexOf(id) >= 0) continue;               // tengo cambios propios sin subir: ganan los míos
      const i = st.games.findIndex((x) => x.id === id);
      if (i >= 0 && f.by && f.by === g.uid && !f.deleted) continue;   // es mi propia subida: ya la tengo
      if (f.deleted) { if (i >= 0 && st.games[i].status !== E.ACTIVE) { st.games.splice(i, 1); res.updatedGames++; } continue; }
      let game;
      try { game = E.remapGame(E.normGame(JSON.parse(f.json)), g.ids || {}, people); } catch (e) { continue; }
      game.group = g.id;
      if (i < 0) { st.games.push(game); res.newGames++; }
      else if (st.games[i].status !== E.ACTIVE) { st.games[i] = game; res.updatedGames++; }
    }
    // 3) Subir jugadores, partidas pendientes y borrados
    const inv = inverse(g);
    const writes = [];
    const playersToPush = new Set(g.dirtyPlayers || []);
    for (const id of g.pending) {
      const game = st.games.find((x) => x.id === id);
      if (game) for (const pid of game.order) if (!g.roster[inv[pid] || pid]) playersToPush.add(pid);
    }
    for (const pid of playersToPush) {
      const p = P.player(pid);
      if (!p) continue;
      const rid = inv[pid] || pid;
      writes.push(upd(docPath(g, 'players/' + rid), { json: strF(JSON.stringify({ id: rid, name: p.name, color: p.color, created: p.created || 0, updated: p.updated || 0 })) }));
      g.roster[rid] = true;
    }
    for (const id of g.pending) {
      const game = st.games.find((x) => x.id === id);
      if (!game || game.status === E.ACTIVE) continue;
      const out = E.clean(E.remapGame(game, inv, {}));
      delete out.group;
      // El nombre que ven los demás es el del jugador en el grupo
      writes.push(upd(docPath(g, 'games/' + id), { json: strF(JSON.stringify(out)), by: strF(g.uid || ''), deleted: { booleanValue: false } }));
      res.uploaded++;
    }
    for (const id of g.deleted || []) writes.push(upd(docPath(g, 'games/' + id), { json: strF(''), by: strF(g.uid || ''), deleted: { booleanValue: true } }));
    if (writes.length) await commit(g, writes);
    g.pending = [];
    g.deleted = [];
    g.dirtyPlayers = [];
    g.lastSync = Date.now();
    P.save();
    return res;
  };

  function uniqueName(name) {
    let n = name, k = 2;
    while (P.nameTaken(n)) n = name + ' ' + k++;
    return n;
  }

  /** Sincroniza en segundo plano y avisa solo si hay novedades o un error que merece la pena. */
  P.syncQuiet = async (announce) => {
    if (!(st.groups || []).length) return;
    const r = await P.syncAll();
    const news = r.newGames + r.updatedGames;
    if (news || r.players) { P.refresh(); P.toast(news ? 'Grupo: ' + P.plural(news, 'partida nueva', 'partidas nuevas') : 'Grupo actualizado'); }
    else if (announce && r.uploaded) P.toast('Partida subida al grupo');
    const err = r.errors.find((x) => !x.error.offline);
    if (err && announce) P.toast('No se pudo sincronizar «' + err.group.name + '»');
  };
})();
