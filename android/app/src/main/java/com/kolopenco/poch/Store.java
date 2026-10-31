package com.kolopenco.poch;

import android.content.Context;
import android.content.SharedPreferences;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Player;
import com.kolopenco.poch.Model.Rules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Datos locales en SharedPreferences (JSON). Cada partida va en su propia clave
 * para que anotar una ronda no reescriba todo el historial.
 */
final class Store {
    private final SharedPreferences sp;

    final List<Player> players = new ArrayList<>();
    final List<Rules> profiles = new ArrayList<>();
    String defaultProfile = "clasica";
    final Map<String, Game> games = new LinkedHashMap<>();
    String activeId;
    final List<String> statsSel = new ArrayList<>();
    /** Jugadores de la comparativa «partidas juntos». */
    final List<String> statsCmp = new ArrayList<>();
    /** Grupos de juego a los que pertenece este móvil. */
    final List<Model.Group> groups = new ArrayList<>();
    /** Último grupo elegido al empezar partida. */
    String lastGroup;

    private static final Random RND = new Random();

    Store(Context c) {
        sp = c.getSharedPreferences("poch", Context.MODE_PRIVATE);
        load();
    }

    static String newId(String prefix) {
        return prefix + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RND.nextInt(1296), 36);
    }

    // ------------------------------------------------------------------ carga

    private void load() {
        players.clear();
        profiles.clear();
        games.clear();
        statsSel.clear();
        statsCmp.clear();
        groups.clear();
        try {
            JSONArray gs = new JSONArray(sp.getString("groups", "[]"));
            for (int i = 0; i < gs.length(); i++) {
                JSONObject o = gs.optJSONObject(i);
                if (o != null && !o.optString("id").isEmpty()) groups.add(Json.group(o));
            }
        } catch (Exception ignored) { }
        lastGroup = sp.getString("last_group", null);
        try {
            players.addAll(Json.players(new JSONArray(sp.getString("players", "[]"))));
        } catch (Exception ignored) { }
        try {
            profiles.addAll(Json.profiles(new JSONArray(sp.getString("profiles", "[]"))));
        } catch (Exception ignored) { }
        if (profiles.isEmpty()) {
            profiles.add(Backup.defaultRules());
            saveProfiles();
        }
        defaultProfile = sp.getString("default_profile", profiles.get(0).id);
        if (profile(defaultProfile) == null) defaultProfile = profiles.get(0).id;
        try {
            JSONArray ids = new JSONArray(sp.getString("game_ids", "[]"));
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.optString(i);
                String s = sp.getString("game_" + id, null);
                if (s == null) continue;
                try {
                    Game g = Json.game(new JSONObject(s));
                    games.put(g.id, g);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        activeId = sp.getString("active_game", null);
        Game a = activeId == null ? null : games.get(activeId);
        if (a == null || !Model.ACTIVE.equals(a.status)) activeId = null;
        try {
            JSONArray sel = new JSONArray(sp.getString("stats_sel", "[]"));
            for (int i = 0; i < sel.length(); i++) statsSel.add(sel.optString(i));
        } catch (Exception ignored) { }
        try {
            JSONArray cmp = new JSONArray(sp.getString("stats_cmp", "[]"));
            for (int i = 0; i < cmp.length(); i++) statsCmp.add(cmp.optString(i));
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ jugadores

    Player player(String id) {
        for (Player p : players) if (p.id.equals(id)) return p;
        return null;
    }

    List<Player> playersSorted() {
        List<Player> out = new ArrayList<>(players);
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    int nextColor() {
        for (int c : Theme.PLAYER_COLORS) {
            boolean used = false;
            for (Player p : players) if (p.color == c) used = true;
            if (!used) return c;
        }
        return Theme.PLAYER_COLORS[players.size() % Theme.PLAYER_COLORS.length];
    }

    Player addPlayer(String name, int color) {
        Player p = new Player();
        p.id = newId("j");
        p.name = name.trim();
        p.color = color;
        p.created = System.currentTimeMillis();
        players.add(p);
        savePlayers();
        return p;
    }

    /** Cambia nombre y color, también en las partidas donde aparece. */
    void updatePlayer(Player p, String name, int color) {
        p.name = name.trim();
        p.color = color;
        savePlayers();
        for (Game g : games.values()) {
            if (!g.order.contains(p.id)) continue;
            g.names.put(p.id, p.name);
            g.colors.put(p.id, p.color);
            saveGame(g);
        }
    }

    boolean hasGames(String pid) {
        for (Game g : games.values()) if (g.order.contains(pid)) return true;
        return false;
    }

    void deletePlayer(Player p) {
        players.remove(p);
        statsSel.remove(p.id);
        statsCmp.remove(p.id);
        savePlayers();
        saveStatsSel();
        saveStatsCmp();
    }

    boolean nameTaken(String name, String exceptId) {
        for (Player p : players)
            if (p.name.trim().equalsIgnoreCase(name.trim()) && !p.id.equals(exceptId)) return true;
        return false;
    }

    void savePlayers() {
        try {
            sp.edit().putString("players", Json.players(players).toString()).apply();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ perfiles

    Rules profile(String id) {
        for (Rules r : profiles) if (r.id.equals(id)) return r;
        return null;
    }

    Rules defaultRules() {
        Rules r = profile(defaultProfile);
        return r != null ? r : profiles.get(0);
    }

    void saveProfiles() {
        try {
            sp.edit().putString("profiles", Json.profiles(profiles).toString())
                    .putString("default_profile", defaultProfile).apply();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ partidas

    Game active() {
        return activeId == null ? null : games.get(activeId);
    }

    List<Game> gamesByDate() {
        List<Game> out = new ArrayList<>(games.values());
        Collections.sort(out, (a, b) -> Long.compare(b.created, a.created));
        return out;
    }

    List<Game> closedGames() {
        List<Game> out = new ArrayList<>();
        for (Game g : games.values()) if (Engine.counts(g)) out.add(g);
        return out;
    }

    void addGame(Game g, boolean makeActive) {
        games.put(g.id, g);
        saveGame(g);
        saveIds();
        if (makeActive) setActive(g.id);
    }

    void setActive(String id) {
        activeId = id;
        sp.edit().putString("active_game", id).apply();
    }

    void saveGame(Game g) {
        try {
            sp.edit().putString("game_" + g.id, Json.game(g).toString()).apply();
        } catch (Exception ignored) { }
    }

    void deleteGame(Game g) {
        games.remove(g.id);
        sp.edit().remove("game_" + g.id).apply();
        saveIds();
        if (g.id.equals(activeId)) setActive(null);
    }

    private void saveIds() {
        JSONArray a = new JSONArray();
        for (String id : games.keySet()) a.put(id);
        sp.edit().putString("game_ids", a.toString()).apply();
    }

    /** Última partida con jugadores, para proponer el mismo grupo. */
    Game lastGame() {
        List<Game> gs = gamesByDate();
        return gs.isEmpty() ? null : gs.get(0);
    }

    // ------------------------------------------------------------------ grupos

    synchronized void saveGroups() {
        try {
            JSONArray a = new JSONArray();
            for (Model.Group g : groups) a.put(Json.group(g));
            sp.edit().putString("groups", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    void saveLastGroup(String id) {
        lastGroup = id;
        sp.edit().putString("last_group", id).apply();
    }

    Model.Group group(String id) {
        if (id == null) return null;
        for (Model.Group g : groups) if (g.id.equals(id)) return g;
        return null;
    }

    /** Grupos a los que pertenece el jugador. */
    List<Model.Group> groupsOf(String pid) {
        List<Model.Group> out = new ArrayList<>();
        for (Model.Group g : groups)
            if (pid.equals(g.me) || g.roster.contains(Cloud.remoteId(g, pid))) out.add(g);
        return out;
    }

    /** «Pirolas» o «Pirolas y Viernes». Null si no está en ninguno. */
    String groupNames(String pid) {
        List<Model.Group> gs = groupsOf(pid);
        if (gs.isEmpty()) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < gs.size(); i++) {
            if (i > 0) b.append(i == gs.size() - 1 ? " y " : ", ");
            b.append(gs.get(i).name);
        }
        return b.toString();
    }

    /** ¿Es este jugador «tú» en algún grupo? */
    boolean isMe(String pid) {
        for (Model.Group g : groups) if (pid.equals(g.me)) return true;
        return false;
    }

    /** Guarda todo (tras sincronizar). */
    void saveAll() {
        savePlayers();
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) if (k.startsWith("game_") && !k.equals("game_ids") && !games.containsKey(k.substring(5))) e.remove(k);
        e.apply();
        for (Game g : games.values()) saveGame(g);
        saveIds();
        if (activeId != null && !games.containsKey(activeId)) setActive(null);
        saveGroups();
    }

    // ------------------------------------------------------------------ estadísticas

    void saveStatsCmp() {
        JSONArray a = new JSONArray();
        for (String s : statsCmp) a.put(s);
        sp.edit().putString("stats_cmp", a.toString()).apply();
    }

    void saveStatsSel() {
        JSONArray a = new JSONArray();
        for (String s : statsSel) a.put(s);
        sp.edit().putString("stats_sel", a.toString()).apply();
    }

    // ------------------------------------------------------------------ copias

    Backup.Data snapshot() {
        Backup.Data d = new Backup.Data();
        d.players.addAll(players);
        d.profiles.addAll(profiles);
        d.defaultProfile = defaultProfile;
        d.games.addAll(games.values());
        d.activeGame = activeId;
        d.statsSel.addAll(statsSel);
        d.groups = new ArrayList<>(groups);
        return d;
    }

    String exportJson() throws Exception {
        return Backup.build(snapshot(), BuildConfig.VERSION_NAME, System.currentTimeMillis()).toString(1);
    }

    long lastBackup() { return sp.getLong("last_backup", 0); }

    void markBackup() { sp.edit().putLong("last_backup", System.currentTimeMillis()).apply(); }

    boolean hasPrevData() { return sp.contains("prev_data"); }

    long prevDataTime() { return sp.getLong("prev_data_time", 0); }

    /**
     * Sustituye todos los datos por los de la copia. Antes guarda los actuales en «prev_data»
     * para poder volver atrás. Escribe de forma síncrona (commit) y vuelve a leer para verificar.
     */
    boolean replaceAll(Backup.Data d, boolean keepPrev) throws Exception {
        SharedPreferences.Editor e = sp.edit();
        if (keepPrev) {
            e.putString("prev_data", Backup.build(snapshot(), BuildConfig.VERSION_NAME, System.currentTimeMillis()).toString());
            e.putLong("prev_data_time", System.currentTimeMillis());
        }
        for (String id : games.keySet()) e.remove("game_" + id);
        e.putString("players", Json.players(d.players).toString());
        e.putString("profiles", Json.profiles(d.profiles).toString());
        e.putString("default_profile", d.defaultProfile);
        JSONArray ids = new JSONArray();
        for (Game g : d.games) {
            ids.put(g.id);
            e.putString("game_" + g.id, Json.game(g).toString());
        }
        e.putString("game_ids", ids.toString());
        if (d.activeGame != null) e.putString("active_game", d.activeGame); else e.remove("active_game");
        JSONArray sel = new JSONArray();
        for (String s : d.statsSel) sel.put(s);
        e.putString("stats_sel", sel.toString());
        if (d.groups != null) {
            // Copia con grupos: se restauran tal cual (identidad incluida). Copia antigua: se conservan los actuales.
            JSONArray gs = new JSONArray();
            for (Model.Group g : d.groups) gs.put(Json.group(g));
            e.putString("groups", gs.toString());
        }
        boolean ok = e.commit();
        load();
        if (d.groups == null) d.groups = new ArrayList<>(groups);
        // Verificación: lo leído debe coincidir exactamente con la copia
        String want = Backup.build(d, "", 0).toString();
        String got = Backup.build(snapshot(), "", 0).toString();
        return ok && want.equals(got);
    }

    Backup.Data prevData() throws Exception {
        return Backup.parse(sp.getString("prev_data", ""));
    }

    void dropPrevData() {
        sp.edit().remove("prev_data").remove("prev_data_time").apply();
    }
}
