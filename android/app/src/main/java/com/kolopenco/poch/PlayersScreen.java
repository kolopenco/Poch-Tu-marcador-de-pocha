package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;
import static com.kolopenco.poch.Ui.lpm;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Player;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Lista de jugadores con buscador y botón para añadir. */
class PlayersScreen extends Screen {
    @Override boolean liveRefresh() { return true; }
    String query = "";
    private LinearLayout list;

    PlayersScreen(MainActivity a) { super(a); }

    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    static String gamesWins(Engine.PStats s) {
        return Ui.plural(s.games, "partida", "partidas") + " · " + Ui.fmt1(s.wins) + (s.wins == 1 ? " victoria" : " victorias");
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        FrameLayout add = new FrameLayout(a);
        add.setBackground(Ui.grad(Theme.BLUE_HI, Theme.BLUE_LO, 20));
        add.addView(Ui.icon(Ico.PLUS, 0xFFFFFFFF, 22), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        Ui.press(add, 20, () -> editPlayer(a, st, null, () -> a.refresh()));
        LinearLayout.LayoutParams ap = lp(dp(40), dp(40));
        ap.rightMargin = dp(6);
        add.setLayoutParams(ap);
        root.addView(Ui.header(Ui.icon(Ico.PEOPLE, Theme.TEXT, 26), "Jugadores", null, add));

        LinearLayout search = Ui.h();
        search.setBackground(Ui.round(Theme.SURFACE, 12, Theme.BORDER, 1));
        Ui.pad(search, 12, 0, 8, 0);
        search.addView(Ui.icon(Ico.SEARCH, Theme.MUTED, 19));
        EditText q = new EditText(a);
        q.setHint("Buscar jugador…");
        q.setText(query);
        q.setTextColor(Theme.TEXT);
        q.setHintTextColor(Theme.MUTED);
        q.setTypeface(Theme.REG);
        q.setTextSize(15);
        q.setSingleLine(true);
        q.setBackground(null);
        Ui.pad(q, 10, 12, 4, 12);
        search.addView(q, lp(0, WC, 1));
        root.addView(search, lpm(MW, WC, 16, 0, 16, 12));

        list = Ui.v();
        Ui.pad(list, 16, 0, 16, 16);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a1, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                fill();
            }
        });
        fill();
        ScrollView sv = a.screenScroll(list);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    private void fill() {
        list.removeAllViews();
        List<Game> closed = st.closedGames();
        List<Player> ps = new ArrayList<>(st.players);
        List<String> ids = new ArrayList<>();
        for (Player p : ps) ids.add(p.id);
        Map<String, Engine.PStats> stats = Engine.statsAll(closed, ids);
        Collections.sort(ps, (x, y) -> {
            int c = Integer.compare(stats.get(y.id).games, stats.get(x.id).games);
            return c != 0 ? c : x.name.compareToIgnoreCase(y.name);
        });
        String nq = norm(query.trim());
        int shown = 0;
        for (Player p : ps) {
            if (!nq.isEmpty() && !norm(p.name).contains(nq)) continue;
            shown++;
            list.addView(Ui.listRow(Ui.dot(p.color, 32), st.isMe(p.id) ? p.name + " (tú)" : p.name, gamesWins(stats.get(p.id)),
                    () -> a.push(new PlayerDetail(a, p.id))));
        }
        if (shown == 0) {
            TextView e = Ui.tv(st.players.isEmpty() ? "Aún no hay jugadores. Pulsa + para añadir el primero."
                    : "Ningún jugador coincide con la búsqueda.", 14.5f, Theme.MUTED, Theme.REG);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(20), dp(36), dp(20), 0);
            list.addView(e);
        }
    }

    /** Hoja para crear o editar un jugador (nombre y color). */
    static void editPlayer(MainActivity a, Store st, Player p, Runnable done) {
        LinearLayout body = Ui.v();
        EditText name = Ui.input("Nombre", p == null ? "" : p.name);
        body.addView(name, lp(MW, WC));
        body.addView(Ui.section("Color"));
        final int[] color = {p == null ? st.nextColor() : p.color};
        Flow flow = new Flow(a, dp(10), dp(10));
        List<FrameLayout> swatches = new ArrayList<>();
        for (int c : Theme.PLAYER_COLORS) {
            FrameLayout sw = new FrameLayout(a);
            sw.addView(Ui.dot(c, 38), new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            sw.setTag(c);
            swatches.add(sw);
            Ui.press(sw, 23, () -> {
                color[0] = c;
                for (FrameLayout s : swatches)
                    s.setBackground(((int) (Integer) s.getTag()) == color[0] ? Ui.round(0, 23, 0xFFFFFFFF, 2.5f) : null);
            });
            sw.setBackground(c == color[0] ? Ui.round(0, 23, 0xFFFFFFFF, 2.5f) : null);
            flow.addView(sw, new android.view.ViewGroup.LayoutParams(dp(46), dp(46)));
        }
        body.addView(flow, lp(MW, WC));
        body.addView(Ui.space(18));
        final FrameLayout[] layer = new FrameLayout[1];
        body.addView(Ui.primary(p == null ? "Añadir jugador" : "Guardar", () -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                a.toast("Escribe un nombre");
                return;
            }
            if (st.nameTaken(n, p == null ? null : p.id)) {
                a.toast("Ya hay un jugador llamado " + n);
                return;
            }
            if (p == null) st.addPlayer(n, color[0]);
            else {
                st.updatePlayer(p, n, color[0]);
                Sync.playerChanged(a, p);
            }
            a.closeOverlay();
            done.run();
        }), lp(MW, WC));
        if (p != null) {
            body.addView(Ui.space(10));
            boolean has = st.hasGames(p.id);
            TextView del = Ui.secondary("Eliminar jugador", Theme.RED_HI, () -> {
                if (st.hasGames(p.id)) {
                    a.info("No se puede eliminar", p.name + " tiene partidas en el historial. Sus estadísticas dependen de ellas.");
                    return;
                }
                a.confirm("Eliminar jugador", "Se eliminará a " + p.name + ".", "Eliminar", true, () -> {
                    st.deletePlayer(p);
                    a.closeAllOverlays();
                    a.popToRoot(a.tab);
                    a.refresh();
                });
            });
            if (has) del.setAlpha(0.6f);
            body.addView(del, lp(MW, WC));
        }
        layer[0] = a.sheet(p == null ? "Nuevo jugador" : "Editar jugador", body);
        if (p == null) {
            name.requestFocus();
            name.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(name, 0);
            }, 250);
        }
    }
}

