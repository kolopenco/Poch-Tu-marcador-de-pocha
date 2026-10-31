// Pruebas de la lógica (las mismas que la app Android). Ejecutar: node web/test/engine.test.js
'use strict';
const E = require('../web/js/engine.js');
const assert = require('assert');

let ok = 0, bad = 0;
function test(name, fn) {
  try { fn(); ok++; } catch (e) { bad++; console.log('FALLO ' + name + ': ' + e.message); }
}

function game(n, mode, rules) {
  const order = [], names = {}, colors = {};
  for (let i = 0; i < n; i++) { order.push('p' + i); names['p' + i] = 'J' + i; colors['p' + i] = i; }
  return E.newGame('g', rules || E.defaultRules(), mode, order, names, colors, 1000);
}
const cards = (g) => g.rounds.map((r) => r.cards);
const auctions = (g) => g.rounds.filter((r) => r.auction).length;
function fill(g, rd, values) {
  E.players(g, rd).forEach((p, i) => { E.res(rd, p).v = i < values.length ? values[i] : 0; });
  if (rd.auction) rd.winner = E.players(g, rd)[0];
}
function playAll(n, tie, per) {
  const r = E.defaultRules(); r.tie = tie;
  const g = game(n, E.SIMPLE, r);
  r.forbidSum = false; g.rules.forbidSum = false;
  while (!E.allSaved(g)) {
    const rd = g.rounds[E.current(g)];
    if (rd.tiebreak) {
      const ps = E.players(g, rd);
      E.res(rd, ps[0]).v = 1;
      for (let i = 1; i < ps.length; i++) E.res(rd, ps[i]).v = -1;
    } else fill(g, rd, per);
    E.save(g, rd, 1);
  }
  return g;
}

