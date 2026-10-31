package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;
import static com.kolopenco.poch.Ui.lpm;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** Estadísticas globales: comparativa de todos los jugadores o de un grupo elegido. */
class StatsScreen extends Screen {
    @Override boolean liveRefresh() { return true; }
    int section;           // 0 General, 1 Rondas, 2 Subastadas
    int sortKey = -1;      // índice de columna; −1 = la primera de la sección
    /** 0 = todos, 1 = seleccionados, 2 = comparativa (solo partidas en las que jugaron juntos). */
    int mode;
    /** Grupo por el que se filtran las partidas (null = todas; NO_GROUP = las que no son de ningún grupo). */
    static String groupFilter;
    static final String NO_GROUP = "__none";

    StatsScreen(MainActivity a) {
        super(a);
        mode = a.store.statsSel.isEmpty() ? 0 : 1;
    }

    static final class Col {
        final String title;
        final ToDoubleFunction<Engine.PStats> value;
        final boolean ascending;
        final int kind; // 0 entero, 1 decimal, 2 porcentaje

        Col(String title, int kind, boolean ascending, ToDoubleFunction<Engine.PStats> value) {
            this.title = title;
            this.kind = kind;
            this.ascending = ascending;
            this.value = value;
        }

        String fmt(Engine.PStats s) {
            double v = value.applyAsDouble(s);
            if (Double.isNaN(v)) return "—";
            if (kind == 2) return Math.round(v) + "%";
            if (kind == 1) return Ui.fmt1(v);
            return String.valueOf(Math.round(v));
        }
    }

    List<Col> cols() {
        List<Col> c = new ArrayList<>();
        if (section == 0) {
            c.add(new Col("Vict.", 1, false, s -> s.wins));
            c.add(new Col("Part.", 0, false, s -> s.games));
            c.add(new Col("Pos.", 1, true, Engine.PStats::posAvg));
            c.add(new Col("Media", 0, false, Engine.PStats::pointsAvg));
            c.add(new Col("% Ac.", 2, false, Engine.PStats::hitPct));
        } else if (section == 1) {
            c.add(new Col("Rondas", 0, false, s -> s.rounds));
            c.add(new Col("Acert.", 0, false, s -> s.hits));
            c.add(new Col("Fall.", 0, true, s -> s.misses));
            c.add(new Col("% Ac.", 2, false, Engine.PStats::hitPct));
            c.add(new Col("% Baz.", 2, false, Engine.PStats::fullHitPct));
        } else {
            c.add(new Col("Disp.", 0, false, s -> s.aucPlayed));
            c.add(new Col("Adj.", 0, false, s -> s.aucWon));
            c.add(new Col("% Adj.", 2, false, Engine.PStats::aucPct));
            c.add(new Col("Acert.", 0, false, s -> s.aucWonHit));
            c.add(new Col("% Ac.", 2, false, Engine.PStats::aucHitPct));
        }
        return c;
    }

    private List<Player> chosen() {
        List<Player> out = new ArrayList<>();
        List<String> src = mode == 1 ? st.statsSel : mode == 2 ? st.statsCmp : null;
        java.util.Set<String> inPool = poolPlayers();
        for (Player p : st.playersSorted()) {
            if (src != null && !src.contains(p.id)) continue;
            if (inPool != null && !inPool.contains(p.id)) continue;
            out.add(p);
        }
        return out;
    }

    /** Con filtro de grupo: jugadores de sus partidas y miembros del grupo. Null = sin filtro. */
    private java.util.Set<String> poolPlayers() {
        if (groupFilter == null) return null;
        java.util.Set<String> in = new java.util.HashSet<>();
        for (Game g : pool()) in.addAll(g.order);
        Model.Group gr = st.group(groupFilter);
        if (gr != null) for (Player p : st.players) if (st.groupsOf(p.id).contains(gr)) in.add(p.id);
        return in;
    }

