package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;
import static com.kolopenco.poch.Ui.lpm;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Res;
import com.kolopenco.poch.Model.Revision;
import com.kolopenco.poch.Model.Round;

import java.util.ArrayList;
import java.util.List;

/** Historial de partidas con filtros Todas / Finalizadas / Incompletas. */
class HistoryScreen extends Screen {
    int filter;

    HistoryScreen(MainActivity a) { super(a); }

    static String title(Store st, Game g) {
        if (Model.ACTIVE.equals(g.status)) return "Partida en curso";
        if (Model.EARLY.equals(g.status)) return "Finalizada (sin victoria)";
        List<String> w = Engine.winners(g);
        if (w.size() == 1) return "Ganada por " + g.name(w.get(0));
        List<String> n = new ArrayList<>();
        for (String p : w) n.add(g.name(p));
        return "Empate (" + GameScreen.joinNames(n) + ")";
    }

    static String subtitle(Game g) {
        int saved = Engine.savedCount(g);
        if (Model.ACTIVE.equals(g.status)) {
            int cur = Math.min(Engine.current(g), g.rounds.size() - 1);
            return g.n() + " jugadores · Ronda " + (cur + 1) + " de " + Engine.regularCount(g);
        }
        if (Model.EARLY.equals(g.status))
            return g.n() + " jugadores · " + Engine.savedRegular(g) + " de " + Engine.regularCount(g) + " rondas";
        return g.n() + " jugadores · " + Ui.plural(saved, "ronda", "rondas");
    }

    /** Icono de la partida: trofeo dorado, plateado (empate), pausa o «en curso». */
    static View gameIcon(Game g) {
        FrameLayout f = new FrameLayout(Ui.a);
        Ico ic;
        int bg;
        if (Model.ACTIVE.equals(g.status)) {
            ic = new Ico(Ico.SYNC, 0xFFFFFFFF);
            bg = Theme.SURFACE3;
        } else if (Model.EARLY.equals(g.status)) {
            ic = new Ico(Ico.PAUSE, 0xFFFFFFFF);
            bg = Theme.SURFACE3;
        } else if (Engine.winners(g).size() > 1) {
            ic = new Ico(Ico.TROPHY, Theme.SILVER).rich();
            bg = 0;
        } else {
            ic = new Ico(Ico.TROPHY, Theme.GOLD).rich();
            bg = 0;
        }
        if (bg != 0) f.setBackground(Ui.oval(bg));
        float s = bg != 0 ? 22 : 36;
        f.addView(Ui.icon(ic, s), new FrameLayout.LayoutParams(dp(s), dp(s), Gravity.CENTER));
        f.setLayoutParams(lp(dp(44), dp(44)));
        return f;
    }

