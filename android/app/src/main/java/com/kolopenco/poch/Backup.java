package com.kolopenco.poch;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Player;
import com.kolopenco.poch.Model.Rules;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Copias de seguridad: todo en un único JSON. Java puro para poder probarlo. */
final class Backup {
    private Backup() {}

    static final class Data {
        List<Player> players = new ArrayList<>();
        List<Rules> profiles = new ArrayList<>();
        String defaultProfile = "";
        List<Game> games = new ArrayList<>();
        String activeGame;
        List<String> statsSel = new ArrayList<>();
        long exported;
        String version = "";

        int closedGames() {
            int k = 0;
            for (Game g : games) if (Engine.counts(g)) k++;
            return k;
        }
    }

    static JSONObject build(Data d, String version, long now) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("app", "Poch");
        o.put("schema", Json.SCHEMA);
        o.put("version", version);
        o.put("exported", now);
        o.put("players", Json.players(d.players));
        o.put("profiles", Json.profiles(d.profiles));
        o.put("defaultProfile", d.defaultProfile == null ? "" : d.defaultProfile);
        o.put("games", Json.games(d.games));
        if (d.activeGame != null) o.put("activeGame", d.activeGame);
        JSONArray sel = new JSONArray();
        for (String s : d.statsSel) sel.put(s);
        o.put("statsSel", sel);
        return o;
    }

    /** Lee y valida una copia. Lanza una excepción con un mensaje claro si no es válida. */
    static Data parse(String text) throws Exception {
        JSONObject o;
        try {
            o = new JSONObject(text.trim());
        } catch (Exception e) {
            throw new Exception("El archivo no es una copia de Poch! (JSON no válido).");
        }
        if (!"Poch".equals(o.optString("app"))) throw new Exception("El archivo no es una copia de Poch!.");
        if (o.optInt("schema", 0) > Json.SCHEMA)
            throw new Exception("La copia es de una versión más nueva de la app. Actualiza Poch! antes de restaurarla.");
        Data d = new Data();
        d.version = o.optString("version");
        d.exported = o.optLong("exported", 0);
        d.players = Json.players(o.optJSONArray("players"));
        d.profiles = Json.profiles(o.optJSONArray("profiles"));
        d.defaultProfile = o.optString("defaultProfile", "");
        d.games = Json.games(o.optJSONArray("games"));
        d.activeGame = o.has("activeGame") ? o.optString("activeGame") : null;
        JSONArray sel = o.optJSONArray("statsSel");
        if (sel != null) for (int i = 0; i < sel.length(); i++) d.statsSel.add(sel.optString(i));

        // Comprobaciones de integridad
        Set<String> ids = new HashSet<>();
        for (Player p : d.players) {
            if (!ids.add(p.id)) throw new Exception("La copia tiene jugadores repetidos.");
        }
        Set<String> gids = new HashSet<>();
        for (Game g : d.games) {
            if (!gids.add(g.id)) throw new Exception("La copia tiene partidas repetidas.");
            if (g.order.size() < Engine.MIN_PLAYERS || g.rounds.isEmpty())
                throw new Exception("Una partida de la copia está incompleta.");
        }
        if (d.activeGame != null) {
            Game a = null;
            for (Game g : d.games) if (g.id.equals(d.activeGame)) a = g;
            if (a == null || !Model.ACTIVE.equals(a.status)) d.activeGame = null;
        }
        if (d.profiles.isEmpty()) d.profiles.add(defaultRules());
        boolean found = false;
        for (Rules r : d.profiles) if (r.id.equals(d.defaultProfile)) found = true;
        if (!found) d.defaultProfile = d.profiles.get(0).id;
        return d;
    }

    // ------------------------------------------------------------------ mezclar copias

    static String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Emparejamiento propuesto de los jugadores de la copia con los míos: mismo id (viene de una copia mía
     * o ya mezclada antes) o, si no, mismo nombre. null = añadir como jugador nuevo.
     */
    static Map<String, String> suggest(Data mine, Data in) {
        Map<String, String> map = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Player p : in.players) {
            for (Player q : mine.players) if (q.id.equals(p.id)) { map.put(p.id, q.id); used.add(q.id); }
        }
        // Partidas que ya tengo (mezcladas antes): el jugador en la misma posición es la misma persona
        for (Game g : in.games) {
            for (Game m : mine.games) {
                if (!m.id.equals(g.id) || m.order.size() != g.order.size()) continue;
                for (int i = 0; i < g.order.size(); i++) {
                    String a = g.order.get(i), b = m.order.get(i);
                    if (map.containsKey(a) || used.contains(b)) continue;
                    boolean mineHas = false;
                    for (Player q : mine.players) if (q.id.equals(b)) mineHas = true;
                    if (mineHas) { map.put(a, b); used.add(b); }
                }
            }
        }
        for (Player p : in.players) {
            if (map.containsKey(p.id)) continue;
            String target = null;
            for (Player q : mine.players)
                if (!used.contains(q.id) && norm(q.name).equals(norm(p.name)) && !norm(p.name).isEmpty()) { target = q.id; break; }
            if (target != null) used.add(target);
            map.put(p.id, target);
        }
        return map;
    }

    static final class MergeResult {
        Data data;
        int newPlayers, mergedPlayers, newGames, updatedGames, sameGames, skippedActive, newProfiles;
    }

    /**
     * Mezcla la copia {@code in} con mis datos. {@code map}: jugador de la copia → mi jugador (null = nuevo).
     * Las partidas en curso de la copia no se importan; una partida que ya tengo se sustituye solo si la de la
     * copia tiene más rondas guardadas. Lanza una excepción si dos jugadores acabarían siendo el mismo en una partida.
     */
    static MergeResult merge(Data mine, Data in, Map<String, String> map) throws Exception {
        MergeResult r = new MergeResult();
        Data d = new Data();
        d.players.addAll(mine.players);
        d.profiles.addAll(mine.profiles);
        d.defaultProfile = mine.defaultProfile;
        d.games.addAll(mine.games);
        d.activeGame = mine.activeGame;
        d.statsSel.addAll(mine.statsSel);

        Map<String, String> ids = new LinkedHashMap<>();
        Map<String, Player> people = new LinkedHashMap<>();
        for (Player p : d.players) people.put(p.id, p);
        Set<String> targets = new HashSet<>();
        for (Player p : in.players) {
            String t = map.get(p.id);
            if (t != null && people.containsKey(t)) {
                if (!targets.add(t)) throw new Exception("Dos jugadores de la copia están unidos al mismo jugador tuyo.");
                ids.put(p.id, t);
                r.mergedPlayers++;
            } else if (people.containsKey(p.id)) {
                ids.put(p.id, p.id);          // mismo jugador (mismo id) aunque no se haya elegido
                r.mergedPlayers++;
            } else {
                Player n = new Player();
                n.id = p.id; n.name = p.name; n.color = p.color; n.created = p.created;
                d.players.add(n);
                people.put(n.id, n);
                ids.put(p.id, p.id);
                r.newPlayers++;
            }
        }

        for (Game src : in.games) {
            if (Model.ACTIVE.equals(src.status)) { r.skippedActive++; continue; }
            Game g = remap(src, ids, people);
            int found = -1;
            for (int i = 0; i < d.games.size(); i++) if (d.games.get(i).id.equals(g.id)) found = i;
            if (found < 0) {
                d.games.add(g);
                r.newGames++;
            } else {
                Game old = d.games.get(found);
                if (Model.ACTIVE.equals(old.status) || Engine.savedCount(g) <= Engine.savedCount(old)) {
                    r.sameGames++;
                } else {
                    d.games.set(found, g);
                    r.updatedGames++;
                }
            }
        }

        Set<String> pids = new HashSet<>();
        for (Rules x : d.profiles) pids.add(x.id);
        for (Rules x : in.profiles) {
            if (pids.add(x.id)) { d.profiles.add(x.copy()); r.newProfiles++; }
        }
        r.data = d;
        return r;
    }

    /** Copia de la partida con los jugadores renombrados según {@code ids}. */
    static Game remap(Game src, Map<String, String> ids, Map<String, Player> people) throws Exception {
        Game g = Json.game(new JSONObject(Json.game(src).toString()));
        List<String> order = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String p : g.order) {
            String q = id(ids, p);
            if (order.contains(q)) throw new Exception("Dos jugadores de la copia están unidos al mismo jugador tuyo.");
            order.add(q);
            Player pl = people.get(q);
            names.put(q, pl != null ? pl.name : g.name(p));
            colors.put(q, pl != null ? pl.color : g.color(p));
        }
        g.order.clear(); g.order.addAll(order);
        g.names.clear(); g.names.putAll(names);
        g.colors.clear(); g.colors.putAll(colors);
        for (Model.Round rd : g.rounds) {
            remapRes(rd.res, ids);
            if (rd.winner != null) rd.winner = id(ids, rd.winner);
            if (rd.only != null) {
                List<String> o = new ArrayList<>();
                for (String p : rd.only) o.add(id(ids, p));
                rd.only = o;
            }
            for (Model.Revision rv : rd.revs) {
                for (Model.Snap s : new Model.Snap[]{rv.before, rv.after}) {
                    if (s == null) continue;
                    remapRes(s.res, ids);
                    if (s.winner != null) s.winner = id(ids, s.winner);
                }
            }
        }
        return g;
    }

    private static String id(Map<String, String> ids, String p) {
        String q = ids.get(p);
        return q == null ? p : q;
    }

    private static void remapRes(Map<String, Model.Res> res, Map<String, String> ids) {
        Map<String, Model.Res> n = new LinkedHashMap<>();
        for (Map.Entry<String, Model.Res> e : res.entrySet()) n.put(id(ids, e.getKey()), e.getValue());
        res.clear();
        res.putAll(n);
    }

    static Rules defaultRules() {
        Rules r = new Rules();
        r.id = "clasica";
        r.name = "Pocha clásica";
        return r;
    }
}
