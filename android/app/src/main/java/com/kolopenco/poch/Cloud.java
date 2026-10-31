package com.kolopenco.poch;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Group;
import com.kolopenco.poch.Model.Player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grupos de juego sincronizados con Firebase por REST (sin librerías). Mismo protocolo que web/js/sync.js:
 *
 *   groups/{g}                 { name, owner, key, created }
 *   groups/{g}/members/{uid}   { key, player, name, joined }
 *   groups/{g}/players/{id}    { json, updatedAt }
 *   groups/{g}/games/{id}      { json, updatedAt, by, deleted }
 *
 * Java puro (sin Android) para poder probarlo contra un servidor simulado. Las llamadas de red se hacen
 * en el hilo donde se llame; los cambios en los datos pasan por {@link Host#main} (el hilo de la interfaz).
 */
final class Cloud {
    private Cloud() {}

    /** Lo que Cloud necesita de la app. */
    interface Host {
        List<Player> players();
        /** Partidas por id (el orden no importa). */
        Map<String, Game> games();
        List<Group> groups();
        void save();
        /** Ejecuta en el hilo de la interfaz y espera a que termine. */
        void main(Runnable r) throws Exception;
    }

    static final class CloudException extends Exception {
        final int status;
        final boolean offline;

        CloudException(String msg, int status, boolean offline) {
            super(msg);
            this.status = status;
            this.offline = offline;
        }
    }

    static String AUTH = "https://identitytoolkit.googleapis.com/v1";
    static String TOKEN = "https://securetoken.googleapis.com/v1";
    static String FS = "https://firestore.googleapis.com/v1";
    static final String WEB_URL = "https://kolopenco.github.io/Poch-Tu-marcador-de-pocha/";

    private static final Map<Group, String[]> TOKENS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());   // grupo → { idToken, caducidad }
    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHA = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // ------------------------------------------------------------------ utilidades

    static String rnd(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append(ALPHA.charAt(RND.nextInt(ALPHA.length())));
        return s.toString();
    }

    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    static String b64u(String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < b.length; i += 3) {
            int n = (b[i] & 255) << 16 | (i + 1 < b.length ? (b[i + 1] & 255) << 8 : 0) | (i + 2 < b.length ? b[i + 2] & 255 : 0);
            s.append(B64.charAt(n >> 18 & 63)).append(B64.charAt(n >> 12 & 63));
            if (i + 1 < b.length) s.append(B64.charAt(n >> 6 & 63));
            if (i + 2 < b.length) s.append(B64.charAt(n & 63));
        }
        return s.toString();
    }

    static String unb64u(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buf = 0, bits = 0;
        for (char c : s.toCharArray()) {
            int v = B64.indexOf(c == '+' ? '-' : c == '/' ? '_' : c);
            if (v < 0) continue;
            buf = buf << 6 | v;
            bits += 6;
            if (bits >= 8) { bits -= 8; out.write(buf >> bits & 255); }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static Object http(String method, String url, Object body, String token) throws CloudException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String txt = "";
            if (is != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                is.close();
                txt = new String(bo.toByteArray(), StandardCharsets.UTF_8).trim();
            }
            Object data = null;
            if (!txt.isEmpty()) data = txt.startsWith("[") ? new JSONArray(txt) : txt.startsWith("{") ? new JSONObject(txt) : txt;
            if (code >= 400) {
                String msg = "HTTP " + code;
                if (data instanceof JSONObject) {
                    JSONObject err = ((JSONObject) data).optJSONObject("error");
                    if (err != null) msg = err.optString("message", err.optString("status", msg));
                }
                throw new CloudException(msg, code, false);
            }
            return data;
        } catch (CloudException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new CloudException("Sin conexión", 0, true);
        } catch (Exception e) {
            throw new CloudException(String.valueOf(e.getMessage()), 0, false);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // ------------------------------------------------------------------ configuración e invitaciones

    /** apiKey y projectId de lo que pega el usuario (el bloque firebaseConfig o un JSON). Null si no están. */
    static String[] parseFirebaseConfig(String text) {
        if (text == null) return null;
        Matcher a = Pattern.compile("apiKey[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']").matcher(text);
        Matcher p = Pattern.compile("projectId[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']").matcher(text);
        if (!a.find() || !p.find()) return null;
        return new String[]{a.group(1).trim(), p.group(1).trim()};
    }

    static String inviteCode(Group g) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1); o.put("p", g.projectId); o.put("k", g.apiKey); o.put("g", g.id); o.put("s", g.key); o.put("n", g.name);
            return b64u(o.toString());
        } catch (Exception e) { return ""; }
    }

    static String inviteLink(Group g) { return WEB_URL + "#unirse=" + inviteCode(g); }

    static String inviteText(Group g) {
        return "Únete a mi grupo de Poch! «" + g.name + "»:\n" + inviteLink(g)
                + "\n\niPhone: abre el enlace en Safari.\nAndroid: copia este mensaje y en Poch! ve a Ajustes › Grupos de juego › Unirse.";
    }

    /** Acepta el enlace, el mensaje entero o solo el código. Devuelve un grupo sin unir, o null. */
    static Group parseInvite(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = Pattern.compile("unirse=([A-Za-z0-9_-]+)").matcher(t);
        String code = m.find() ? m.group(1) : t.matches("[A-Za-z0-9_-]{40,}") ? t : null;
        if (code == null) return null;
        try {
            JSONObject o = new JSONObject(unb64u(code));
            if (o.optInt("v") != 1 || o.optString("p").isEmpty() || o.optString("k").isEmpty()
                    || o.optString("g").isEmpty() || o.optString("s").isEmpty()) return null;
            Group g = new Group();
            g.projectId = o.optString("p"); g.apiKey = o.optString("k"); g.id = o.optString("g");
            g.key = o.optString("s"); g.name = o.optString("n", "Grupo");
            return g;
        } catch (Exception e) { return null; }
    }

    // ------------------------------------------------------------------ Firestore

    private static String docPath(Group g, String rest) {
        return "projects/" + g.projectId + "/databases/(default)/documents/groups/" + g.id + (rest == null ? "" : "/" + rest);
    }

    private static String commitUrl(Group g) {
        return FS + "/projects/" + g.projectId + "/databases/(default)/documents:commit";
    }

    private static JSONObject strF(String v) throws Exception { return new JSONObject().put("stringValue", v); }

    /** Valores de un documento de Firestore como texto (o "true"/"false"). */
    static Map<String, String> fields(JSONObject doc) {
        Map<String, String> o = new HashMap<>();
        JSONObject f = doc.optJSONObject("fields");
        if (f == null) return o;
        java.util.Iterator<String> it = f.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject v = f.optJSONObject(k);
            if (v == null) continue;
            if (v.has("stringValue")) o.put(k, v.optString("stringValue"));
            else if (v.has("booleanValue")) o.put(k, String.valueOf(v.optBoolean("booleanValue")));
            else if (v.has("timestampValue")) o.put(k, normTs(v.optString("timestampValue")));
            else if (v.has("integerValue")) o.put(k, v.optString("integerValue"));
        }
        return o;
    }

    /** Marca de tiempo con 9 decimales siempre, para poder compararlas como texto. */
    static String normTs(String t) {
        Matcher m = Pattern.compile("^(.*?)(\\.(\\d+))?Z$").matcher(t);
        if (!m.find()) return t;
        String f = m.group(3) == null ? "" : m.group(3);
        return m.group(1) + "." + (f + "000000000").substring(0, 9) + "Z";
    }

    static String docId(JSONObject doc) {
        String n = doc.optString("name");
        return n.substring(n.lastIndexOf('/') + 1);
    }

    private static void signUp(Group g) throws Exception {
        JSONObject r = (JSONObject) http("POST", AUTH + "/accounts:signUp?key=" + enc(g.apiKey), new JSONObject().put("returnSecureToken", true), null);
        g.uid = r.optString("localId");
        g.refresh = r.optString("refreshToken");
        TOKENS.put(g, new String[]{r.optString("idToken"), String.valueOf(System.currentTimeMillis() + (r.optLong("expiresIn", 3600) - 120) * 1000)});
    }

    private static String token(Host h, Group g) throws Exception {
        String[] t = TOKENS.get(g);
        if (t != null && Long.parseLong(t[1]) > System.currentTimeMillis()) return t[0];
        if (g.refresh != null) {
            try {
                JSONObject r = (JSONObject) http("POST", TOKEN + "/token?key=" + enc(g.apiKey),
                        new JSONObject().put("grant_type", "refresh_token").put("refresh_token", g.refresh), null);
                g.refresh = r.optString("refresh_token", g.refresh);
                g.uid = r.optString("user_id", g.uid);
                TOKENS.put(g, new String[]{r.optString("id_token"), String.valueOf(System.currentTimeMillis() + (r.optLong("expires_in", 3600) - 120) * 1000)});
                return r.optString("id_token");
            } catch (CloudException e) {
                if (e.offline) throw e;
                // identidad perdida: se crea otra y se vuelve a entrar con la clave del grupo
            }
        }
        signUp(g);
        writeMember(h, g);
        h.main(h::save);
        return TOKENS.get(g)[0];
    }

    private static void commit(Host h, Group g, JSONArray writes) throws Exception {
        String tk = token(h, g);
        for (int i = 0; i < writes.length(); i += 400) {
            JSONArray part = new JSONArray();
            for (int j = i; j < Math.min(writes.length(), i + 400); j++) part.put(writes.opt(j));
            http("POST", commitUrl(g), new JSONObject().put("writes", part), tk);
        }
    }

    private static List<JSONObject> query(Host h, Group g, String collection, String since) throws Exception {
        String tk = token(h, g);
        JSONObject q = new JSONObject();
        q.put("from", new JSONArray().put(new JSONObject().put("collectionId", collection)));
        q.put("orderBy", new JSONArray().put(new JSONObject().put("field", new JSONObject().put("fieldPath", "updatedAt"))));
        if (since != null) {
            q.put("where", new JSONObject().put("fieldFilter", new JSONObject()
                    .put("field", new JSONObject().put("fieldPath", "updatedAt"))
                    .put("op", "GREATER_THAN")
                    .put("value", new JSONObject().put("timestampValue", since))));
        }
        Object r = http("POST", FS + "/" + docPath(g, null) + ":runQuery", new JSONObject().put("structuredQuery", q), tk);
        List<JSONObject> out = new ArrayList<>();
        if (r instanceof JSONArray) {
            JSONArray a = (JSONArray) r;
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && x.optJSONObject("document") != null) out.add(x.optJSONObject("document"));
            }
        }
        return out;
    }

    private static Player player(Host h, String id) {
        for (Player p : h.players()) if (p.id.equals(id)) return p;
        return null;
    }

    private static void writeMember(Host h, Group g) throws Exception {
        Player me = g.me == null ? null : player(h, g.me);
        JSONObject f = new JSONObject();
        f.put("key", strF(g.key));
        f.put("player", strF(g.me == null ? "" : remoteId(g, g.me)));
        f.put("name", strF(me == null ? "" : me.name));
        f.put("joined", strF(String.valueOf(System.currentTimeMillis())));
        JSONObject w = new JSONObject().put("update", new JSONObject().put("name", docPath(g, "members/" + g.uid)).put("fields", f));
        http("POST", commitUrl(g), new JSONObject().put("writes", new JSONArray().put(w)), TOKENS.get(g)[0]);
    }

    private static JSONObject upd(String name, JSONObject f) throws Exception {
        return new JSONObject().put("update", new JSONObject().put("name", name).put("fields", f))
                .put("updateTransforms", new JSONArray().put(new JSONObject().put("fieldPath", "updatedAt").put("setToServerValue", "REQUEST_TIME")));
    }

    // ------------------------------------------------------------------ ids grupo ↔ móvil

    static String localId(Group g, String rid) {
        String l = g.ids.get(rid);
        return l == null ? rid : l;
    }

    static String remoteId(Group g, String lid) {
        if (lid == null) return null;
        for (Map.Entry<String, String> e : g.ids.entrySet()) if (e.getValue().equals(lid)) return e.getKey();
        return lid;
    }

    private static Map<String, String> inverse(Group g) {
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<String, String> e : g.ids.entrySet()) m.put(e.getValue(), e.getKey());
        return m;
    }

    // ------------------------------------------------------------------ crear, unirse, salir

    static Group group(Host h, String id) {
        for (Group g : h.groups()) if (g.id.equals(id)) return g;
        return null;
    }

    static Group createGroup(Host h, String apiKey, String projectId, String name, String me) throws Exception {
        Group g = new Group();
        g.id = rnd(20); g.key = rnd(24); g.name = name; g.apiKey = apiKey; g.projectId = projectId; g.owner = true; g.me = me;
        signUp(g);
        JSONObject f = new JSONObject();
        f.put("name", strF(name)); f.put("owner", strF(g.uid)); f.put("key", strF(g.key)); f.put("created", strF(String.valueOf(System.currentTimeMillis())));
        JSONObject w = new JSONObject().put("update", new JSONObject().put("name", docPath(g, null)).put("fields", f))
                .put("currentDocument", new JSONObject().put("exists", false));
        http("POST", commitUrl(g), new JSONObject().put("writes", new JSONArray().put(w)), TOKENS.get(g)[0]);
        writeMember(h, g);
        if (me != null) g.dirtyPlayers.add(me);
        h.main(() -> { h.groups().add(g); h.save(); });
        syncGroup(h, g);
        return g;
    }

    static final class JoinData {
        Group group;
        final List<Player> players = new ArrayList<>();
        final List<Game> games = new ArrayList<>();
    }

    /** Primer paso de unirse: entra con la clave y descarga jugadores y partidas (sin aplicarlos aún). */
    static JoinData joinFetch(Host h, Group inv) throws Exception {
        JoinData d = new JoinData();
        Group g = inv;
        g.owner = false;
        signUp(g);
        writeMember(h, g);
        String tk = TOKENS.get(g)[0];
        try {
            String n = fields((JSONObject) http("GET", FS + "/" + docPath(g, null), null, tk)).get("name");
            if (n != null && !n.isEmpty()) g.name = n;
        } catch (CloudException ignored) { }
        for (JSONObject doc : query(h, g, "players", null)) {
            try {
                Player p = Json.player(new JSONObject(fields(doc).get("json")));
                if (!p.id.isEmpty()) d.players.add(p);
            } catch (Exception ignored) { }
        }
        for (JSONObject doc : query(h, g, "games", null)) {
            Map<String, String> f = fields(doc);
            if ("true".equals(f.get("deleted")) || f.get("json") == null || f.get("json").isEmpty()) continue;
            try { d.games.add(Json.game(new JSONObject(f.get("json")))); } catch (Exception ignored) { }
        }
        d.group = g;
        return d;
    }

    /** Segundo paso: aplica el emparejamiento (idGrupo → idLocal|null) y queda unido. */
    static SyncResult joinApply(Host h, Group g, Map<String, String> map, String meRemote, Player meNew) throws Exception {
        h.main(() -> {
            g.ids.clear();
            for (Map.Entry<String, String> e : map.entrySet()) if (e.getValue() != null) g.ids.put(e.getKey(), e.getValue());
            if (meNew != null) {
                h.players().add(meNew);
                g.me = meNew.id;
                g.dirtyPlayers.add(meNew.id);
            } else {
                g.me = localId(g, meRemote);
                // «tú» es un jugador de este móvil que aún no estaba en el grupo: se sube
                if (meRemote != null && !map.containsKey(meRemote) && !g.dirtyPlayers.contains(g.me)) g.dirtyPlayers.add(g.me);
            }
            Group old = group(h, g.id);
            if (old != null) h.groups().remove(old);
            h.groups().add(g);
            h.save();
        });
        try { writeMember(h, g); } catch (CloudException ignored) { }
        return syncGroup(h, g);
    }

    static void leave(Host h, Group g) throws Exception {
        try {
            String tk = token(h, g);
            http("DELETE", FS + "/" + docPath(g, "members/" + g.uid), null, tk);
        } catch (Exception ignored) { }
        h.main(() -> {
            h.groups().remove(g);
            for (Game game : h.games().values()) if (g.id.equals(game.group)) game.group = null;
            h.save();
        });
    }

    static final class Member {
        String uid, player, name;
    }

    static List<Member> members(Host h, Group g) throws Exception {
        String tk = token(h, g);
        Object r = http("GET", FS + "/" + docPath(g, "members") + "?pageSize=300", null, tk);
        List<Member> out = new ArrayList<>();
        JSONArray a = r instanceof JSONObject ? ((JSONObject) r).optJSONArray("documents") : null;
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            Map<String, String> f = fields(d);
            Member m = new Member();
            m.uid = docId(d); m.player = f.get("player"); m.name = f.get("name");
            out.add(m);
        }
        return out;
    }

    static void removeMember(Host h, Group g, String uid) throws Exception {
        http("DELETE", FS + "/" + docPath(g, "members/" + uid), null, token(h, g));
    }

    private static void patchGroup(Host h, Group g, String field, String value) throws Exception {
        JSONObject w = new JSONObject().put("update", new JSONObject().put("name", docPath(g, null)).put("fields", new JSONObject().put(field, strF(value))))
                .put("updateMask", new JSONObject().put("fieldPaths", new JSONArray().put(field)));
        http("POST", commitUrl(g), new JSONObject().put("writes", new JSONArray().put(w)), token(h, g));
    }

    /** Cambia la clave: las invitaciones antiguas dejan de servir (quien ya está, sigue). */
    static void rotateKey(Host h, Group g) throws Exception {
        String key = rnd(24);
        patchGroup(h, g, "key", key);
        g.key = key;
        writeMember(h, g);
        h.main(h::save);
    }

    static void rename(Host h, Group g, String name) throws Exception {
        patchGroup(h, g, "name", name);
        h.main(() -> { g.name = name; h.save(); });
    }

    // ------------------------------------------------------------------ marcar cambios (hilo de la interfaz)

    static void markGame(Host h, Game game) {
        if (game == null || game.group == null) return;
        Group g = group(h, game.group);
        if (g == null) return;
        game.updated = System.currentTimeMillis();
        if (!g.pending.contains(game.id)) g.pending.add(game.id);
        h.save();
    }

    static void markDeleted(Host h, Game game) {
        if (game == null || game.group == null) return;
        Group g = group(h, game.group);
        if (g == null) return;
        g.deleted.add(game.id);
        g.pending.remove(game.id);
        h.save();
    }

    static void markPlayer(Host h, Player p) {
        p.updated = System.currentTimeMillis();
        for (Group g : h.groups()) {
            String rid = remoteId(g, p.id);
            if ((g.roster.contains(rid) || p.id.equals(g.me)) && !g.dirtyPlayers.contains(p.id)) g.dirtyPlayers.add(p.id);
        }
        h.save();
    }

    // ------------------------------------------------------------------ sincronizar

    static final class SyncResult {
        int newGames, updatedGames, players, uploaded;
    }

    /** Sincroniza un grupo: baja jugadores y partidas nuevos, y sube lo pendiente. */
    static SyncResult syncGroup(Host h, Group g) throws Exception {
        SyncResult res = new SyncResult();
        List<JSONObject> pd = query(h, g, "players", g.lastPlayers);
        List<JSONObject> gd = query(h, g, "games", g.lastGames);
        final JSONArray[] writes = {new JSONArray()};
        final List<String> pushed = new ArrayList<>(), pushedDeleted = new ArrayList<>(), pushedPlayers = new ArrayList<>();
        h.main(() -> {
            try {
                // 1) Jugadores
                for (JSONObject d : pd) {
                    Map<String, String> f = fields(d);
                    Player rp;
                    try { rp = Json.player(new JSONObject(f.get("json"))); } catch (Exception e) { continue; }
                    if (rp.id.isEmpty()) continue;
                    g.roster.add(rp.id);
                    String ts = f.get("updatedAt");
                    if (ts != null && (g.lastPlayers == null || ts.compareTo(g.lastPlayers) > 0)) g.lastPlayers = ts;
                    String lid = localId(g, rp.id);
                    Player lp = player(h, lid);
                    if (lp == null) {
                        Player n = new Player();
                        n.id = lid; n.name = uniqueName(h, rp.name); n.color = rp.color; n.created = rp.created;
                        h.players().add(n);
                        res.players++;
                    } else if (lid.equals(rp.id) && rp.updated > lp.updated) {
                        lp.name = rp.name; lp.color = rp.color; lp.updated = rp.updated;
                        for (Game x : h.games().values()) if (x.order.contains(lp.id)) { x.names.put(lp.id, lp.name); x.colors.put(lp.id, lp.color); }
                    }
                }
                // 2) Partidas
                Map<String, Player> people = new LinkedHashMap<>();
                for (Player p : h.players()) people.put(p.id, p);
                for (JSONObject d : gd) {
                    Map<String, String> f = fields(d);
                    String ts = f.get("updatedAt");
                    if (ts != null && (g.lastGames == null || ts.compareTo(g.lastGames) > 0)) g.lastGames = ts;
                    String id = docId(d);
                    if (g.pending.contains(id)) continue;
                    Game cur = h.games().get(id);
                    boolean deleted = "true".equals(f.get("deleted"));
                    if (cur != null && !deleted && g.uid != null && g.uid.equals(f.get("by"))) continue;   // mi propia subida
                    if (deleted) {
                        if (cur != null && !Model.ACTIVE.equals(cur.status)) { h.games().remove(id); res.updatedGames++; }
                        continue;
                    }
                    Game game;
                    try { game = Backup.remap(Json.game(new JSONObject(f.get("json"))), g.ids, people); } catch (Exception e) { continue; }
                    game.group = g.id;
                    if (cur == null) { h.games().put(game.id, game); res.newGames++; }
                    else if (!Model.ACTIVE.equals(cur.status)) { h.games().put(game.id, game); res.updatedGames++; }
                }
                // 3) Preparar subida
                Map<String, String> inv = inverse(g);
                Set<String> toPush = new LinkedHashSet<>(g.dirtyPlayers);
                for (String id : g.pending) {
                    Game x = h.games().get(id);
                    if (x == null) continue;
                    for (String pid : x.order) if (!g.roster.contains(inv.containsKey(pid) ? inv.get(pid) : pid)) toPush.add(pid);
                }
                for (String pid : toPush) {
                    Player p = player(h, pid);
                    if (p == null) continue;
                    String rid = inv.containsKey(pid) ? inv.get(pid) : pid;
                    Player out = new Player();
                    out.id = rid; out.name = p.name; out.color = p.color; out.created = p.created; out.updated = p.updated;
                    writes[0].put(upd(docPath(g, "players/" + rid), new JSONObject().put("json", strF(Json.player(out).toString()))));
                    g.roster.add(rid);
                    pushedPlayers.add(pid);
                }
                for (String id : g.pending) {
                    Game x = h.games().get(id);
                    if (x == null || Model.ACTIVE.equals(x.status)) { if (x == null) pushed.add(id); continue; }
                    JSONObject out = Json.game(Backup.remap(x, inv, new HashMap<>()));
                    out.remove("group");
                    writes[0].put(upd(docPath(g, "games/" + id), new JSONObject().put("json", strF(out.toString()))
                            .put("by", strF(g.uid == null ? "" : g.uid)).put("deleted", new JSONObject().put("booleanValue", false))));
                    pushed.add(id);
                    res.uploaded++;
                }
                for (String id : g.deleted) {
                    writes[0].put(upd(docPath(g, "games/" + id), new JSONObject().put("json", strF(""))
                            .put("by", strF(g.uid == null ? "" : g.uid)).put("deleted", new JSONObject().put("booleanValue", true))));
                    pushedDeleted.add(id);
                }
                h.save();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        if (writes[0].length() > 0) commit(h, g, writes[0]);
        h.main(() -> {
            g.pending.removeAll(pushed);
            g.deleted.removeAll(pushedDeleted);
            g.dirtyPlayers.removeAll(pushedPlayers);
            g.lastSync = System.currentTimeMillis();
            h.save();
        });
        return res;
    }

    private static String uniqueName(Host h, String name) {
        String n = name;
        int k = 2;
        while (taken(h, n)) n = name + " " + k++;
        return n;
    }

    private static boolean taken(Host h, String name) {
        for (Player p : h.players()) if (Backup.norm(p.name).equals(Backup.norm(name))) return true;
        return false;
    }

    /** Mensaje entendible para los errores de Firebase. */
    static String explain(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof CloudException && ((CloudException) e).offline) return "No hay conexión a Internet. Inténtalo cuando la tengas.";
        if (m.contains("ADMIN_ONLY_OPERATION") || m.contains("OPERATION_NOT_ALLOWED"))
            return "En Firebase falta activar el acceso «Anónimo» (Authentication › Sign-in method).";
        if (m.contains("API key not valid") || m.contains("API_KEY_INVALID"))
            return "La configuración de Firebase no es válida: revisa que has copiado bien el apiKey.";
        if (m.contains("does not exist") || m.contains("NOT_FOUND"))
            return "No se encuentra la base de datos: crea «Firestore Database» en tu proyecto de Firebase.";
        if (m.contains("PERMISSION_DENIED") || m.contains("insufficient permissions"))
            return "Firebase ha denegado el acceso: revisa que has pegado las reglas de seguridad de la guía, o la invitación ya no es válida.";
        return m;
    }
}
