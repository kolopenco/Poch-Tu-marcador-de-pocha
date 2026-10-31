/*
 * Poch! · lógica de La Pocha (rondas, puntos, validaciones, clasificación y estadísticas).
 * Es una traducción directa de Engine.java de la app Android. Los datos usan exactamente
 * el mismo formato JSON, así que las copias de seguridad sirven en los dos sentidos.
 */
(function (root) {
  'use strict';

  const SIMPLE = 'simple', FULL = 'full';
  const ACTIVE = 'active', COMPLETED = 'completed', EARLY = 'early';
  const TIE_SHARED = 'shared', TIE_BREAK = 'tiebreak';
  const SUITS = ['oros', 'copas', 'espadas', 'bastos'];
  const SUIT_NAMES = ['Oros', 'Copas', 'Espadas', 'Bastos'];
  const MIN_PLAYERS = 2, MAX_PLAYERS = 10;

  const isNum = (v) => typeof v === 'number' && isFinite(v);

  function suitName(s) {
    const i = SUITS.indexOf(s);
    return i < 0 ? s : SUIT_NAMES[i];
  }

  function defaultRules() {
    return {
      schema: 1, id: 'clasica', name: 'Pocha clásica', deck: 40, maxCards: 7,
      seqType: 'standard', customSeq: '', repeatMax: 0, auctionMax: true,
      base: 10, perTrick: 5, penalty: 5, multBase: false,
      multOn: [true, false, false, false], multF: [2, 2, 2, 2],
      forbidSum: true, tie: TIE_SHARED,
    };
  }

  /** Completa unas reglas leídas (de una copia antigua, por ejemplo) con los valores por defecto. */
  function normRules(r) {
    const d = defaultRules();
    const o = Object.assign({}, d, r || {});
    o.multOn = d.multOn.map((v, i) => (r && Array.isArray(r.multOn) && typeof r.multOn[i] === 'boolean') ? r.multOn[i] : v);
    o.multF = d.multF.map((v, i) => (r && Array.isArray(r.multF) && isNum(r.multF[i])) ? r.multF[i] : v);
    return o;
  }

  const copy = (x) => JSON.parse(JSON.stringify(x));

  function factor(r, suit) {
    if (!suit) return 1;
    const i = SUITS.indexOf(suit);
    if (i < 0) return 1;
    return r.multOn[i] ? Math.max(1, r.multF[i]) : 1;
  }

  // ------------------------------------------------------------------ rondas

  function maxCards(r, n) {
    if (n <= 0) return 1;
    return Math.max(1, Math.min(r.maxCards, Math.floor(r.deck / n)));
  }

  function sequence(r, n) {
    const out = [];
    const cap = Math.max(1, Math.floor(r.deck / Math.max(1, n)));
    if (r.seqType === 'custom') {
      for (const t of String(r.customSeq || '').split(/[^0-9]+/)) {
        if (!t) continue;
        const c = parseInt(t, 10);
        if (c >= 1) out.push(Math.min(c, cap));
      }
      if (out.length) return out;
    }
    const m = maxCards(r, n);
    const rep = r.repeatMax > 0 ? r.repeatMax : n;
    for (let c = 1; c < m; c++) out.push(c);
    for (let i = 0; i < rep; i++) out.push(m);
    for (let c = m - 1; c >= 1; c--) out.push(c);
    return out;
  }

  function newGame(id, rules, mode, order, names, colors, now) {
    const g = {
      schema: 1, id, status: ACTIVE, created: now, mode: mode === FULL ? FULL : SIMPLE,
      order: order.slice(), names: {}, colors: {}, rules: copy(rules), rounds: [],
    };
    for (const p of order) {
      g.names[p] = names[p];
      g.colors[p] = colors[p] == null ? 0xFF607080 | 0 : colors[p];
    }
    const seq = sequence(g.rules, order.length);
    const top = Math.max.apply(null, seq);
    for (const c of seq) g.rounds.push({ cards: c, auction: !!g.rules.auctionMax && c === top, res: {}, saved: false });
    return g;
  }

  function current(g) {
    for (let i = 0; i < g.rounds.length; i++) if (!g.rounds[i].saved) return i;
    return g.rounds.length;
  }
  const regularCount = (g) => g.rounds.filter((r) => !r.tiebreak).length;
  const savedCount = (g) => g.rounds.filter((r) => r.saved).length;
  const savedRegular = (g) => g.rounds.filter((r) => r.saved && !r.tiebreak).length;

  function dealer(g, idx) {
    const n = g.order.length;
    if (!n) return null;
    return g.order[((idx % n) + n) % n];
  }

  const plays = (rd, pid) => !rd.only || rd.only.indexOf(pid) >= 0;
  const players = (g, rd) => g.order.filter((p) => plays(rd, p));

  function res(rd, pid) {
    if (!rd.res) rd.res = {};
    if (!rd.res[pid]) rd.res[pid] = {};
    return rd.res[pid];
  }

  // ------------------------------------------------------------------ botones − / +

  function stepSimple(v, delta, cards) {
    if (!isNum(v)) return delta > 0 ? 0 : -1;
    return Math.max(-cards, Math.min(cards, v + delta));
  }

  function stepFull(v, delta, cards) {
    if (!isNum(v)) return 0;
    return Math.max(0, Math.min(cards, v + delta));
  }

  // ------------------------------------------------------------------ puntos

  function entered(g, rd, pid) {
    if (!plays(rd, pid)) return true;
    const x = rd.res && rd.res[pid];
    if (!x) return false;
    if (g.mode === FULL) return isNum(x.b) && isNum(x.m);
    return isNum(x.v);
  }

  function hit(g, rd, pid) {
    if (!plays(rd, pid)) return null;
    const x = rd.res && rd.res[pid];
    if (!x) return null;
    if (g.mode === FULL) {
      if (!isNum(x.b) || !isNum(x.m)) return null;
      return x.b === x.m;
    }
    if (!isNum(x.v)) return null;
    return x.v >= 0;
  }

  function pointsR(r, mode, mult, x) {
    if (!x) return null;
    const f = factor(r, mult);
    const base = r.base * (r.multBase ? f : 1);
    if (mode === FULL) {
      if (!isNum(x.b) || !isNum(x.m)) return null;
      if (x.b === x.m) return base + x.m * r.perTrick * f;
      return -Math.abs(x.b - x.m) * r.penalty * f;
    }
    if (!isNum(x.v)) return null;
    if (x.v >= 0) return base + x.v * r.perTrick * f;
    return x.v * r.penalty * f;
  }

  function points(g, rd, pid) {
    return pointsR(g.rules, g.mode, rd.mult, plays(rd, pid) ? rd.res && rd.res[pid] : null);
  }

  function total(g, idx, pid) {
    let t = 0;
    for (let i = 0; i <= idx && i < g.rounds.length; i++) {
      const p = points(g, g.rounds[i], pid);
      if (p != null) t += p;
    }
    return t;
  }
  const totalAll = (g, pid) => total(g, g.rounds.length - 1, pid);

  function af(g, idx, pid) {
    let a = 0, f = 0;
    for (let i = 0; i <= idx && i < g.rounds.length; i++) {
      const h = hit(g, g.rounds[i], pid);
      if (h == null) continue;
      if (h) a++; else f++;
    }
    return [a, f];
  }

  // ------------------------------------------------------------------ validación

  function problems(g, rd) {
    const out = [];
    const ps = players(g, rd);
    let missing = 0;
    for (const p of ps) if (!entered(g, rd, p)) missing++;
    if (missing > 0) out.push(missing === 1 ? 'Falta 1 resultado' : 'Faltan ' + missing + ' resultados');
    if (g.mode === FULL) {
      let made = 0, bids = 0, allMade = true, allBids = true;
      for (const p of ps) {
        const x = rd.res && rd.res[p];
        if (!x || !isNum(x.m)) allMade = false; else made += x.m;
        if (!x || !isNum(x.b)) allBids = false; else bids += x.b;
      }
      if (allMade && made !== rd.cards)
        out.push('Las hechas suman ' + made + ' y hay ' + rd.cards + (rd.cards === 1 ? ' baza' : ' bazas'));
      if (allBids && g.rules.forbidSum && bids === rd.cards && ps.length > 1)
        out.push('Las pedidas no pueden sumar ' + rd.cards);
    }
    if (g.mode !== FULL && missing === 0 && ps.length) {
      // Modo sencillo: quien acierta hizo exactamente las bazas anotadas.
      let hitTricks = 0, misses = 0;
      for (const p of ps) {
        const v = rd.res[p].v;
        if (v >= 0) hitTricks += v; else misses++;
      }
      if (hitTricks > rd.cards)
        out.push('Los aciertos suman ' + hitTricks + ' bazas y solo hay ' + rd.cards);
      else if (misses === 0 && g.rules.forbidSum && ps.length > 1)
        out.push('Alguien tiene que fallar: las pedidas no pueden sumar ' + rd.cards);
      else if (misses === 0 && hitTricks !== rd.cards)
        out.push('Si aciertan todos, las bazas deben sumar ' + rd.cards + ' (ahora ' + hitTricks + ')');
    }
    if (rd.auction && (!rd.winner || ps.indexOf(rd.winner) < 0))
      out.push('Marca quién se llevó la subasta');
    return out;
  }

  const canSave = (g, rd) => problems(g, rd).length === 0;

  function save(g, rd, now, autoTiebreak) {
    rd.saved = true;
    rd.savedAt = now;
    if (!rd.auction) delete rd.winner;
    if (autoTiebreak !== false) ensureTiebreak(g);
  }

  // ------------------------------------------------------------------ clasificación

  function standings(g) {
    const out = g.order.map((p) => ({ pid: p, total: totalAll(g, p), pos: 0 }));
    out.sort((a, b) => b.total - a.total);
    out.forEach((r, i) => { r.pos = (i > 0 && out[i - 1].total === r.total) ? out[i - 1].pos : i + 1; });
    return out;
  }

  const leaders = (g) => standings(g).filter((r) => r.pos === 1).map((r) => r.pid);
  const allSaved = (g) => current(g) >= g.rounds.length;
  const needsTiebreak = (g) => allSaved(g) && g.rules.tie === TIE_BREAK && leaders(g).length > 1;
  const tieAtEnd = (g) => allSaved(g) && leaders(g).length > 1;

  function ensureTiebreak(g) {
    if (!needsTiebreak(g)) return false;
    g.rounds.push({ cards: 1, auction: false, tiebreak: true, only: leaders(g).slice(), res: {}, saved: false });
    return true;
  }

  function setTieRule(g, tie) {
    g.rules.tie = tie;
    if (tie === TIE_BREAK) {
      ensureTiebreak(g);
    } else {
      for (let i = g.rounds.length - 1; i >= 0; i--) {
        const r = g.rounds[i];
        if (r.tiebreak && !r.saved) g.rounds.splice(i, 1); else break;
      }
    }
  }

  const finished = (g) => allSaved(g) && !needsTiebreak(g);

  function winners(g) {
    if (g.status === EARLY) return [];
    if (!allSaved(g)) return [];
    return leaders(g);
  }

  function winShare(g, pid) {
    const w = winners(g);
    return w.indexOf(pid) < 0 ? 0 : 1 / w.length;
  }

  // ------------------------------------------------------------------ edición

  function snap(rd) {
    return { mult: rd.mult || null, winner: rd.winner || null, res: copy(rd.res || {}) };
  }

  function applySnap(rd, s) {
    if (s.mult) rd.mult = s.mult; else delete rd.mult;
    if (s.winner) rd.winner = s.winner; else delete rd.winner;
    rd.res = copy(s.res || {});
  }

  function sameRes(a, b) {
    const n = (x) => x == null ? null : x;
    a = a || {}; b = b || {};
    return n(a.v) === n(b.v) && n(a.b) === n(b.b) && n(a.m) === n(b.m);
  }

  function sameSnap(a, b) {
    if ((a.mult || null) !== (b.mult || null) || (a.winner || null) !== (b.winner || null)) return false;
    const keys = new Set(Object.keys(a.res || {}).concat(Object.keys(b.res || {})));
    for (const k of keys) if (!sameRes(a.res[k], b.res[k])) return false;
    return true;
  }

  function original(rd) {
    for (const rv of rd.revs || []) if (!rv.isUndo) return rv.before;
    return null;
  }

  function commitEdit(g, rd, before, now) {
    const after = snap(rd);
    if (sameSnap(before, after)) return false;
    if (!rd.revs) rd.revs = [];
    rd.revs.push({ at: now, before: copy(before), after });
    if (!rd.auction) delete rd.winner;
    if (g.status === ACTIVE) ensureTiebreak(g);
    return true;
  }

  function canUndo(rd) {
    const revs = rd.revs || [];
    for (let i = revs.length - 1; i >= 0; i--) if (!revs[i].isUndo && !revs[i].undone) return true;
    return false;
  }

  function undo(g, rd, now) {
    const revs = rd.revs || [];
    for (let i = revs.length - 1; i >= 0; i--) {
      const rv = revs[i];
      if (rv.isUndo || rv.undone) continue;
      const cur = snap(rd);
      applySnap(rd, rv.before);
      rv.undone = true;
      revs.push({ at: now, isUndo: true, before: cur, after: copy(rv.before) });
      if (g.status === ACTIVE) ensureTiebreak(g);
      return true;
    }
    return false;
  }

  function edited(rd) {
    const o = original(rd);
    return !!o && !sameSnap(o, snap(rd));
  }

  function editedPlayers(g, rd) {
    const out = new Set();
    const o = original(rd);
    if (!o) return out;
    const multChanged = (o.mult || null) !== (rd.mult || null);
    for (const p of g.order) {
      if (!plays(rd, p)) continue;
      const b = rd.res && rd.res[p];
      const same = sameRes(o.res[p], b);
      const wonChanged = (p === o.winner) !== (p === rd.winner);
      if (!same || wonChanged || (multChanged && b)) out.add(p);
    }
    return out;
  }

  // ------------------------------------------------------------------ estadísticas

  const counts = (g) => g.status === COMPLETED || g.status === EARLY;

  function withAll(games, pids) {
    return games.filter((g) => pids.every((p) => g.order.indexOf(p) >= 0));
  }

  function emptyStats(pid) {
    return {
      pid, games: 0, completed: 0, wins: 0, lasts: 0, posSum: 0, bestScore: null, pointsSum: 0,
      rounds: 0, hits: 0, misses: 0, fullRounds: 0, bidSum: 0, madeSum: 0, fullHits: 0,
      aucPlayed: 0, aucWon: 0, aucWonHit: 0, recent: [],
    };
  }

  const div = (a, b) => b === 0 ? NaN : a / b;
  const derived = {
    posAvg: (s) => div(s.posSum, s.completed),
    pointsAvg: (s) => div(s.pointsSum, s.completed),
    hitPct: (s) => 100 * div(s.hits, s.rounds),
    missPct: (s) => 100 * div(s.misses, s.rounds),
    bidAvg: (s) => div(s.bidSum, s.fullRounds),
    madeAvg: (s) => div(s.madeSum, s.fullRounds),
    fullHitPct: (s) => 100 * div(s.fullHits, s.fullRounds),
    aucPct: (s) => 100 * div(s.aucWon, s.aucPlayed),
    aucHitPct: (s) => 100 * div(s.aucWonHit, s.aucWon),
  };

  function stats(games, pid) {
    const s = emptyStats(pid);
    const mine = games.filter((g) => counts(g) && g.order.indexOf(pid) >= 0);
    mine.sort((a, b) => b.created - a.created);
    for (const g of mine) {
      s.games++;
      for (const rd of g.rounds) {
        if (!rd.saved || !plays(rd, pid)) continue;
        const h = hit(g, rd, pid);
        if (h == null) continue;
        s.rounds++;
        if (h) s.hits++; else s.misses++;
        if (g.mode === FULL) {
          const x = rd.res[pid];
          s.fullRounds++;
          s.bidSum += x.b;
          s.madeSum += x.m;
          if (h) s.fullHits++;
        }
        if (rd.auction) {
          s.aucPlayed++;
          if (pid === rd.winner) {
            s.aucWon++;
            if (h) s.aucWonHit++;
          }
        }
      }
      const rc = { game: g, pos: 0, points: totalAll(g, pid), won: false };
      if (g.status === COMPLETED) {
        s.completed++;
        const st = standings(g);
        let pos = 0, worst = 0;
        for (const r of st) {
          if (r.pid === pid) pos = r.pos;
          worst = Math.max(worst, r.pos);
        }
        s.posSum += pos;
        if (pos === worst && st.length > 1) s.lasts++;
        s.pointsSum += rc.points;
        s.bestScore = s.bestScore == null ? rc.points : Math.max(s.bestScore, rc.points);
        s.wins += winShare(g, pid);
        rc.pos = pos;
        rc.won = winShare(g, pid) > 0;
      }
      s.recent.push(rc);
    }
    return s;
  }

  function statsAll(games, pids) {
    const out = {};
    for (const p of pids) out[p] = stats(games, p);
    return out;
  }

  // ------------------------------------------------------------------ copias

  /** Quita los null/undefined que Android no espera (lee "mult": null como el texto "null"). */
  function clean(x) {
    if (Array.isArray(x)) return x.map(clean);
    if (x && typeof x === 'object') {
      const o = {};
      for (const k of Object.keys(x)) {
        const v = x[k];
        if (v === null || v === undefined) continue;
        o[k] = clean(v);
      }
      return o;
    }
    return x;
  }

  function buildBackup(d, version, now) {
    return clean({
      app: 'Poch', schema: 1, version, exported: now,
      players: d.players, profiles: d.profiles, defaultProfile: d.defaultProfile || '',
      games: d.games, activeGame: d.activeGame || null, statsSel: d.statsSel || [],
    });
  }

  function parseBackup(text) {
    let o;
    try { o = JSON.parse(String(text).trim()); } catch (e) { throw new Error('El archivo no es una copia de Poch! (JSON no válido).'); }
    if (!o || o.app !== 'Poch') throw new Error('El archivo no es una copia de Poch!.');
    if ((o.schema || 0) > 1) throw new Error('La copia es de una versión más nueva de la app. Actualiza Poch! antes de restaurarla.');
    const d = {
      version: o.version || '', exported: o.exported || 0,
      players: (o.players || []).filter((p) => p && p.id).map((p) => ({ id: p.id, name: p.name || '', color: isNum(p.color) ? p.color : (0xFF607080 | 0), created: p.created || 0 })),
      profiles: (o.profiles || []).filter(Boolean).map(normRules),
      defaultProfile: o.defaultProfile || '',
      games: (o.games || []).filter((g) => g && g.id).map(normGame),
      activeGame: o.activeGame || null,
      statsSel: Array.isArray(o.statsSel) ? o.statsSel.slice() : [],
    };
    const ids = new Set();
    for (const p of d.players) { if (ids.has(p.id)) throw new Error('La copia tiene jugadores repetidos.'); ids.add(p.id); }
    const gids = new Set();
    for (const g of d.games) {
      if (gids.has(g.id)) throw new Error('La copia tiene partidas repetidas.');
      gids.add(g.id);
      if (g.order.length < MIN_PLAYERS || !g.rounds.length) throw new Error('Una partida de la copia está incompleta.');
    }
    if (d.activeGame) {
      const a = d.games.find((g) => g.id === d.activeGame);
      if (!a || a.status !== ACTIVE) d.activeGame = null;
    }
    if (!d.profiles.length) d.profiles.push(defaultRules());
    if (!d.profiles.some((r) => r.id === d.defaultProfile)) d.defaultProfile = d.profiles[0].id;
    return d;
  }

  // ------------------------------------------------------------------ mezclar copias

  const norm = (s) => String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();

  /** Emparejamiento propuesto: mismo id, misma posición en una partida que ya tengo, o mismo nombre. */
  function suggest(mine, inc) {
    const map = {}, used = new Set();
    const mineIds = new Set(mine.players.map((p) => p.id));
    for (const p of inc.players) if (mineIds.has(p.id)) { map[p.id] = p.id; used.add(p.id); }
    for (const g of inc.games) {
      const m = mine.games.find((x) => x.id === g.id && x.order.length === g.order.length);
      if (!m) continue;
      g.order.forEach((a, i) => {
        const b = m.order[i];
        if (a in map || used.has(b) || !mineIds.has(b)) return;
        map[a] = b; used.add(b);
      });
    }
    for (const p of inc.players) {
      if (p.id in map) continue;
      const q = mine.players.find((q) => !used.has(q.id) && norm(p.name) && norm(q.name) === norm(p.name));
      map[p.id] = q ? q.id : null;
      if (q) used.add(q.id);
    }
    return map;
  }

  function remapGame(src, ids, people) {
    const g = copy(src);
    const id = (p) => ids[p] || p;
    const order = [], names = {}, colors = {};
    for (const p of g.order) {
      const q = id(p);
      if (order.indexOf(q) >= 0) throw new Error('Dos jugadores de la copia están unidos al mismo jugador tuyo.');
      order.push(q);
      names[q] = people[q] ? people[q].name : g.names[p];
      colors[q] = people[q] ? people[q].color : g.colors[p];
    }
    g.order = order; g.names = names; g.colors = colors;
    const remapRes = (r) => { const n = {}; for (const k of Object.keys(r || {})) n[id(k)] = r[k]; return n; };
    for (const rd of g.rounds) {
      rd.res = remapRes(rd.res);
      if (rd.winner) rd.winner = id(rd.winner);
      if (rd.only) rd.only = rd.only.map(id);
      for (const rv of rd.revs || []) for (const s of [rv.before, rv.after]) {
        if (!s) continue;
        s.res = remapRes(s.res);
        if (s.winner) s.winner = id(s.winner);
      }
    }
    return g;
  }

  /** Mezcla la copia con mis datos (misma regla que Backup.merge de Android). */
  function merge(mine, inc, map) {
    const r = { newPlayers: 0, mergedPlayers: 0, newGames: 0, updatedGames: 0, sameGames: 0, skippedActive: 0, newProfiles: 0 };
    const d = {
      players: mine.players.slice(), profiles: mine.profiles.slice(), defaultProfile: mine.defaultProfile,
      games: mine.games.slice(), activeGame: mine.activeGame, statsSel: (mine.statsSel || []).slice(),
    };
    const people = {};
    for (const p of d.players) people[p.id] = p;
    const ids = {}, targets = new Set();
    for (const p of inc.players) {
      const t = map[p.id];
      if (t && people[t]) {
        if (targets.has(t)) throw new Error('Dos jugadores de la copia están unidos al mismo jugador tuyo.');
        targets.add(t); ids[p.id] = t; r.mergedPlayers++;
      } else if (people[p.id]) {
        ids[p.id] = p.id; r.mergedPlayers++;
      } else {
        const n = { id: p.id, name: p.name, color: p.color, created: p.created || 0 };
        d.players.push(n); people[n.id] = n; ids[p.id] = p.id; r.newPlayers++;
      }
    }
    for (const src of inc.games) {
      if (src.status === ACTIVE) { r.skippedActive++; continue; }
      const g = remapGame(src, ids, people);
      const i = d.games.findIndex((x) => x.id === g.id);
      if (i < 0) { d.games.push(g); r.newGames++; }
      else if (d.games[i].status === ACTIVE || savedCount(g) <= savedCount(d.games[i])) r.sameGames++;
      else { d.games[i] = g; r.updatedGames++; }
    }
    const pids = new Set(d.profiles.map((x) => x.id));
    for (const x of inc.profiles) if (!pids.has(x.id)) { pids.add(x.id); d.profiles.push(copy(x)); r.newProfiles++; }
    r.data = d;
    return r;
  }

  function normGame(g) {
    const o = Object.assign({ status: ACTIVE, mode: SIMPLE, names: {}, colors: {}, rounds: [], order: [] }, g);
    o.rules = normRules(g.rules);
    o.rounds = (g.rounds || []).map((r) => Object.assign({ res: {}, saved: false, auction: false }, r));
    return o;
  }

  const Engine = {
    SIMPLE, FULL, ACTIVE, COMPLETED, EARLY, TIE_SHARED, TIE_BREAK, SUITS, SUIT_NAMES, MIN_PLAYERS, MAX_PLAYERS,
    suitName, defaultRules, normRules, normGame, copy, factor, maxCards, sequence, newGame, current, regularCount,
    savedCount, savedRegular, dealer, plays, players, res, stepSimple, stepFull, entered, hit, pointsR, points,
    total, totalAll, af, problems, canSave, save, standings, leaders, allSaved, needsTiebreak, tieAtEnd,
    ensureTiebreak, setTieRule, finished, winners, winShare, snap, applySnap, sameSnap, original, commitEdit,
    canUndo, undo, edited, editedPlayers, counts, withAll, stats, statsAll, derived, clean, buildBackup, parseBackup,
    norm, suggest, merge, remapGame,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = Engine;
  else root.Engine = Engine;
})(typeof window !== 'undefined' ? window : this);