test('5 jugadores: 17 rondas, 5 subastadas', () => {
  const g = game(5, E.SIMPLE);
  assert.deepStrictEqual(cards(g), [1, 2, 3, 4, 5, 6, 7, 7, 7, 7, 7, 6, 5, 4, 3, 2, 1]);
  assert.strictEqual(auctions(g), 5);
});
test('6 jugadores: 16 rondas', () => {
  const g = game(6, E.SIMPLE);
  assert.deepStrictEqual(cards(g), [1, 2, 3, 4, 5, 6, 6, 6, 6, 6, 6, 5, 4, 3, 2, 1]);
  assert.strictEqual(auctions(g), 6);
});
test('7 jugadores: 15 rondas', () => {
  const g = game(7, E.SIMPLE);
  assert.deepStrictEqual(cards(g), [1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 4, 3, 2, 1]);
  assert.strictEqual(auctions(g), 7);
});
test('botones del modo sencillo', () => {
  const r = E.defaultRules();
  assert.strictEqual(E.stepSimple(undefined, 1, 5), 0);
  assert.strictEqual(E.pointsR(r, E.SIMPLE, null, { v: 0 }), 10);
  assert.strictEqual(E.stepSimple(undefined, -1, 5), -1);
  assert.strictEqual(E.pointsR(r, E.SIMPLE, null, { v: -1 }), -5);
  assert.strictEqual(E.stepFull(undefined, -1, 5), 0);
});
test('tabla de puntos y Oros ×2', () => {
  const r = E.defaultRules();
  for (const [v, n, o] of [[0, 10, 10], [1, 15, 20], [3, 25, 40], [-1, -5, -10], [-2, -10, -20]]) {
    assert.strictEqual(E.pointsR(r, E.SIMPLE, null, { v }), n);
    assert.strictEqual(E.pointsR(r, E.SIMPLE, 'oros', { v }), o);
  }
  assert.strictEqual(E.pointsR(r, E.FULL, 'oros', { b: 3, m: 3 }), 40);
});
test('subasta: un único adjudicatario', () => {
  const g = game(5, E.SIMPLE);
  const rd = g.rounds[7];
  for (const p of g.order) E.res(rd, p).v = 1;
  E.res(rd, 'p4').v = -1;
  assert.ok(!E.canSave(g, rd));
  rd.winner = 'p1';
  assert.ok(E.canSave(g, rd));
});
test('modo completo: hechas y pedidas', () => {
  const g = game(3, E.FULL);
  const rd = g.rounds[2];
  Object.assign(E.res(rd, 'p0'), { b: 1, m: 1 });
  Object.assign(E.res(rd, 'p1'), { b: 1, m: 1 });
  Object.assign(E.res(rd, 'p2'), { b: 1, m: 0 });
  assert.ok(!E.canSave(g, rd));
  rd.res.p2.m = 1;
  assert.ok(!E.canSave(g, rd));
  rd.res.p2.b = 0;
  assert.ok(E.canSave(g, rd));
});
test('modo sencillo: comprobaciones', () => {
  const g = game(4, E.SIMPLE);
  const rd = g.rounds[2];
  fill(g, rd, [1, 1, 1, 0]);
  assert.ok(!E.canSave(g, rd));
  rd.res.p3.v = -1; rd.res.p0.v = 2;
  assert.ok(!E.canSave(g, rd));
  rd.res.p0.v = 1;
  assert.ok(E.canSave(g, rd));
  g.rules.forbidSum = false;
  rd.res.p3.v = 0;
  assert.ok(E.canSave(g, rd));
  rd.res.p0.v = 0;
  assert.ok(!E.canSave(g, rd));
});
test('editar y deshacer', () => {
  const g = game(4, E.SIMPLE);
  g.rules.forbidSum = false;
  for (let i = 0; i < 3; i++) { fill(g, g.rounds[i], [1, 0, -1, 0]); E.save(g, g.rounds[i], 2000 + i); }
  assert.strictEqual(E.total(g, 2, 'p0'), 45);
  const r0 = g.rounds[0];
  const before = E.snap(r0);
  r0.res.p0.v = -1;
  assert.ok(E.commitEdit(g, r0, before, 5000));
  assert.strictEqual(E.total(g, 2, 'p0'), 25);
  assert.ok(E.edited(r0));
  assert.strictEqual(E.editedPlayers(g, r0).size, 1);
  assert.ok(E.undo(g, r0, 6000));
  assert.strictEqual(E.total(g, 2, 'p0'), 45);
  assert.ok(!E.canUndo(r0));
});
test('empate compartido y desempate', () => {
  const g = playAll(3, E.TIE_SHARED, [1, 1, -1]);
  g.status = E.COMPLETED;
  assert.strictEqual(E.winners(g).length, 2);
  assert.strictEqual(E.stats([g], 'p1').wins, 0.5);
  const h = playAll(3, E.TIE_BREAK, [1, 1, -1]);
  const last = h.rounds[h.rounds.length - 1];
  assert.ok(last.tiebreak);
  assert.deepStrictEqual(E.winners(h), ['p0']);
});
test('anticipada: estadísticas sin victoria', () => {
  const g = game(3, E.SIMPLE);
  g.rules.forbidSum = false;
  for (let i = 0; i < 4; i++) { fill(g, g.rounds[i], [1, 0, -1]); E.save(g, g.rounds[i], 1); }
  g.status = E.EARLY;
  const s = E.stats([g], 'p0');
  assert.strictEqual(s.games, 1);
  assert.strictEqual(s.wins, 0);
  assert.strictEqual(s.rounds, 4);
});
test('partidas jugadas juntos', () => {
  const a = playAll(3, E.TIE_SHARED, [1, 0, -1]); a.status = E.COMPLETED; a.id = 'a';
  const b = playAll(4, E.TIE_SHARED, [-1, 1, 0, -1]); b.status = E.COMPLETED; b.id = 'b';
  const c = playAll(2, E.TIE_SHARED, [1, -1]); c.status = E.COMPLETED; c.id = 'c';
  assert.strictEqual(E.withAll([a, b, c], ['p0', 'p1']).length, 3);
  assert.strictEqual(E.withAll([a, b, c], ['p0', 'p1', 'p2']).length, 2);
  assert.strictEqual(E.withAll([a, b, c], ['p0', 'p3']).length, 1);
});
test('copia de seguridad compatible con Android (sin null)', () => {
  const g = game(5, E.SIMPLE);
  g.rules.forbidSum = false;
  fill(g, g.rounds[0], [1, 0, -1, 0, 0]);
  E.save(g, g.rounds[0], 3);
  g.rounds[1].mult = null;
  const txt = JSON.stringify(E.buildBackup({ players: [{ id: 'p0', name: 'A', color: -1, created: 0 }], profiles: [E.defaultRules()], defaultProfile: 'clasica', games: [g], activeGame: 'g' }, 'web', 9));
  assert.ok(txt.indexOf('null') < 0);
  const d = E.parseBackup(txt);
  assert.strictEqual(d.games.length, 1);
  assert.strictEqual(E.totalAll(d.games[0], 'p0'), E.totalAll(g, 'p0'));
  assert.throws(() => E.parseBackup('{"app":"Otra"}'));
});
test('lee una copia hecha en Android', () => {
  // Fragmento con el formato exacto que escribe Json.java
  const txt = '{"app":"Poch","schema":1,"version":"Beta 1.0.1","exported":1,"players":[{"id":"j1","name":"Berto","color":-14770604,"created":1},{"id":"j2","name":"Elías","color":-1879268,"created":2}],"profiles":[{"schema":1,"id":"clasica","name":"Pocha clásica","deck":40,"maxCards":7,"seqType":"standard","customSeq":"","repeatMax":0,"auctionMax":true,"base":10,"perTrick":5,"penalty":5,"multBase":false,"multOn":[true,false,false,false],"multF":[2,2,2,2],"forbidSum":true,"tie":"shared"}],"defaultProfile":"clasica","games":[{"schema":1,"id":"g1","status":"active","created":5,"mode":"simple","order":["j1","j2"],"names":{"j1":"Berto","j2":"Elías"},"colors":{"j1":-14770604,"j2":-1879268},"rules":{"schema":1,"id":"clasica","name":"Pocha clásica","deck":40,"maxCards":7,"seqType":"standard","customSeq":"","repeatMax":0,"auctionMax":true,"base":10,"perTrick":5,"penalty":5,"multBase":false,"multOn":[true,false,false,false],"multF":[2,2,2,2],"forbidSum":true,"tie":"shared"},"rounds":[{"cards":1,"auction":false,"mult":"oros","res":{"j1":{"v":1},"j2":{"v":-1}},"saved":true,"savedAt":6},{"cards":2,"auction":false,"res":{},"saved":false}]}],"activeGame":"g1","statsSel":[]}';
  const d = E.parseBackup(txt);
  assert.strictEqual(d.activeGame, 'g1');
  assert.strictEqual(E.totalAll(d.games[0], 'j1'), 20);
  assert.strictEqual(E.totalAll(d.games[0], 'j2'), -10);
});