/** Ficha de un jugador con sus estadísticas. */
class PlayerDetail extends Screen {
    final String pid;
    /** Filtro: solo partidas en las que también jugaron todos estos jugadores. */
    final List<String> with = new ArrayList<>();

    PlayerDetail(MainActivity a, String pid) {
        super(a);
        this.pid = pid;
    }

    @Override View build() {
        Player p = st.player(pid);
        LinearLayout root = Ui.v();
        if (p == null) {
            root.addView(Ui.centerHeader("Jugador", a::pop, null));
            return root;
        }
        FrameLayout edit = Ui.iconButton(Ico.EDIT, Theme.TEXT, 21, () -> PlayersScreen.editPlayer(a, st, p, a::refresh));
        root.addView(Ui.centerHeader(p.name, a::pop, edit));
        with.removeIf(id -> st.player(id) == null);
        List<Game> pool = st.closedGames();
        if (!with.isEmpty()) {
            List<String> all = new ArrayList<>(with);
            all.add(pid);
            pool = Engine.withAll(pool, all);
        }
        Engine.PStats s = Engine.stats(pool, pid);
        List<String> withNames = new ArrayList<>();
        for (String id : with) withNames.add(st.player(id).name);

        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 2, 16, 24);

        LinearLayout hero = Ui.h();
        hero.addView(Ui.dot(p.color, 50));
        LinearLayout hc = Ui.v();
        hc.setPadding(dp(14), 0, 0, 0);
        hc.addView(Ui.one(p.name, 22, Theme.TEXT, Theme.BOLD));
        TextView hs = Ui.tv(with.isEmpty() ? "Estadísticas generales" : "Jugando con " + GameScreen.joinNames(withNames),
                13.5f, with.isEmpty() ? Theme.TEXT2 : Theme.BLUE_TEXT, with.isEmpty() ? Theme.REG : Theme.MED);
        hs.setPadding(0, dp(4), 0, 0);
        hs.setMaxLines(2);
        hc.addView(hs);
        hero.addView(hc, lp(0, WC, 1));
        // Filtro: debajo del botón de editar, a la derecha del nombre
        boolean on = !with.isEmpty();
        LinearLayout filter = Ui.h();
        filter.setBackground(on ? Ui.grad(Theme.BLUE_HI, Theme.BLUE_LO, 10) : Ui.round(Theme.SURFACE, 10, Theme.BORDER2, 1));
        Ui.pad(filter, 10, 0, 12, 0);
        filter.setMinimumHeight(dp(38));
        filter.addView(Ui.icon(Ico.FILTER, on ? Theme.TEXT : Theme.TEXT2, 17));
        TextView ft = Ui.one(on ? "Con " + with.size() : "Filtrar", 13.5f, on ? Theme.TEXT : Theme.TEXT2, on ? Theme.SEMI : Theme.MED);
        ft.setPadding(dp(6), 0, 0, 0);
        filter.addView(ft);
        Ui.press(filter, 10, () -> pickWith(p));
        LinearLayout.LayoutParams fp = lp(WC, WC);
        fp.leftMargin = dp(8);
        hero.addView(filter, fp);
        body.addView(hero, lpm(MW, WC, 2, 4, 0, 14));
        if (on) {
            LinearLayout note = Ui.h();
            note.setBackground(Ui.round(Theme.BLUE_BG, 12, Theme.alpha(Theme.BLUE, 0.7f), 1));
            Ui.pad(note, 12, 6, 4, 6);
            note.addView(Ui.icon(Ico.PEOPLE, Theme.BLUE_TEXT, 19));
            TextView nt = Ui.tv(s.games == 0 ? "No hay partidas cerradas de " + p.name + " con " + GameScreen.joinNames(withNames) + "."
                            : "Solo partidas con " + GameScreen.joinNames(withNames) + ": " + Ui.plural(s.games, "partida", "partidas") + ".",
                    13, Theme.TEXT, Theme.MED);
            nt.setLineSpacing(dp(2), 1);
            nt.setPadding(dp(10), 0, dp(4), 0);
            note.addView(nt, lp(0, WC, 1));
            note.addView(Ui.iconButton(Ico.CLOSE, Theme.TEXT2, 18, () -> {
                with.clear();
                a.refresh();
            }));
            body.addView(note, lpm(MW, WC, 0, 0, 0, 14));
        }

