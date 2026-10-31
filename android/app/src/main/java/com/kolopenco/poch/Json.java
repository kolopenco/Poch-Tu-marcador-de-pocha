package com.kolopenco.poch;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Player;
import com.kolopenco.poch.Model.Res;
import com.kolopenco.poch.Model.Revision;
import com.kolopenco.poch.Model.Round;
import com.kolopenco.poch.Model.Rules;
import com.kolopenco.poch.Model.Snap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Conversión del modelo a JSON y vuelta (formato de guardado y de las copias). */
final class Json {
    private Json() {}

    static final int SCHEMA = 1;

    // ------------------------------------------------------------ jugadores

    static JSONObject player(Player p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("name", p.name);
        o.put("color", p.color);
        o.put("created", p.created);
        if (p.updated != 0) o.put("updated", p.updated);
        return o;
    }

    static Player player(JSONObject o) {
        Player p = new Player();
        p.id = o.optString("id");
        p.name = o.optString("name");
        p.color = o.optInt("color", 0xFF607080);
        p.created = o.optLong("created", 0);
        p.updated = o.optLong("updated", 0);
        return p;
    }

    static JSONArray players(List<Player> ps) throws JSONException {
        JSONArray a = new JSONArray();
        for (Player p : ps) a.put(player(p));
        return a;
    }