    static View gameRow(MainActivity a, Store st, Game g, Runnable click) {
        boolean live = Model.ACTIVE.equals(g.status);
        LinearLayout row = Ui.h();
        row.setBackground(live ? Ui.round(0xFF0F2242, 14, Theme.BLUE, 1.5f) : Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
        Ui.pad(row, 12, 11, 10, 11);
        row.addView(gameIcon(g));
        LinearLayout col = Ui.v();
        col.setPadding(dp(14), 0, dp(4), 0);
        if (live) {
            TextView tag = Ui.tag("En curso", Theme.GREEN, 0xFFFFFFFF);
            LinearLayout.LayoutParams tp = lp(WC, WC);
            tp.bottomMargin = dp(4);
            col.addView(tag, tp);
        }
        col.addView(Ui.one(live ? "Partida actual" : title(st, g), 16, Theme.TEXT, Theme.SEMI));
        TextView s = Ui.one(subtitle(g), 13, Theme.TEXT2, Theme.REG);
        s.setPadding(0, dp(3), 0, 0);
        col.addView(s);
        TextView d = Ui.one(Ui.dateTime(g.created), 12.5f, Theme.MUTED, Theme.REG);
        d.setPadding(0, dp(3), 0, 0);
        col.addView(d);
        row.addView(col, lp(0, WC, 1));
        row.addView(Ui.icon(Ico.CHEVRON, Theme.TEXT2, 20));
        Ui.press(row, 14, click);
        row.setLayoutParams(lpm(MW, WC, 0, 0, 0, 9));
        return row;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.header(Ui.icon(Ico.LIST, Theme.TEXT, 26), "Historial", null));
        root.addView(Ui.segmented(new String[]{"Todas", "Finalizadas", "Incompletas"}, filter, i -> {
            filter = i;
            scrollY = 0;
            a.refresh();
        }), lpm(MW, WC, 16, 0, 16, 10));

        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 2, 16, 16);
        int shown = 0;
        for (Game g : st.gamesByDate()) {
            boolean done = Model.COMPLETED.equals(g.status);
            if (filter == 1 && !done) continue;
            if (filter == 2 && done) continue;
            shown++;
            final String id = g.id;
            body.addView(gameRow(a, st, g, () -> {
                if (Model.ACTIVE.equals(g.status)) {
                    a.popToRoot(0);
                    a.selectTab(0);
                } else {
                    a.push(new HistoryDetail(a, id));
                }
            }));
        }
        if (shown == 0) {
            TextView e = Ui.tv(filter == 0 ? "Aún no hay partidas. Cuando terminéis la primera aparecerá aquí."
                    : filter == 1 ? "No hay partidas finalizadas." : "No hay partidas en curso ni terminadas antes de tiempo.",
                    14.5f, Theme.MUTED, Theme.REG);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(20), dp(40), dp(20), 0);
            body.addView(e);
        }
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }
}

/** Detalle de una partida: Resumen, Rondas y Configuración. */
class HistoryDetail extends Screen {
    final String id;
    int tabSel;
    int roundSel = -1;

    HistoryDetail(MainActivity a, String id) {
        super(a);
        this.id = id;
    }

    @Override View build() {
        Game g = st.games.get(id);
        LinearLayout root = Ui.v();
        if (g == null) {
            root.addView(Ui.centerHeader("Detalle de partida", a::pop, null));
            root.addView(Ui.tv("La partida ya no existe.", 15, Theme.MUTED, Theme.REG));
            return root;
        }
        FrameLayout share = Ui.iconButton(Ico.SHARE, Theme.TEXT, 21, () -> share(g));
        root.addView(Ui.centerHeader("Detalle de partida", a::pop, share));

        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);