test('mezclar copias', () => {
  const pl = (id, name) => ({ id, name, color: 1, created: 0 });
  const mine = { players: [pl('m1', 'Mich'), pl('m2', 'Berto')], profiles: [E.defaultRules()], defaultProfile: 'clasica', games: [], activeGame: null };
  const r = E.defaultRules(); r.forbidSum = false;
  const names = { x1: 'Michael', x2: 'berto ', x3: 'Ana' };
  const g = E.newGame('g1', r, E.SIMPLE, ['x1', 'x2', 'x3'], names, { x1: 2, x2: 2, x3: 2 }, 10);
  while (!E.allSaved(g)) { const rd = g.rounds[E.current(g)]; fill(g, rd, [1, 0, -1]); E.save(g, rd, 1); }
  g.status = E.COMPLETED;
  const live = E.newGame('g2', r, E.SIMPLE, ['x1', 'x2', 'x3'], names, {}, 20);
  const inc = { players: [pl('x1', 'Michael'), pl('x2', 'berto '), pl('x3', 'Ana')], profiles: [E.defaultRules()], games: [g, live] };
  const map = E.suggest(mine, inc);
  assert.strictEqual(map.x1, null);
  assert.strictEqual(map.x2, 'm2');
  map.x1 = 'm1';
  const res = E.merge(mine, inc, map);
  assert.strictEqual(res.newPlayers, 1);
  assert.strictEqual(res.newGames, 1);
  assert.strictEqual(res.skippedActive, 1);
  assert.deepStrictEqual(res.data.games[0].order, ['m1', 'm2', 'x3']);
  assert.strictEqual(E.totalAll(res.data.games[0], 'm1'), E.totalAll(g, 'x1'));
  const again = E.merge(res.data, inc, E.suggest(res.data, inc));
  assert.strictEqual(again.newPlayers, 0);
  assert.strictEqual(again.sameGames, 1);
  map.x3 = 'm1';
  assert.throws(() => E.merge(mine, inc, map));
});

test('grupos en la copia (formato Android)', () => {
  const txt = JSON.stringify({ app: 'Poch', schema: 1, version: 'Beta 1.2.1', exported: 1, players: [{ id: 'a', name: 'Ana', color: 1, created: 0 }],
    profiles: [], defaultProfile: '', games: [], statsSel: [],
    groups: [{ id: 'G1', name: 'Pirolas', apiKey: 'k', projectId: 'p', key: 's', uid: 'u1', refresh: 'r1', owner: true, me: 'a',
      ids: { remB: 'a' }, roster: ['remB', 'x'], pending: [], deleted: [], dirtyPlayers: [], lastSync: 5 }] });
  const d = E.parseBackup(txt);
  assert.strictEqual(d.groups.length, 1);
  const g = d.groups[0];
  assert.ok(g.owner && g.uid === 'u1' && g.refresh === 'r1' && g.roster.x === true && g.ids.remB === 'a');
  const out = E.buildBackup(d, 'x', 1);
  assert.deepStrictEqual(out.groups[0].roster, ['remB', 'x']);
  assert.strictEqual(E.parseBackup(JSON.stringify(E.buildBackup({ players: [], profiles: [], games: [] }, 'x', 1))).groups, null);
  const r = E.merge({ players: [{ id: 'm', name: 'Ana', color: 1 }], profiles: [], defaultProfile: '', games: [], statsSel: [], groups: [] }, d, { a: 'm' });
  assert.strictEqual(r.newGroups, 1);
  assert.strictEqual(r.data.groups[0].me, 'm');
  assert.strictEqual(r.data.groups[0].ids.remB, 'm');
  assert.strictEqual(r.data.groups[0].lastGames, null);
  assert.strictEqual(E.merge(r.data, d, { a: 'm' }).newGroups, 0);
});

console.log(ok + ' ok, ' + bad + ' fallos');
if (bad) process.exit(1);