    static List<Player> players(JSONArray a) {
        List<Player> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optString("id").isEmpty()) out.add(player(o));
        }
        return out;
    }

    // ------------------------------------------------------------ reglas

    static JSONObject rules(Rules r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("schema", SCHEMA);
        o.put("id", r.id);
        o.put("name", r.name);
        o.put("deck", r.deck);
        o.put("maxCards", r.maxCards);
        o.put("seqType", r.seqType);
        o.put("customSeq", r.customSeq);
        o.put("repeatMax", r.repeatMax);
        o.put("auctionMax", r.auctionMax);
        o.put("base", r.base);
        o.put("perTrick", r.perTrick);
        o.put("penalty", r.penalty);
        o.put("multBase", r.multBase);
        JSONArray on = new JSONArray(), f = new JSONArray();
        for (int i = 0; i < 4; i++) { on.put(r.multOn[i]); f.put(r.multF[i]); }
        o.put("multOn", on);
        o.put("multF", f);
        o.put("forbidSum", r.forbidSum);
        o.put("tie", r.tie);
        return o;
    }

    static Rules rules(JSONObject o) {
        Rules r = new Rules();
        if (o == null) return r;
        r.id = o.optString("id", "");
        r.name = o.optString("name", r.name);
        r.deck = o.optInt("deck", r.deck);
        r.maxCards = o.optInt("maxCards", r.maxCards);
        r.seqType = o.optString("seqType", r.seqType);
        r.customSeq = o.optString("customSeq", "");
        r.repeatMax = o.optInt("repeatMax", 0);
        r.auctionMax = o.optBoolean("auctionMax", true);
        r.base = o.optInt("base", r.base);
        r.perTrick = o.optInt("perTrick", r.perTrick);
        r.penalty = o.optInt("penalty", r.penalty);
        r.multBase = o.optBoolean("multBase", false);
        JSONArray on = o.optJSONArray("multOn"), f = o.optJSONArray("multF");
        for (int i = 0; i < 4; i++) {
            if (on != null && i < on.length()) r.multOn[i] = on.optBoolean(i, r.multOn[i]);
            if (f != null && i < f.length()) r.multF[i] = f.optInt(i, r.multF[i]);
        }
        r.forbidSum = o.optBoolean("forbidSum", true);
        r.tie = o.optString("tie", Model.TIE_SHARED);
        return r;
    }

    static JSONArray profiles(List<Rules> ps) throws JSONException {
        JSONArray a = new JSONArray();
        for (Rules r : ps) a.put(rules(r));
        return a;
    }

    static List<Rules> profiles(JSONArray a) {
        List<Rules> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(rules(o));
        }
        return out;
    }

    // ------------------------------------------------------------ partidas

    private static void putInt(JSONObject o, String k, Integer v) throws JSONException {
        if (v != null) o.put(k, v.intValue());
    }

    private static Integer getInt(JSONObject o, String k) {
        if (!o.has(k) || o.isNull(k)) return null;
        return o.optInt(k);
    }

    static JSONObject res(Map<String, Res> m) throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Res> e : m.entrySet()) {
            Res x = e.getValue();
            JSONObject r = new JSONObject();
            putInt(r, "v", x.v);
            putInt(r, "b", x.bid);
            putInt(r, "m", x.made);
            o.put(e.getKey(), r);
        }
        return o;
    }

    static void res(JSONObject o, Map<String, Res> into) {
        into.clear();
        if (o == null) return;
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject r = o.optJSONObject(k);
            if (r == null) continue;
            Res x = new Res();
            x.v = getInt(r, "v");
            x.bid = getInt(r, "b");
            x.made = getInt(r, "m");
            into.put(k, x);
        }
    }

    static JSONObject snap(Snap s) throws JSONException {
        JSONObject o = new JSONObject();
        if (s.mult != null) o.put("mult", s.mult);
        if (s.winner != null) o.put("winner", s.winner);
        o.put("res", res(s.res));
        return o;
    }

    static Snap snap(JSONObject o) {
        Snap s = new Snap();
        if (o == null) return s;
        s.mult = o.has("mult") ? o.optString("mult") : null;
        s.winner = o.has("winner") ? o.optString("winner") : null;
        res(o.optJSONObject("res"), s.res);
        return s;
    }

    static JSONObject round(Round rd) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("cards", rd.cards);
        o.put("auction", rd.auction);
        if (rd.tiebreak) o.put("tiebreak", true);
        if (rd.only != null) {
            JSONArray a = new JSONArray();
            for (String p : rd.only) a.put(p);
            o.put("only", a);
        }
        if (rd.mult != null) o.put("mult", rd.mult);
        if (rd.winner != null) o.put("winner", rd.winner);
        o.put("res", res(rd.res));
        o.put("saved", rd.saved);
        if (rd.savedAt != 0) o.put("savedAt", rd.savedAt);
        if (!rd.revs.isEmpty()) {
            JSONArray a = new JSONArray();
            for (Revision rv : rd.revs) {
                JSONObject r = new JSONObject();
                r.put("at", rv.at);
                r.put("before", snap(rv.before));
                r.put("after", snap(rv.after));
                if (rv.undone) r.put("undone", true);
                if (rv.isUndo) r.put("isUndo", true);
                a.put(r);
            }
            o.put("revs", a);
        }
        return o;
    }

    static Round round(JSONObject o) {
        Round rd = new Round();
        rd.cards = o.optInt("cards", 1);
        rd.auction = o.optBoolean("auction", false);
        rd.tiebreak = o.optBoolean("tiebreak", false);
        JSONArray only = o.optJSONArray("only");
        if (only != null) {
            rd.only = new ArrayList<>();
            for (int i = 0; i < only.length(); i++) rd.only.add(only.optString(i));
        }
        rd.mult = o.has("mult") ? o.optString("mult") : null;
        rd.winner = o.has("winner") ? o.optString("winner") : null;
        res(o.optJSONObject("res"), rd.res);
        rd.saved = o.optBoolean("saved", false);
        rd.savedAt = o.optLong("savedAt", 0);
        JSONArray revs = o.optJSONArray("revs");
        if (revs != null) {
            for (int i = 0; i < revs.length(); i++) {
                JSONObject r = revs.optJSONObject(i);
                if (r == null) continue;
                Revision rv = new Revision();
                rv.at = r.optLong("at", 0);
                rv.before = snap(r.optJSONObject("before"));
                rv.after = snap(r.optJSONObject("after"));
                rv.undone = r.optBoolean("undone", false);
                rv.isUndo = r.optBoolean("isUndo", false);
                rd.revs.add(rv);
            }
        }
        return rd;
    }

    static JSONObject game(Game g) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("schema", SCHEMA);
        o.put("id", g.id);
        o.put("status", g.status);
        o.put("created", g.created);
        if (g.ended != 0) o.put("ended", g.ended);
        o.put("mode", g.mode);
        JSONArray order = new JSONArray();
        for (String p : g.order) order.put(p);
        o.put("order", order);
        JSONObject names = new JSONObject(), colors = new JSONObject();
        for (String p : g.order) {
            names.put(p, g.name(p));
            colors.put(p, g.color(p));
        }
        o.put("names", names);
        o.put("colors", colors);
        o.put("rules", rules(g.rules));
        JSONArray rs = new JSONArray();
        for (Round rd : g.rounds) rs.put(round(rd));
        o.put("rounds", rs);
        if (g.closed) o.put("closed", true);
        if (g.group != null) o.put("group", g.group);
        if (g.updated != 0) o.put("updated", g.updated);
        return o;
    }

    static Game game(JSONObject o) {
        Game g = new Game();
        g.id = o.optString("id");
        g.status = o.optString("status", Model.ACTIVE);
        g.created = o.optLong("created", 0);
        g.ended = o.optLong("ended", 0);
        g.mode = o.optString("mode", Model.SIMPLE);
        JSONArray order = o.optJSONArray("order");
        JSONObject names = o.optJSONObject("names"), colors = o.optJSONObject("colors");
        if (order != null) {
            for (int i = 0; i < order.length(); i++) {
                String p = order.optString(i);
                g.order.add(p);
                g.names.put(p, names == null ? "?" : names.optString(p, "?"));
                g.colors.put(p, colors == null ? 0xFF607080 : colors.optInt(p, 0xFF607080));
            }
        }
        g.rules = rules(o.optJSONObject("rules"));
        JSONArray rs = o.optJSONArray("rounds");
        if (rs != null) {
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.optJSONObject(i);
                if (r != null) g.rounds.add(round(r));
            }
        }
        g.closed = o.optBoolean("closed", false);
        g.group = o.has("group") ? o.optString("group") : null;
        g.updated = o.optLong("updated", 0);
        return g;
    }

    static JSONArray games(List<Game> gs) throws JSONException {
        JSONArray a = new JSONArray();
        for (Game g : gs) a.put(game(g));
        return a;
    }

    static List<Game> games(JSONArray a) {
        List<Game> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optString("id").isEmpty()) out.add(game(o));
        }
        return out;
    }

    // ------------------------------------------------------------ grupos

    static JSONObject group(Model.Group g) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", g.id); o.put("name", g.name); o.put("apiKey", g.apiKey); o.put("projectId", g.projectId); o.put("key", g.key);
        if (g.uid != null) o.put("uid", g.uid);
        if (g.refresh != null) o.put("refresh", g.refresh);
        o.put("owner", g.owner);
        if (g.me != null) o.put("me", g.me);
        JSONObject ids = new JSONObject();
        for (Map.Entry<String, String> e : g.ids.entrySet()) ids.put(e.getKey(), e.getValue());
        o.put("ids", ids);
        o.put("roster", strings(g.roster));
        o.put("pending", strings(g.pending));
        o.put("deleted", strings(g.deleted));
        o.put("dirtyPlayers", strings(g.dirtyPlayers));
        if (g.lastPlayers != null) o.put("lastPlayers", g.lastPlayers);
        if (g.lastGames != null) o.put("lastGames", g.lastGames);
        o.put("lastSync", g.lastSync);
        return o;
    }

    static Model.Group group(JSONObject o) {
        Model.Group g = new Model.Group();
        g.id = o.optString("id"); g.name = o.optString("name", "Grupo"); g.apiKey = o.optString("apiKey");
        g.projectId = o.optString("projectId"); g.key = o.optString("key");
        g.uid = o.has("uid") ? o.optString("uid") : null;
        g.refresh = o.has("refresh") ? o.optString("refresh") : null;
        g.owner = o.optBoolean("owner", false);
        g.me = o.has("me") ? o.optString("me") : null;
        JSONObject ids = o.optJSONObject("ids");
        if (ids != null) { Iterator<String> it = ids.keys(); while (it.hasNext()) { String k = it.next(); g.ids.put(k, ids.optString(k)); } }
        readStrings(o.optJSONArray("roster"), g.roster);
        readStrings(o.optJSONArray("pending"), g.pending);
        readStrings(o.optJSONArray("deleted"), g.deleted);
        readStrings(o.optJSONArray("dirtyPlayers"), g.dirtyPlayers);
        g.lastPlayers = o.has("lastPlayers") ? o.optString("lastPlayers") : null;
        g.lastGames = o.has("lastGames") ? o.optString("lastGames") : null;
        g.lastSync = o.optLong("lastSync", 0);
        return g;
    }

    private static JSONArray strings(java.util.Collection<String> c) {
        JSONArray a = new JSONArray();
        for (String s : c) a.put(s);
        return a;
    }

    private static void readStrings(JSONArray a, java.util.Collection<String> into) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) into.add(a.optString(i));
    }
}