        body.addView(Ui.tileRow(
                Ui.tile(Ico.TROPHY, Theme.GOLD, true, Ui.fmt1(s.wins), "Victorias"),
                Ui.tile(Ico.GAMEPAD, Theme.BLUE_HI, false, String.valueOf(s.games), "Partidas"),
                Ui.tile(Ico.CHART, Theme.TEXT2, false, Ui.fmt1(s.posAvg()), "Posición media"),
                Ui.tile(Ico.STAR, Theme.GOLD, true, s.completed == 0 ? "—" : Views.num(s.bestScore), "Mejor puntuación")));
        if (s.games > s.completed) {
            TextView n = Ui.tv(Ui.plural(s.games - s.completed, "partida finalizada", "partidas finalizadas")
                    + " antes de tiempo: cuentan sus rondas, no victorias ni puestos.", 12, Theme.MUTED, Theme.REG);
            n.setPadding(dp(2), dp(8), 0, 0);
            body.addView(n);
        }

        // Resultados en rondas
        body.addView(Ui.section("Resultados en rondas"));
        LinearLayout rr = Ui.h();
        LinearLayout left = Ui.h();
        left.setBackground(Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
        Ui.pad(left, 12, 12, 12, 12);
        Views.Ring ring = new Views.Ring(a);
        ring.pct = (float) s.hitPct();
        left.addView(ring, lp(dp(66), dp(66)));
        LinearLayout lc = Ui.v();
        lc.setPadding(dp(12), 0, 0, 0);
        lc.addView(Ui.one("Rondas acertadas", 12.5f, Theme.TEXT2, Theme.MED));
        lc.addView(line(s.hits + " acertadas", Theme.GREEN_HI));
        lc.addView(line(s.misses + " falladas", Theme.RED_HI));
        lc.addView(line("Total: " + s.rounds, Theme.MUTED));
        left.addView(lc, lp(0, WC, 1));
        rr.addView(left, lp(0, MW, 1.45f));
        LinearLayout right = Ui.v();
        right.setGravity(Gravity.CENTER);
        right.setBackground(Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
        Ui.pad(right, 8, 10, 8, 10);
        TextView rt = Ui.tv("Media por partida", 12.5f, Theme.TEXT2, Theme.MED);
        rt.setGravity(Gravity.CENTER);
        right.addView(rt);
        TextView rv = Ui.one(s.completed == 0 ? "—" : String.valueOf(Math.round(s.pointsAvg())), 28, Theme.TEXT, Theme.BOLD);
        rv.setGravity(Gravity.CENTER);
        rv.setPadding(0, dp(6), 0, dp(4));
        right.addView(rv);
        TextView ru = Ui.tv("puntos", 12.5f, Theme.TEXT2, Theme.REG);
        ru.setGravity(Gravity.CENTER);
        right.addView(ru);
        LinearLayout.LayoutParams rp = lp(0, MW, 1);
        rp.leftMargin = dp(8);
        rr.addView(right, rp);
        body.addView(rr, lp(MW, WC));

        // Bazas (modo completo)
        body.addView(Ui.section("Bazas (modo completo)"));
        if (s.fullRounds == 0) {
            body.addView(note("No disponible: solo se registran pedidas y hechas en las partidas en modo completo."));
        } else {
            body.addView(Ui.tileRow(
                    Ui.tile(0, 0, false, Ui.fmt1(s.bidAvg()), "Bazas pedidas (media)"),
                    Ui.tile(0, 0, false, Ui.fmt1(s.madeAvg()), "Bazas hechas (media)"),
                    Ui.tile(0, 0, false, Ui.pct(s.fullHitPct()), "Acierto en bazas pedidas")));
            body.addView(note("Sobre " + Ui.plural(s.fullRounds, "ronda", "rondas") + " en modo completo."));
        }

        // Subastadas
        body.addView(Ui.section("Subastadas"));
        body.addView(Ui.tileRow(
                Ui.tile(Ico.CROWN, Theme.GOLD, true, String.valueOf(s.aucPlayed), "Disputadas"),
                Ui.tile(Ico.TROPHY, Theme.GOLD, true, String.valueOf(s.aucWon), "Ganadas"),
                Ui.tile(Ico.TARGET, Theme.RED_HI, false, Ui.pct(s.aucPct()), "% adjudicación"),
                Ui.tile(Ico.CHECK, Theme.GREEN, false, String.valueOf(s.aucWonHit), "Aciertos" + (s.aucWon > 0 ? " (" + Ui.pct(s.aucHitPct()) + ")" : ""))));

        // Otros datos
        body.addView(Ui.section("Más datos"));
        LinearLayout more = Ui.card();
        Ui.pad(more, 14, 4, 14, 4);
        more.addView(Ui.kv("Partidas completas", String.valueOf(s.completed)));
        more.addView(Ui.divider());
        more.addView(Ui.kv("Finalizadas antes de tiempo", String.valueOf(s.games - s.completed)));
        more.addView(Ui.divider());
        more.addView(Ui.kv("Últimos puestos", String.valueOf(s.lasts)));
        more.addView(Ui.divider());
        more.addView(Ui.kv("Porcentaje de victorias", s.completed == 0 ? "—" : Ui.pct(100.0 * s.wins / s.completed)));
        body.addView(more, Ui.cardLp());

        // Histórico reciente
        body.addView(Ui.section("Histórico reciente"));
        if (s.recent.isEmpty()) body.addView(note(with.isEmpty() ? "Todavía no ha jugado ninguna partida cerrada."
                : "No hay partidas cerradas con esos jugadores."));
        int k = 0;
        for (Engine.Recent rc : s.recent) {
            if (k++ >= 8) break;
            body.addView(recentRow(rc));
        }

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    /** Elegir con qué jugadores filtrar: cuentan las partidas en las que estaban todos ellos. */
    private void pickWith(Player p) {
        List<Player> others = new ArrayList<>();
        for (Player o : st.playersSorted()) if (!o.id.equals(pid)) others.add(o);
        StatsScreen.multiPick(a, "Jugando con\u2026",
                "Cuentan solo las partidas de " + p.name + " en las que también jugaron todos los que elijas.",
                others, with, "Aplicar filtro", picked -> {
                    with.clear();
                    with.addAll(picked);
                    scrollY = 0;
                    a.refresh();
                    return true;
                });
    }

    private TextView line(String s, int color) {
        TextView t = Ui.one(s, 13, color, Theme.MED);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private TextView note(String s) {
        TextView t = Ui.tv(s, 12.5f, Theme.MUTED, Theme.REG);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(dp(2), dp(8), 0, 0);
        return t;
    }

    private View recentRow(Engine.Recent rc) {
        Game g = rc.game;
        LinearLayout row = Ui.h();
        row.setBackground(Ui.round(Theme.SURFACE, 12, Theme.BORDER, 1));
        Ui.pad(row, 12, 10, 14, 10);
        Ico ic;
        if (rc.pos == 0) ic = new Ico(Ico.PAUSE, Theme.MUTED);
        else ic = new Ico(Ico.TROPHY, rc.pos == 1 ? Theme.GOLD : rc.pos == 2 ? Theme.SILVER : rc.pos == 3 ? Theme.BRONZE : Theme.FAINT).rich();
        row.addView(Ui.icon(ic, 30));
        LinearLayout col = Ui.v();
        col.setPadding(dp(12), 0, 0, 0);
        String t;
        if (rc.pos == 0) t = "Sin victoria vs " + g.n() + " jugadores";
        else if (rc.won) t = (Engine.winners(g).size() > 1 ? "Ganó (compartida)" : "Ganó") + " vs " + g.n() + " jugadores";
        else t = rc.pos + "º puesto vs " + g.n() + " jugadores";
        col.addView(Ui.one(t, 15, Theme.TEXT, Theme.MED));
        TextView d = Ui.one(Ui.date(g.created), 12.5f, Theme.MUTED, Theme.REG);
        d.setPadding(0, dp(3), 0, 0);
        col.addView(d);
        row.addView(col, lp(0, WC, 1));
        TextView pts = Ui.one(Views.signed(rc.points), 16, rc.points >= 0 ? Theme.GREEN_HI : Theme.RED_HI, Theme.BOLD);
        row.addView(pts);
        Ui.press(row, 12, () -> a.push(new HistoryDetail(a, g.id)));
        row.setLayoutParams(lpm(MW, WC, 0, 0, 0, 8));
        return row;
    }
}