        // Cabecera con trofeo
        LinearLayout hero = Ui.h();
        FrameLayout ic = new FrameLayout(a);
        ic.setBackground(Ui.oval(0xFF16264A));
        View gi = HistoryScreen.gameIcon(g);
        ic.addView(gi, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        hero.addView(ic, lp(dp(64), dp(64)));
        LinearLayout col = Ui.v();
        col.setPadding(dp(14), 0, 0, 0);
        col.addView(Ui.tv(HistoryScreen.title(st, g), 19, Theme.TEXT, Theme.BOLD));
        TextView s1 = Ui.tv(HistoryScreen.subtitle(g) + (Model.FULL.equals(g.mode) ? " · modo completo" : ""), 13.5f, Theme.TEXT2, Theme.REG);
        s1.setPadding(0, dp(4), 0, 0);
        col.addView(s1);
        TextView s2 = Ui.tv(Ui.dateTime(g.created), 13, Theme.MUTED, Theme.REG);
        s2.setPadding(0, dp(3), 0, 0);
        col.addView(s2);
        hero.addView(col, lp(0, WC, 1));
        body.addView(hero, lpm(MW, WC, 0, 4, 0, 14));

        body.addView(Ui.segmented(new String[]{"Resumen", "Rondas", "Configuración"}, tabSel, i -> {
            tabSel = i;
            a.refresh();
        }), lpm(MW, WC, 0, 0, 0, 14));

        if (tabSel == 0) summary(g, body);
        else if (tabSel == 1) rounds(g, body);
        else config(g, body);

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    // ------------------------------------------------------------------ resumen

    private void summary(Game g, LinearLayout body) {
        if (Model.EARLY.equals(g.status)) {
            LinearLayout n = Ui.h();
            n.setBackground(Ui.round(0x1AF2B41F, 12, Theme.alpha(Theme.GOLD, 0.4f), 1));
            Ui.pad(n, 12, 10, 12, 10);
            n.addView(Ui.icon(Ico.PAUSE, Theme.GOLD, 18));
            TextView t = Ui.tv("Finalizada anticipadamente · Sin victoria. Las rondas jugadas cuentan para las estadísticas.",
                    13, Theme.TEXT2, Theme.REG);
            t.setPadding(dp(10), 0, 0, 0);
            n.addView(t, lp(0, WC, 1));
            body.addView(n, lpm(MW, WC, 0, 0, 0, 12));
        }
        body.addView(Ui.section("Clasificación"));
        body.addView(FinalScreen.standings(a, g));

        // Subastas de la partida
        List<Round> auc = new ArrayList<>();
        for (Round r : g.rounds) if (r.saved && r.auction) auc.add(r);
        if (!auc.isEmpty()) {
            body.addView(Ui.section("Subastas"));
            LinearLayout c = Ui.card();
            for (String p : g.order) {
                int won = 0, hit = 0;
                for (Round r : auc) if (p.equals(r.winner)) {
                    won++;
                    Boolean h = Engine.hit(g, r, p);
                    if (h != null && h) hit++;
                }
                if (won == 0) continue;
                LinearLayout row = Ui.h();
                Ui.pad(row, 0, 6, 0, 6);
                row.addView(Ui.dot(g.color(p), 14));
                TextView t = Ui.one(g.name(p), 15, Theme.TEXT, Theme.MED);
                t.setPadding(dp(10), 0, 0, 0);
                row.addView(t, lp(0, WC, 1));
                row.addView(Ui.one(Ui.plural(won, "ganada", "ganadas") + " · " + hit + " acertada" + (hit == 1 ? "" : "s"),
                        13.5f, Theme.TEXT2, Theme.REG));
                c.addView(row);
            }
            if (c.getChildCount() == 0) c.addView(Ui.tv("Sin adjudicatario registrado.", 14, Theme.MUTED, Theme.REG));
            body.addView(c, Ui.cardLp());
        }

        body.addView(Ui.space(10));
        LinearLayout acts = Ui.h();
        acts.addView(Ui.iconTextButton(Ico.EDIT, "Revisar rondas", Theme.YELLOW,
                () -> a.push(new GameScreen(a, g, true, 0))), lp(0, WC, 1));
        LinearLayout del = Ui.iconTextButton(Ico.TRASH, "Eliminar", Theme.RED_HI, () -> delete(g));
        LinearLayout.LayoutParams p = lp(WC, WC);
        p.leftMargin = dp(10);
        acts.addView(del, p);
        body.addView(acts);
    }

    private void delete(Game g) {
        a.confirm("Eliminar partida", "Se borrará del historial y dejará de contar en las estadísticas. No se puede deshacer.",
                "Eliminar", true, () -> {
                    st.deleteGame(g);
                    a.pop();
                    a.toast("Partida eliminada");
                });
    }

    // ------------------------------------------------------------------ rondas

    private void rounds(Game g, LinearLayout body) {
        int saved = Engine.savedCount(g);
        if (saved == 0) {
            body.addView(Ui.tv("No hay rondas guardadas.", 14.5f, Theme.MUTED, Theme.REG));
            return;
        }
        if (roundSel < 0 || roundSel >= saved) roundSel = 0;

        LinearLayout legend = Ui.h();
        legend.addView(legendItem(Theme.SURFACE3, "Normal"));
        legend.addView(legendItem(Theme.BLUE, "Subastada"));
        legend.addView(legendItem(Theme.YELLOW, "Editada"));
        body.addView(legend, lpm(MW, WC, 0, 0, 0, 10));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout circles = Ui.h();
        Ui.pad(circles, 2, 4, 2, 4);
        for (int i = 0; i < saved; i++) {
            Round r = g.rounds.get(i);
            boolean ed = Engine.edited(r), sel = i == roundSel;
            int bg = ed ? Theme.YELLOW : r.auction ? Theme.BLUE : r.tiebreak ? Theme.GOLD : Theme.SURFACE3;
            TextView c = Ui.one(String.valueOf(r.cards), 14, ed || r.tiebreak ? 0xFF0A1222 : 0xFFFFFFFF, Theme.BOLD);
            c.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable d = Ui.oval(bg);
            if (sel) d.setStroke(dp(2.5f), 0xFFFFFFFF);
            c.setBackground(d);
            final int idx = i;
            Ui.press(c, 18, () -> {
                roundSel = idx;
                a.refresh();
            });
            LinearLayout.LayoutParams p = lp(dp(36), dp(36));
            p.rightMargin = dp(7);
            circles.addView(c, p);
        }
        hs.addView(circles);
        body.addView(hs, lp(MW, WC));
        hs.post(() -> hs.scrollTo(Math.max(0, dp(43) * roundSel - dp(120)), 0));

        Round r = g.rounds.get(roundSel);
        String head = r.tiebreak ? "Desempate · 1 carta" : "Ronda " + (roundSel + 1) + " · " + Ui.plural(r.cards, "carta", "cartas");
        TextView ht = Ui.tv(head, 16.5f, Theme.TEXT, Theme.SEMI);
        ht.setGravity(Gravity.CENTER);
        ht.setPadding(0, dp(12), 0, dp(4));
        body.addView(ht, lp(MW, WC));
        List<String> extras = new ArrayList<>();
        extras.add("Reparte " + g.name(Engine.dealer(g, roundSel)));
        if (r.mult != null) extras.add(Model.suitName(r.mult) + " ×" + g.rules.factor(r.mult));
        if (r.auction) extras.add(r.winner != null ? "Subasta: " + g.name(r.winner) : "Subastada");
        TextView et = Ui.tv(android.text.TextUtils.join(" · ", extras), 13, Theme.TEXT2, Theme.REG);
        et.setGravity(Gravity.CENTER);
        et.setPadding(0, 0, 0, dp(10));
        body.addView(et, lp(MW, WC));

        LinearLayout table = Ui.v();
        table.setBackground(Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
        Ui.pad(table, 12, 6, 12, 6);
        table.addView(tableRow(null, "Jugador", "Resultado", "Puntos", "Total", true, 0, false));
        java.util.Set<String> changed = Engine.editedPlayers(g, r);
        for (String p : g.order) {
            table.addView(Ui.divider());
            Integer pts = Engine.points(g, r, p);
            Res x = r.res.get(p);
            String res = !r.plays(p) ? "No juega" : GameScreen.readText(g, x).replace("Pedidas: ", "P ").replace(" · Hechas: ", " · H ");
            boolean yel = changed.contains(p);
            int col = yel ? Theme.YELLOW : pts == null ? Theme.MUTED : pts >= 0 ? Theme.GREEN_HI : Theme.RED_HI;
            table.addView(tableRow(p, g.name(p), res, pts == null ? "—" : Views.signed(pts),
                    Views.num(Engine.total(g, roundSel, p)), false, col, yel));
        }
        body.addView(table, lp(MW, WC));

        LinearLayout acts = Ui.h();
        acts.addView(Ui.iconTextButton(Ico.EDIT, "Editar ronda", Theme.YELLOW, () -> {
            GameScreen gs = new GameScreen(a, g, true, roundSel);
            gs.editing = true;
            gs.editSnap = r.snap();
            a.push(gs);
        }), lp(0, WC, 1));
        if (Engine.canUndo(r)) {
            LinearLayout.LayoutParams p = lp(0, WC, 1);
            p.leftMargin = dp(10);
            acts.addView(Ui.iconTextButton(Ico.UNDO, "Deshacer", Theme.TEXT2, () -> {
                Engine.undo(g, r, System.currentTimeMillis());
                st.saveGame(g);
                a.toast("Corrección deshecha");
                a.refresh();
            }), p);
        }
        body.addView(acts, lpm(MW, WC, 0, 12, 0, 0));

        if (!r.revs.isEmpty()) {
            body.addView(Ui.section("Correcciones de esta ronda"));
            LinearLayout c = Ui.card();
            for (int i = r.revs.size() - 1; i >= 0; i--) {
                Revision rv = r.revs.get(i);
                if (c.getChildCount() > 0) c.addView(Ui.divider());
                LinearLayout e = Ui.v();
                Ui.pad(e, 0, 8, 0, 8);
                String what = rv.isUndo ? "Deshecha una corrección" : rv.undone ? "Corrección (deshecha)" : "Corrección";
                e.addView(Ui.tv(what + " · " + Ui.dateTime(rv.at), 13.5f, rv.isUndo || rv.undone ? Theme.MUTED : Theme.YELLOW, Theme.SEMI));
                for (String line : diff(g, rv)) {
                    TextView t = Ui.tv(line, 13, Theme.TEXT2, Theme.REG);
                    t.setPadding(0, dp(4), 0, 0);
                    e.addView(t);
                }
                c.addView(e);
            }
            body.addView(c, Ui.cardLp());
        }
    }

    /** Líneas «Berto: Fallo −1 → Acierto 3» de una corrección. */
    static List<String> diff(Game g, Revision rv) {
        List<String> out = new ArrayList<>();
        for (String p : g.order) {
            Res a = rv.before.res.get(p), b = rv.after.res.get(p);
            boolean same = a == null ? (b == null || b.same(null)) : a.same(b);
            if (!same) out.add(g.name(p) + ": " + GameScreen.readText(g, a) + " → " + GameScreen.readText(g, b));
        }
        if (!eq(rv.before.winner, rv.after.winner))
            out.add("Subasta: " + (rv.before.winner == null ? "nadie" : g.name(rv.before.winner)) + " → "
                    + (rv.after.winner == null ? "nadie" : g.name(rv.after.winner)));
        if (!eq(rv.before.mult, rv.after.mult))
            out.add("Multiplicador: " + (rv.before.mult == null ? "ninguno" : Model.suitName(rv.before.mult)) + " → "
                    + (rv.after.mult == null ? "ninguno" : Model.suitName(rv.after.mult)));
        return out;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private View legendItem(int color, String text) {
        LinearLayout l = Ui.h();
        View d = new View(a);
        d.setBackground(Ui.oval(color));
        l.addView(d, lp(dp(12), dp(12)));
        TextView t = Ui.one(text, 13, Theme.TEXT2, Theme.MED);
        t.setPadding(dp(6), 0, dp(16), 0);
        l.addView(t);
        return l;
    }

    private View tableRow(String pid, String name, String res, String pts, String total, boolean head, int ptsColor, boolean yellow) {
        LinearLayout r = Ui.h();
        Ui.pad(r, 0, head ? 6 : 9, 0, head ? 6 : 9);
        int hc = Theme.MUTED;
        float fs = head ? 12 : 14;
        LinearLayout nm = Ui.h();
        if (pid != null) {
            Game g = st.games.get(id);
            nm.addView(Ui.dot(g.color(pid), 11));
            nm.addView(Ui.hspace(7));
        }
        nm.addView(Ui.one(name, fs, head ? hc : Theme.TEXT, head ? Theme.MED : Theme.MED), lp(0, WC, 1));
        r.addView(nm, lp(0, WC, 1.15f));
        TextView rt = Ui.one(res, head ? 12 : 13, head ? hc : Theme.TEXT2, Theme.REG);
        r.addView(rt, lp(0, WC, 1.35f));
        TextView pt = Ui.one(pts, fs, head ? hc : ptsColor, head ? Theme.MED : Theme.BOLD);
        pt.setGravity(Gravity.END);
        r.addView(pt, lp(0, WC, 0.65f));
        TextView tt = Ui.one(total, fs, head ? hc : Theme.TEXT, head ? Theme.MED : Theme.SEMI);
        tt.setGravity(Gravity.END);
        r.addView(tt, lp(0, WC, 0.7f));
        if (yellow) r.setBackground(Ui.round(0x14FFC83A, 8));
        return r;
    }

    // ------------------------------------------------------------------ configuración

    private void config(Game g, LinearLayout body) {
        TextView t = Ui.tv("Reglas con las que se jugó esta partida. Si cambias un perfil más adelante, esta partida no cambia.",
                13, Theme.MUTED, Theme.REG);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(dp(2), 0, 0, dp(10));
        body.addView(t);
        body.addView(RulesViewScreen.rulesCard(g));
    }

    private void share(Game g) {
        StringBuilder s = new StringBuilder();
        s.append("Poch! · ").append(HistoryScreen.title(st, g)).append('\n');
        s.append(Ui.dateTime(g.created)).append(" · ").append(HistoryScreen.subtitle(g)).append("\n\n");
        for (Engine.Rank r : Engine.standings(g)) {
            s.append(Model.EARLY.equals(g.status) ? "• " : r.pos + ". ")
                    .append(g.name(r.pid)).append(": ").append(Views.num(r.total)).append('\n');
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, s.toString());
        try {
            a.startActivity(Intent.createChooser(i, "Compartir resultado"));
        } catch (Exception e) {
            a.toast("No hay apps para compartir");
        }
    }
}

/** Reglas congeladas de una partida. */
class RulesViewScreen extends Screen {
    final Game g;

    RulesViewScreen(MainActivity a, Game g) {
        super(a);
        this.g = g;
    }

    static LinearLayout rulesCard(Game g) {
        LinearLayout c = Ui.card();
        Ui.pad(c, 14, 4, 14, 4);
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"Jugadores", String.valueOf(g.n())});
        lines.addAll(RulesText.lines(g.rules, g.mode, g.n()));
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) c.addView(Ui.divider());
            c.addView(Ui.kv(lines.get(i)[0], lines.get(i)[1]));
        }
        return c;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Configuración de la partida", a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);
        TextView sub = Ui.tv(g.n() + " jugadores · Modo " + (Model.FULL.equals(g.mode) ? "completo" : "sencillo"),
                14, Theme.TEXT2, Theme.REG);
        sub.setPadding(dp(2), 0, 0, dp(10));
        body.addView(sub);
        if (Model.ACTIVE.equals(g.status)) {
            // La regla de empate se puede cambiar durante la partida
            LinearLayout tc = Ui.card();
            tc.addView(Ui.settingRow("Si hay empate en cabeza", "Se puede cambiar en cualquier momento de la partida", null));
            tc.addView(Ui.segmented(new String[]{"Victoria compartida", "Desempate"}, Model.TIE_BREAK.equals(g.rules.tie) ? 1 : 0, i -> {
                Engine.setTieRule(g, i == 1 ? Model.TIE_BREAK : Model.TIE_SHARED);
                a.store.saveGame(g);
                a.toast(i == 1 ? "Si acabáis empatados, se desempata" : "Si acabáis empatados, se comparte la victoria");
                a.refresh();
            }), lpm(MW, WC, 0, 2, 0, 4));
            body.addView(tc, Ui.cardLp());
        }
        body.addView(rulesCard(g));
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }
}
