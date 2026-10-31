// Servidor que imita Firebase (Auth anónima + Firestore por REST) con las mismas reglas que docs/firestore.rules.
// Solo para pruebas: node tests/mock-firebase.js 8790
'use strict';
const http = require('http');
const PORT = +process.argv[2] || 8790;
const API_KEY = 'AIzaTEST-poch';
const docs = new Map();            // ruta relativa (groups/...) → { fields, updateTime }
const refresh = new Map();         // refreshToken → uid
let clock = Date.now() * 1000;     // microsegundos, siempre creciente
const now = () => { clock = Math.max(clock + 1, Date.now() * 1000); return new Date(Math.floor(clock / 1000)).toISOString().replace('Z', String(clock % 1000).padStart(3, '0') + 'Z'); };
const rid = () => Math.random().toString(36).slice(2, 12) + Math.random().toString(36).slice(2, 12);

function send(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' });
  res.end(body == null ? '' : JSON.stringify(body));
}
const deny = (res) => send(res, 403, { error: { code: 403, message: 'Missing or insufficient permissions.', status: 'PERMISSION_DENIED' } });
const val = (f) => f == null ? undefined : ('stringValue' in f ? f.stringValue : 'booleanValue' in f ? f.booleanValue : 'timestampValue' in f ? f.timestampValue : undefined);

// ---- reglas (las de docs/firestore.rules) ----
function parse(path) {           // groups/{g}[/{col}/{doc}]
  const p = path.split('/');
  if (p[0] !== 'groups' || !p[1]) return null;
  return { g: p[1], col: p[2], doc: p[3] };
}
const getData = (path) => { const d = docs.get(path); if (!d) return null; const o = {}; for (const k in d.fields) o[k] = val(d.fields[k]); return o; };
const isMember = (uid, g) => !!uid && docs.has('groups/' + g + '/members/' + uid);
function allowed(op, path, uid, newFields) {
  if (!uid) return false;
  const p = parse(path);
  if (!p) return false;
  const group = getData('groups/' + p.g);
  const nd = {}; for (const k in (newFields || {})) nd[k] = val(newFields[k]);
  if (!p.col) {
    if (op === 'read') return isMember(uid, p.g);
    if (op === 'create') return nd.owner === uid;
    return !!group && group.owner === uid;                 // update/delete
  }
  if (p.col === 'members') {
    if (op === 'read' || op === 'list') return isMember(uid, p.g);
    if (op === 'create' || op === 'update') return p.doc === uid && !!group && nd.key === group.key;
    if (op === 'delete') return p.doc === uid || (!!group && group.owner === uid);
  }
  if (p.col === 'players' || p.col === 'games') return isMember(uid, p.g);
  return false;
}

function body(req) {
  return new Promise((ok) => { let s = ''; req.on('data', (c) => { s += c; }); req.on('end', () => { try { ok(s ? JSON.parse(s) : {}); } catch (e) { ok({}); } }); });
}