    /** Partidas cerradas, filtradas por grupo. */
    private List<Game> pool() {
        if (groupFilter != null && !NO_GROUP.equals(groupFilter) && st.group(groupFilter) == null) groupFilter = null;
        List<Game> out = new ArrayList<>();
        for (Game g : st.closedGames()) {
            if (groupFilter == null
                    || (NO_GROUP.equals(groupFilter) ? g.group == null || st.group(g.group) == null : groupFilter.equals(g.group)))
                out.add(g);
        }
        return out;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.header(Ui.icon(Ico.CHART, Theme.TEXT, 26), "Estadísticas", null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 0, 16, 24);

        body.addView(Ui.segmented(new String[]{"General", "Rondas", "Subastadas"}, section, i -> {
            section = i;
            sortKey = -1;
            a.refresh();
        }), lpm(MW, WC, 0, 0, 0, 12));

        // Filtro de grupo
        if (!st.groups.isEmpty()) {
            pool();
            body.addView(Ui.one("Filtrar por grupo", 14, Theme.TEXT2, Theme.MED), lpm(MW, WC, 2, 0, 0, 8));
            LinearLayout gr = Ui.h();
            gr.addView(Ui.chip("Todas las partidas", groupFilter == null, () -> { groupFilter = null; a.refresh(); }));
            for (Model.Group g : st.groups) {
                gr.addView(Ui.hspace(8));
                gr.addView(Ui.chip(g.name, g.id.equals(groupFilter), () -> { groupFilter = g.id; a.refresh(); }));
            }
            gr.addView(Ui.hspace(8));
            gr.addView(Ui.chip("Sin grupo", NO_GROUP.equals(groupFilter), () -> { groupFilter = NO_GROUP; a.refresh(); }));
            android.widget.HorizontalScrollView gs = new android.widget.HorizontalScrollView(a);
            gs.setHorizontalScrollBarEnabled(false);
            gs.addView(gr);
            body.addView(gs, lpm(MW, WC, 0, 0, 0, 14));
        }

        // Filtro de jugadores
        body.addView(Ui.one("Filtrar jugadores", 14, Theme.TEXT2, Theme.MED), lpm(MW, WC, 2, 0, 0, 8));
        LinearLayout fr = Ui.h();
        java.util.Set<String> pp = poolPlayers();
        int all = 0;
        for (Player p : st.players) if (pp == null || pp.contains(p.id)) all++;
        fr.addView(Ui.chip("Todos (" + all + ")", mode == 0, () -> {
            mode = 0;
            a.refresh();
        }));
        fr.addView(Ui.hspace(8));
        int nsel = known(st.statsSel);
        fr.addView(Ui.chip(nsel == 0 ? "Seleccionar" : nsel + (nsel == 1 ? " seleccionado" : " seleccionados"),
                mode == 1, () -> pickPlayers(false)));
        fr.addView(Ui.hspace(8));
        int ncmp = known(st.statsCmp);
        boolean cmpOn = mode == 2;
        LinearLayout cmp = Ui.h();
        cmp.setBackground(cmpOn ? Ui.grad(Theme.BLUE_HI, Theme.BLUE_LO, 10) : Ui.round(Theme.SURFACE, 10, Theme.BORDER2, 1));
        Ui.pad(cmp, 10, 0, 12, 0);
        cmp.setMinimumHeight(dp(36));
        cmp.addView(Ui.icon(Ico.PEOPLE, cmpOn ? Theme.TEXT : Theme.TEXT2, 17));
        TextView ct = Ui.one("Comparativa" + (cmpOn ? " (" + ncmp + ")" : ""), 13.5f,
                cmpOn ? Theme.TEXT : Theme.TEXT2, cmpOn ? Theme.SEMI : Theme.MED);
        ct.setPadding(dp(6), 0, 0, 0);
        cmp.addView(ct);
        Ui.press(cmp, 10, () -> pickPlayers(true));
        fr.addView(cmp);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(fr);
        body.addView(hs, lpm(MW, WC, 0, 0, 0, 14));

        List<Player> ps = chosen();
        List<String> ids = new ArrayList<>();
        for (Player p : ps) ids.add(p.id);
        List<Game> pool = pool();
        if (groupFilter != null) {
            Model.Group fg = st.group(groupFilter);
            TextView gnote = Ui.tv(Ui.plural(pool.size(), "partida", "partidas") + (fg != null ? " del grupo «" + fg.name + "»" : " sin grupo"), 13, Theme.BLUE_TEXT, Theme.MED);
            gnote.setPadding(dp(2), 0, 0, dp(8));
            body.addView(gnote);
        }
        if (mode == 2) {
            pool = Engine.withAll(pool, ids);
            body.addView(togetherNote(ps, pool.size()), lpm(MW, WC, 0, 0, 0, 4));
        }
        Map<String, Engine.PStats> stats = Engine.statsAll(pool, ids);

        if (ps.isEmpty()) {
            TextView e = Ui.tv(st.players.isEmpty() ? "Aún no hay jugadores." : "No hay jugadores seleccionados.",
                    14.5f, Theme.MUTED, Theme.REG);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(30), 0, 0);
            body.addView(e);
        } else {
            List<Col> cols = cols();
            int key = sortKey < 0 ? 0 : sortKey;
            Col sc = cols.get(key);
            List<Player> sorted = new ArrayList<>(ps);
            Collections.sort(sorted, (x, y) -> {
                double vx = sc.value.applyAsDouble(stats.get(x.id)), vy = sc.value.applyAsDouble(stats.get(y.id));
                boolean nx = Double.isNaN(vx), ny = Double.isNaN(vy);
                if (nx || ny) return nx == ny ? x.name.compareToIgnoreCase(y.name) : nx ? 1 : -1;
                int c = sc.ascending ? Double.compare(vx, vy) : Double.compare(vy, vx);
                return c != 0 ? c : x.name.compareToIgnoreCase(y.name);
            });

            body.addView(Ui.section(section == 0 ? "Comparativa general" : section == 1 ? "Comparativa de rondas" : "Comparativa de subastas"));
            LinearLayout table = Ui.v();
            table.setBackground(Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
            Ui.pad(table, 10, 6, 10, 6);
            table.addView(headRow(cols, key));
            int rank = 0;
            double prev = Double.NaN;
            for (int i = 0; i < sorted.size(); i++) {
                Player p = sorted.get(i);
                Engine.PStats s = stats.get(p.id);
                double v = sc.value.applyAsDouble(s);
                if (i == 0 || Double.isNaN(v) || v != prev) rank = i + 1;
                prev = v;
                table.addView(Ui.divider());
                table.addView(dataRow(p, s, cols, key, rank));
            }
            body.addView(table, lp(MW, WC));
            TextView note = Ui.tv(section == 0
                    ? "Toca una columna para ordenar. Media y posición sobre partidas completas; % de rondas acertadas sobre todas las rondas jugadas."
                    : section == 1 ? "% Baz.: acierto exacto en las rondas en modo completo (pedidas = hechas)."
                    : "Disp.: subastas jugadas. Adj.: subastas que se llevó. Acert.: aciertos en las que se llevó.",
                    12, Theme.MUTED, Theme.REG);
            note.setLineSpacing(dp(2), 1);
            note.setPadding(dp(2), dp(8), 0, 0);
            body.addView(note);

            charts(body, sorted, stats);
        }

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    private static final float NAME_W = 1.6f;

    private View headRow(List<Col> cols, int key) {
        LinearLayout r = Ui.h();
        Ui.pad(r, 0, 6, 0, 6);
        TextView h = Ui.one("#", 12, Theme.MUTED, Theme.MED);
        h.setGravity(Gravity.CENTER);
        r.addView(h, lp(dp(26), WC));
        r.addView(Ui.one("Jugador", 12, Theme.MUTED, Theme.MED), lpm(0, WC, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) r.getChildAt(1).getLayoutParams()).weight = NAME_W;
        for (int i = 0; i < cols.size(); i++) {
            final int idx = i;
            boolean on = i == key;
            TextView t = Ui.one(cols.get(i).title + (on ? (cols.get(i).ascending ? " ▲" : " ▼") : ""),
                    11.5f, on ? Theme.BLUE_TEXT : Theme.MUTED, on ? Theme.SEMI : Theme.MED);
            t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            t.setMinHeight(dp(28));
            Ui.press(t, 6, () -> {
                sortKey = idx;
                a.refresh();
            });
            r.addView(t, lp(0, WC, 1));
        }
        return r;
    }

    private View dataRow(Player p, Engine.PStats s, List<Col> cols, int key, int rank) {
        LinearLayout r = Ui.h();
        Ui.pad(r, 0, 8, 0, 8);
        Views.Badge b = new Views.Badge(a);
        b.text = String.valueOf(rank);
        b.color = rank == 1 ? 0xFF1F9D55 : Theme.SURFACE3;
        LinearLayout.LayoutParams bp = lp(dp(22), dp(22));
        bp.rightMargin = dp(4);
        r.addView(b, bp);
        LinearLayout nm = Ui.h();
        nm.addView(Ui.dot(p.color, 11));
        TextView n = Ui.one(p.name, 13.5f, Theme.TEXT, Theme.MED);
        n.setPadding(dp(6), 0, 0, 0);
        nm.addView(n, lp(0, WC, 1));
        LinearLayout.LayoutParams np = lp(0, WC, NAME_W);
        np.leftMargin = dp(6);
        r.addView(nm, np);
        for (int i = 0; i < cols.size(); i++) {
            TextView t = Ui.one(cols.get(i).fmt(s), 13.5f, i == key ? Theme.TEXT : Theme.TEXT2, i == key ? Theme.SEMI : Theme.REG);
            t.setGravity(Gravity.END);
            r.addView(t, lp(0, WC, 1));
        }
        Ui.press(r, 8, () -> a.push(new PlayerDetail(a, p.id)));
        return r;
    }

    private void charts(LinearLayout body, List<Player> ps, Map<String, Engine.PStats> stats) {
        if (section == 0) {
            body.addView(Ui.section("Puntuación media por partida"));
            LinearLayout c = Ui.card();
            double max = 1;
            for (Player p : ps) {
                double v = stats.get(p.id).pointsAvg();
                if (!Double.isNaN(v)) max = Math.max(max, v);
            }
            List<Player> byAvg = new ArrayList<>(ps);
            Collections.sort(byAvg, (x, y) -> Double.compare(nz(stats.get(y.id).pointsAvg()), nz(stats.get(x.id).pointsAvg())));
            for (Player p : byAvg) {
                double v = stats.get(p.id).pointsAvg();
                c.addView(bar(p, Double.isNaN(v) ? 0 : (float) (v / max), Double.isNaN(v) ? "—" : String.valueOf(Math.round(v))));
            }
            body.addView(c, Ui.cardLp());
        }
        if (section <= 1) {
            body.addView(Ui.section("Rondas acertadas vs falladas"));
            LinearLayout c = Ui.card();
            LinearLayout leg = Ui.h();
            leg.addView(Ui.flex());
            leg.addView(legend(Theme.GREEN, "Aciertos"));
            leg.addView(legend(Theme.RED, "Fallos"));
            c.addView(leg, lpm(MW, WC, 0, 0, 0, 6));
            for (Player p : sortedBy(ps, stats, Engine.PStats::hitPct)) {
                Views.SplitBar sb = new Views.SplitBar(a);
                sb.color = p.color;
                sb.name = p.name;
                sb.hitPct = (float) stats.get(p.id).hitPct();
                c.addView(sb, lp(MW, dp(28)));
            }
            body.addView(c, Ui.cardLp());
        }
        if (section == 1) {
            body.addView(Ui.section("Bazas: media de pedidas y hechas"));
            LinearLayout c = Ui.card();
            boolean any = false;
            for (Player p : ps) {
                Engine.PStats s = stats.get(p.id);
                if (s.fullRounds == 0) continue;
                any = true;
                c.addView(Ui.kv(p.name, "pide " + Ui.fmt1(s.bidAvg()) + " · hace " + Ui.fmt1(s.madeAvg())
                        + " · " + Ui.plural(s.fullRounds, "ronda", "rondas")));
            }
            if (!any) c.addView(Ui.tv("No disponible: no hay partidas en modo completo.", 13.5f, Theme.MUTED, Theme.REG));
            body.addView(c, Ui.cardLp());
        }
        if (section == 2) {
            body.addView(Ui.section("Porcentaje de adjudicación"));
            LinearLayout c = Ui.card();
            for (Player p : sortedBy(ps, stats, Engine.PStats::aucPct)) {
                double v = stats.get(p.id).aucPct();
                c.addView(bar(p, Double.isNaN(v) ? 0 : (float) (v / 100), Ui.pct(v)));
            }
            body.addView(c, Ui.cardLp());
            body.addView(Ui.section("Acierto tras llevarse la subasta"));
            LinearLayout c2 = Ui.card();
            for (Player p : sortedBy(ps, stats, Engine.PStats::aucHitPct)) {
                Views.SplitBar sb = new Views.SplitBar(a);
                sb.color = p.color;
                sb.name = p.name;
                sb.hitPct = (float) stats.get(p.id).aucHitPct();
                c2.addView(sb, lp(MW, dp(28)));
            }
            body.addView(c2, Ui.cardLp());
        }
    }

    private static double nz(double v) { return Double.isNaN(v) ? -1e9 : v; }

    private List<Player> sortedBy(List<Player> ps, Map<String, Engine.PStats> stats, ToDoubleFunction<Engine.PStats> f) {
        List<Player> out = new ArrayList<>(ps);
        Collections.sort(out, (x, y) -> Double.compare(nz(f.applyAsDouble(stats.get(y.id))), nz(f.applyAsDouble(stats.get(x.id)))));
        return out;
    }

    private View bar(Player p, float frac, String value) {
        Views.BarRow br = new Views.BarRow(a);
        br.color = p.color;
        br.name = p.name;
        br.frac = frac;
        br.value = value;
        br.setLayoutParams(lp(MW, dp(28)));
        return br;
    }

    private View legend(int color, String text) {
        LinearLayout l = Ui.h();
        View d = new View(a);
        d.setBackground(Ui.oval(color));
        l.addView(d, lp(dp(10), dp(10)));
        TextView t = Ui.one(text, 12, Theme.TEXT2, Theme.MED);
        t.setPadding(dp(5), 0, dp(10), 0);
        l.addView(t);
        return l;
    }

    private int known(List<String> ids) {
        int n = 0;
        for (String id : ids) if (st.player(id) != null) n++;
        return n;
    }

    /** Aviso de la comparativa: con quién y cuántas partidas juntos. */
    private View togetherNote(List<Player> ps, int games) {
        LinearLayout box = Ui.h();
        box.setBackground(Ui.round(Theme.BLUE_BG, 12, Theme.alpha(Theme.BLUE, 0.7f), 1));
        Ui.pad(box, 12, 10, 12, 10);
        box.addView(Ui.icon(Ico.PEOPLE, Theme.BLUE_TEXT, 20));
        List<String> names = new ArrayList<>();
        for (Player p : ps) names.add(p.name);
        TextView t = Ui.tv(games == 0
                        ? GameScreen.joinNames(names) + " aún no han jugado ninguna partida juntos."
                        : "Solo partidas en las que jugaron juntos " + GameScreen.joinNames(names) + ": "
                        + Ui.plural(games, "partida", "partidas") + ".",
                13.5f, Theme.TEXT, Theme.MED);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(dp(10), 0, 0, 0);
        box.addView(t, lp(0, WC, 1));
        return box;
    }

    /** Elegir jugadores: para ver solo a unos (false) o para la comparativa de partidas juntos (true). */
    private void pickPlayers(boolean cmp) {
        List<String> preset = cmp ? st.statsCmp : st.statsSel;
        multiPick(a, cmp ? "Comparativa" : "Elegir jugadores",
                cmp ? "Elige 2 o más jugadores: solo cuentan las partidas en las que jugaron todos ellos (puede haber más jugadores en la partida)."
                        : "Muestra las estadísticas de los jugadores que elijas, en todas sus partidas.",
                st.playersSorted(), preset, cmp ? "Comparar" : "Ver estadísticas", picked -> {
                    if (cmp && picked.size() == 1) {
                        a.toast("Elige al menos 2 jugadores");
                        return false;
                    }
                    List<String> target = cmp ? st.statsCmp : st.statsSel;
                    target.clear();
                    target.addAll(picked);
                    if (cmp) st.saveStatsCmp(); else st.saveStatsSel();
                    mode = picked.isEmpty() ? 0 : cmp ? 2 : 1;
                    a.refresh();
                    return true;
                });
    }

    /**
     * Hoja con casillas para elegir varios jugadores. {@code done} recibe los elegidos en el orden de la lista
     * y devuelve true para cerrar la hoja.
     */
    static void multiPick(MainActivity a, String title, String note, List<Player> players, java.util.Collection<String> preset,
                          String button, java.util.function.Predicate<List<String>> done) {
        LinearLayout body = Ui.v();
        if (note != null) {
            TextView n = Ui.tv(note, 13, Theme.TEXT2, Theme.REG);
            n.setLineSpacing(dp(2), 1);
            n.setPadding(dp(2), 0, 0, dp(10));
            body.addView(n);
        }
        Set<String> chosen = new HashSet<>(preset);
        List<Runnable> refreshers = new ArrayList<>();
        LinearLayout quick = Ui.h();
        quick.addView(Ui.chip("Todos", false, () -> {
            for (Player p : players) chosen.add(p.id);
            for (Runnable r : refreshers) r.run();
        }));
        quick.addView(Ui.hspace(8));
        quick.addView(Ui.chip("Ninguno", false, () -> {
            chosen.clear();
            for (Runnable r : refreshers) r.run();
        }));
        body.addView(quick, lpm(MW, WC, 0, 0, 0, 10));
        for (Player p : players) {
            LinearLayout row = Ui.h();
            Ui.pad(row, 12, 8, 12, 8);
            row.setMinimumHeight(dp(52));
            row.addView(Ui.dot(p.color, 24));
            TextView t = Ui.one(p.name, 15.5f, Theme.TEXT, Theme.MED);
            t.setPadding(dp(12), 0, 0, 0);
            row.addView(t, lp(0, WC, 1));
            FrameLayout box = new FrameLayout(a);
            row.addView(box, lp(dp(24), dp(24)));
            Runnable paint = () -> {
                boolean on = chosen.contains(p.id);
                box.removeAllViews();
                box.setBackground(on ? Ui.round(Theme.BLUE, 7) : Ui.round(0, 7, Theme.MUTED, 1.6f));
                if (on) box.addView(Ui.icon(Ico.CHECK, 0xFFFFFFFF, 18), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
                row.setBackground(Ui.round(on ? 0xFF0F2242 : Theme.SURFACE2, 12));
            };
            paint.run();
            refreshers.add(paint);
            Ui.press(row, 12, () -> {
                if (!chosen.remove(p.id)) chosen.add(p.id);
                paint.run();
            });
            body.addView(row, lpm(MW, WC, 0, 0, 0, 6));
        }
        if (players.isEmpty()) body.addView(Ui.tv("No hay más jugadores.", 14, Theme.MUTED, Theme.REG));
        body.addView(Ui.space(10));
        body.addView(Ui.primary(button, () -> {
            List<String> picked = new ArrayList<>();
            for (Player p : players) if (chosen.contains(p.id)) picked.add(p.id);
            if (done.test(picked)) a.closeOverlay();
        }), lp(MW, WC));
        a.sheet(title, body);
    }
}