http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204);
  const url = new URL(req.url, 'http://x');
  const b = await body(req);
  const uidFromAuth = () => { const a = req.headers.authorization || ''; const m = a.match(/^Bearer tok\.(.+)$/); return m ? m[1] : null; };
  // ---- Auth ----
  if (url.pathname === '/auth/accounts:signUp') {
    if (url.searchParams.get('key') !== API_KEY) return send(res, 400, { error: { code: 400, message: 'API key not valid. Please pass a valid API key.' } });
    const uid = rid(), rt = 'ref.' + rid();
    refresh.set(rt, uid);
    return send(res, 200, { localId: uid, idToken: 'tok.' + uid, refreshToken: rt, expiresIn: '3600' });
  }
  if (url.pathname === '/token/token') {
    const uid = refresh.get(b.refresh_token);
    if (!uid) return send(res, 400, { error: { message: 'INVALID_REFRESH_TOKEN' } });
    return send(res, 200, { user_id: uid, id_token: 'tok.' + uid, refresh_token: b.refresh_token, expires_in: '3600' });
  }
  // ---- Firestore ----
  const m = url.pathname.match(/^\/fs\/projects\/([^/]+)\/databases\/\(default\)\/documents(.*)$/);
  if (!m) return send(res, 404, { error: { message: 'NOT_FOUND' } });
  const rest = decodeURIComponent(m[2]);
  const uid = uidFromAuth();
  const base = 'projects/' + m[1] + '/databases/(default)/documents/';
  const rel = (name) => name.slice(name.indexOf('/documents/') + 11);
  const out = (path) => { const d = docs.get(path); return { name: base + path, fields: d.fields, updateTime: d.updateTime }; };

  if (rest === ':commit' && req.method === 'POST') {
    const writes = b.writes || [];
    for (const w of writes) {
      const path = rel(w.update.name);
      const exists = docs.has(path);
      if (w.currentDocument && w.currentDocument.exists === false && exists) return send(res, 409, { error: { status: 'ALREADY_EXISTS', message: 'Document already exists' } });
      const nf = w.updateMask ? Object.assign({}, exists ? docs.get(path).fields : {}, w.update.fields) : w.update.fields;
      if (!allowed(exists ? 'update' : 'create', path, uid, nf)) return deny(res);
    }
    for (const w of writes) {
      const path = rel(w.update.name);
      const t = now();
      const fields = Object.assign({}, w.updateMask && docs.has(path) ? docs.get(path).fields : {}, w.update.fields);
      for (const tr of w.updateTransforms || []) if (tr.setToServerValue === 'REQUEST_TIME') fields[tr.fieldPath] = { timestampValue: t };
      docs.set(path, { fields, updateTime: t });
    }
    return send(res, 200, { writeResults: writes.map(() => ({})) });
  }
  const qm = rest.match(/^\/(groups\/[^/:]+):runQuery$/);
  if (qm && req.method === 'POST') {
    const q = b.structuredQuery;
    const col = q.from[0].collectionId;
    const parent = qm[1];
    if (!allowed('read', parent + '/' + col + '/x', uid)) return deny(res);
    const since = q.where ? q.where.fieldFilter.value.timestampValue : null;
    const list = [...docs.keys()].filter((k) => k.startsWith(parent + '/' + col + '/') && k.split('/').length === 4)
      .map((k) => ({ k, t: val(docs.get(k).fields.updatedAt) || '' }))
      .filter((x) => !since || x.t > since).sort((a, b2) => (a.t < b2.t ? -1 : 1));
    if (!list.length) return send(res, 200, [{ readTime: now() }]);
    return send(res, 200, list.map((x) => ({ document: out(x.k), readTime: now() })));
  }
  const path = rest.replace(/^\//, '');
  if (req.method === 'GET') {
    if (path.split('/').length % 2 === 1) {                // colección
      if (!allowed('list', path + '/x', uid)) return deny(res);
      const ds = [...docs.keys()].filter((k) => k.startsWith(path + '/') && k.split('/').length === path.split('/').length + 1).map(out);
      return send(res, 200, ds.length ? { documents: ds } : {});
    }
    if (!allowed('read', path, uid)) return deny(res);
    if (!docs.has(path)) return send(res, 404, { error: { status: 'NOT_FOUND', message: 'Document not found' } });
    return send(res, 200, out(path));
  }
  if (req.method === 'PATCH') {
    const d = docs.get(path);
    const merged = Object.assign({}, d ? d.fields : {});
    const mask = url.searchParams.getAll('updateMask.fieldPaths');
    for (const k of (mask.length ? mask : Object.keys(b.fields || {}))) merged[k] = b.fields[k];
    if (!allowed(d ? 'update' : 'create', path, uid, merged)) return deny(res);
    docs.set(path, { fields: merged, updateTime: now() });
    return send(res, 200, out(path));
  }
  if (req.method === 'DELETE') {
    if (!allowed('delete', path, uid)) return deny(res);
    docs.delete(path);
    return send(res, 200, {});
  }
  send(res, 404, { error: { message: 'NOT_FOUND' } });
}).listen(PORT, () => console.log('mock-firebase en ' + PORT));
