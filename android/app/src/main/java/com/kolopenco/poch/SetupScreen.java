package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;
import static com.kolopenco.poch.Ui.lpm;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Player;
import com.kolopenco.poch.Model.Rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Asistente de nueva partida: jugadores → reglas → resumen. */
class SetupScreen extends Screen {
    int step;
    final List<String> sel = new ArrayList<>();
    Rules rules;
    String profileId;
    boolean customized;
    String mode = Model.SIMPLE;

    private LinearLayout selBox;
    private TextView countNote;

    SetupScreen(MainActivity a) {
        super(a);
        Game last = st.lastGame();
        if (last != null) {
            for (String p : last.order) if (st.player(p) != null) sel.add(p);
            mode = last.mode;
        }
        Rules def = st.defaultRules();
        rules = def.copy();
        profileId = def.id;
    }

    @Override boolean onBack() {
        if (step > 0) {
            step--;
            scrollY = 0;
            a.refresh();
            return true;
        }
        return false;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Nueva partida", () -> {
            if (!onBack()) a.pop();
        }, null));
        root.addView(stepper(), lpm(MW, WC, 16, 0, 16, 6));

        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 16);
        if (step == 0) stepPlayers(body);
        else if (step == 1) stepRules(body);
        else stepSummary(body);
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));

        LinearLayout foot = Ui.v();
        Ui.pad(foot, 16, 6, 16, 12);
        String problem = null;
        if (step == 0 && sel.size() < Engine.MIN_PLAYERS) problem = "Elige al menos 2 jugadores";
        if (problem != null) {
            TextView h = Ui.one(problem, 13, Theme.TEXT2, Theme.MED);
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, 0, 0, dp(8));
            foot.addView(h);
        }
        TextView next = Ui.primary(step < 2 ? "Siguiente" : "Empezar partida", () -> {
            if (step < 2) {
                step++;
                scrollY = 0;
                a.refresh();
            } else {
                start();
            }
        });
        Ui.setPrimaryEnabled(next, problem == null);
        foot.addView(next, lp(MW, WC));
        root.addView(foot);
        return root;
    }

    /** Indicador de pasos 1-2-3. */
    private View stepper() {
        LinearLayout row = Ui.h();
        String[] labels = {"Jugadores", "Reglas", "Resumen"};
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                View line = new View(a);
                line.setBackgroundColor(i <= step ? Theme.BLUE : Theme.BORDER2);
                LinearLayout.LayoutParams lpLine = lp(0, dp(2), 0.4f);
                lpLine.setMargins(dp(6), 0, dp(6), 0);
                row.addView(line, lpLine);
            }
            LinearLayout it = Ui.h();
            boolean done = i < step, cur = i == step;
            FrameLayout c = new FrameLayout(a);
            c.setBackground(Ui.oval(cur ? Theme.BLUE : done ? Theme.GREEN : Theme.SURFACE3));
            if (done) {
                c.addView(Ui.icon(Ico.CHECK, 0xFFFFFFFF, 16), new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
            } else {
                TextView n = Ui.one(String.valueOf(i + 1), 13, cur ? 0xFFFFFFFF : Theme.TEXT2, Theme.BOLD);
                c.addView(n, new FrameLayout.LayoutParams(WC, WC, Gravity.CENTER));
            }
            it.addView(c, lp(dp(26), dp(26)));
            TextView t = Ui.one(labels[i], 13.5f, cur ? Theme.TEXT : Theme.MUTED, cur ? Theme.SEMI : Theme.MED);
            t.setPadding(dp(7), 0, 0, 0);
            it.addView(t);
            final int target = i;
            if (i < step) Ui.press(it, 13, () -> {
                step = target;
                a.refresh();
            });
            row.addView(it);
        }
        return row;
    }

    // ------------------------------------------------------------------ paso 1: jugadores

    private void stepPlayers(LinearLayout body) {
        TextView title = Ui.tv("Orden de reparto", 24, Theme.TEXT, Theme.BOLD);
        title.setGravity(Gravity.CENTER);
        body.addView(title, lpm(MW, WC, 0, 14, 0, 8));
        TextView note = Ui.tv("Arrastra ≡ para cambiar el orden.\nSe reparte según estos números: el 1 reparte la primera ronda, luego el 2, y así sucesivamente.",
                14, Theme.TEXT2, Theme.REG);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(3), 1);
        note.setPadding(dp(8), 0, dp(8), dp(16));
        body.addView(note, lp(MW, WC));
        selBox = Ui.v();
        body.addView(selBox, lp(MW, WC));
        countNote = Ui.tv("", 13, Theme.TEXT2, Theme.MED);
        countNote.setPadding(dp(2), dp(4), 0, 0);
        body.addView(countNote);
        renderSel();

        body.addView(Ui.section("Añadir jugadores"));
        Flow flow = new Flow(a, dp(8), dp(8));
        for (Player p : st.playersSorted()) {
            if (sel.contains(p.id)) continue;
            flow.addView(playerChip(p));
        }
        LinearLayout add = Ui.h();
        add.setBackground(Ui.round(Theme.BLUE_BG, 18, Theme.alpha(Theme.BLUE, 0.8f), 1.2f));
        Ui.pad(add, 10, 0, 14, 0);
        add.setMinimumHeight(dp(38));
        add.addView(Ui.icon(Ico.PLUS, Theme.BLUE_TEXT, 18));
        TextView at = Ui.one("Nuevo jugador", 14, Theme.BLUE_TEXT, Theme.SEMI);
        at.setPadding(dp(6), 0, 0, 0);
        add.addView(at);
        Ui.press(add, 18, this::newPlayer);
        flow.addView(add);
        body.addView(flow, lp(MW, WC));
    }

    private View playerChip(Player p) {
        LinearLayout c = Ui.h();
        c.setBackground(Ui.round(Theme.SURFACE, 18, Theme.BORDER2, 1));
        Ui.pad(c, 8, 0, 14, 0);
        c.setMinimumHeight(dp(38));
        c.addView(Ui.dot(p.color, 18));
        TextView t = Ui.one(p.name, 14.5f, Theme.TEXT, Theme.MED);
        t.setPadding(dp(8), 0, 0, 0);
        c.addView(t);
        Ui.press(c, 18, () -> {
            if (sel.size() >= Engine.MAX_PLAYERS) {
                a.toast("Máximo " + Engine.MAX_PLAYERS + " jugadores");
                return;
            }
            sel.add(p.id);
            a.refresh();
        });
        return c;
    }

    private void newPlayer() {
        a.prompt("Nuevo jugador", "Nombre", "", "Añadir", name -> {
            if (name.isEmpty()) return;
            if (st.nameTaken(name, null)) {
                a.toast("Ya hay un jugador llamado " + name);
                return;
            }
            Player p = st.addPlayer(name, st.nextColor());
            if (sel.size() < Engine.MAX_PLAYERS) sel.add(p.id);
            a.refresh();
        });
    }

    private void renderSel() {
        selBox.removeAllViews();
        if (sel.isEmpty()) {
            TextView e = Ui.tv("Todavía no hay jugadores en la partida. Añádelos abajo.", 14, Theme.MUTED, Theme.REG);
            e.setGravity(Gravity.CENTER);
            e.setBackground(Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
            Ui.pad(e, 16, 20, 16, 20);
            selBox.addView(e, lp(MW, WC));
        }
        for (int i = 0; i < sel.size(); i++) selBox.addView(selRow(i), lpm(MW, dp(60), 0, 0, 0, 8));
        int n = sel.size();
        if (n >= Engine.MIN_PLAYERS) {
            List<Integer> seq = Engine.sequence(rules, n);
            int max = 0;
            for (int c : seq) max = Math.max(max, c);
            countNote.setText(n + " jugadores · máximo " + Ui.plural(max, "carta", "cartas") + " · " + seq.size() + " rondas");
        } else {
            countNote.setText("");
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View selRow(int i) {
        String pid = sel.get(i);
        Player p = st.player(pid);
        LinearLayout row = Ui.h();
        row.setBackground(Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
        Ui.pad(row, 4, 0, 4, 0);
        FrameLayout handle = new FrameLayout(a);
        handle.addView(Ui.icon(Ico.DRAG, Theme.MUTED, 22), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        row.addView(handle, lp(dp(44), MW));
        TextView num = Ui.one(String.valueOf(i + 1), 13, Theme.MUTED, Theme.SEMI);
        num.setMinWidth(dp(16));
        row.addView(num);
        row.addView(Ui.hspace(6));
        row.addView(Ui.dot(p == null ? Theme.MUTED : p.color, 26));
        TextView name = Ui.one(p == null ? "?" : p.name, 16, Theme.TEXT, Theme.SEMI);
        name.setPadding(dp(12), 0, dp(6), 0);
        row.addView(name, lp(0, WC, 1));
        if (i == 0) {
            LinearLayout tag = Ui.h();
            tag.setBackground(Ui.round(Theme.SURFACE3, 8));
            Ui.pad(tag, 6, 3, 8, 3);
            tag.addView(Ui.icon(Ico.DECK, 0, 16));
            TextView tt = Ui.one("Reparte", 12, Theme.TEXT, Theme.SEMI);
            tt.setPadding(dp(4), 0, 0, 0);
            tag.addView(tt);
            row.addView(tag);
        }
        row.addView(Ui.iconButton(Ico.CLOSE, Theme.MUTED, 18, () -> {
            sel.remove(pid);
            a.refresh();
        }));

        final float[] start = new float[1];
        final int[] target = new int[1];
        handle.setOnTouchListener((v, e) -> {
            int from = selBox.indexOfChild(row);
            int n = selBox.getChildCount();
            float slot = row.getHeight() + dp(8);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawY();
                    target[0] = from;
                    row.setTranslationZ(dp(10));
                    row.setScaleX(1.02f);
                    row.setScaleY(1.02f);
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getRawY() - start[0];
                    float minDy = -from * slot, maxDy = (n - 1 - from) * slot;
                    dy = Math.max(minDy, Math.min(maxDy, dy));
                    row.setTranslationY(dy);
                    int t = Math.max(0, Math.min(n - 1, from + Math.round(dy / slot)));
                    if (t != target[0]) {
                        target[0] = t;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    }
                    for (int j = 0; j < n; j++) {
                        if (j == from) continue;
                        View o = selBox.getChildAt(j);
                        float shift = 0;
                        if (from < t && j > from && j <= t) shift = -slot;
                        if (t < from && j >= t && j < from) shift = slot;
                        if (o.getTranslationY() != shift) o.animate().translationY(shift).setDuration(120).start();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    for (int j = 0; j < n; j++) {
                        View o = selBox.getChildAt(j);
                        o.animate().cancel();
                        o.setTranslationY(0);
                    }
                    row.setTranslationZ(0);
                    row.setScaleX(1);
                    row.setScaleY(1);
                    int t = target[0];
                    if (t != from && from >= 0) {
                        String id = sel.remove(from);
                        sel.add(t, id);
                    }
                    // No se rehace la lista dentro del propio toque: Android aún está
                    // entregando el evento a esta fila y la app se cerraba al soltar.
                    selBox.post(this::renderSel);
                    return true;
                }
            }
            return false;
        });
        return row;
    }

    // ------------------------------------------------------------------ paso 2: reglas

    private void stepRules(LinearLayout body) {
        body.addView(Ui.section("Perfil de reglas"));
        for (Rules r : st.profiles) {
            boolean on = !customized && r.id.equals(profileId);
            body.addView(option(r.name + (r.id.equals(st.defaultProfile) ? "  ★" : ""), RulesText.summary(r), on, () -> {
                rules = r.copy();
                profileId = r.id;
                customized = false;
                a.refresh();
            }));
        }
        if (customized) {
            body.addView(option("Personalizadas para esta partida", RulesText.summary(rules), true, () -> { }));
        }
        LinearLayout custom = Ui.iconTextButton(Ico.SLIDERS, "Personalizar reglas", Theme.BLUE_TEXT, () ->
                a.push(new RulesEditor(a, rules.copy(), false, r -> {
                    rules = r;
                    customized = true;
                })));
        body.addView(custom, lpm(MW, WC, 0, 2, 0, 0));

        body.addView(Ui.section("Modo de anotación"));
        body.addView(option("Sencillo", "Un número por jugador: cuántas hizo si acierta (0 también es acierto) o por cuántas falló. Lo más rápido.",
                Model.SIMPLE.equals(mode), () -> {
                    mode = Model.SIMPLE;
                    a.refresh();
                }));
        body.addView(option("Completo", "Pedidas y hechas de cada jugador. Comprueba las sumas y da estadísticas de bazas.",
                Model.FULL.equals(mode), () -> {
                    mode = Model.FULL;
                    a.refresh();
                }));
        LinearLayout warn = Ui.h();
        warn.setBackground(Ui.round(0x1AF2B41F, 12, Theme.alpha(Theme.GOLD, 0.4f), 1));
        Ui.pad(warn, 12, 10, 12, 10);
        warn.addView(Ui.icon(Ico.INFO, Theme.GOLD, 20));
        TextView wt = Ui.tv("El modo no se puede cambiar una vez empezada la partida.", 13, Theme.TEXT2, Theme.REG);
        wt.setPadding(dp(10), 0, 0, 0);
        warn.addView(wt, lp(0, WC, 1));
        body.addView(warn, lpm(MW, WC, 0, 4, 0, 0));
    }

    /** Tarjeta de opción con círculo de selección. */
    static LinearLayout option(String title, String sub, boolean on, Runnable r) {
        LinearLayout row = Ui.h();
        row.setGravity(Gravity.TOP);
        row.setBackground(on ? Ui.round(0xFF0F2242, 14, Theme.BLUE, 1.6f) : Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
        Ui.pad(row, 14, 13, 14, 13);
        FrameLayout radio = new FrameLayout(Ui.a);
        radio.setBackground(on ? Ui.oval(Theme.BLUE) : Ui.round(0, 11, Theme.MUTED, 1.6f));
        if (on) {
            View inner = new View(Ui.a);
            inner.setBackground(Ui.oval(0xFFFFFFFF));
            radio.addView(inner, new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER));
        }
        LinearLayout.LayoutParams rp = lp(dp(22), dp(22));
        rp.topMargin = dp(1);
        row.addView(radio, rp);
        LinearLayout col = Ui.v();
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(Ui.tv(title, 15.5f, Theme.TEXT, Theme.SEMI));
        if (sub != null) {
            TextView s = Ui.tv(sub, 13, Theme.TEXT2, Theme.REG);
            s.setLineSpacing(dp(2), 1);
            s.setPadding(0, dp(4), 0, 0);
            col.addView(s);
        }
        row.addView(col, lp(0, WC, 1));
        Ui.press(row, 14, r);
        row.setLayoutParams(lpm(MW, WC, 0, 0, 0, 8));
        return row;
    }

    // ------------------------------------------------------------------ paso 3: resumen

    private void stepSummary(LinearLayout body) {
        int n = sel.size();
        body.addView(Ui.section("Jugadores y orden"));
        LinearLayout pc = Ui.card();
        for (int i = 0; i < n; i++) {
            Player p = st.player(sel.get(i));
            LinearLayout r = Ui.h();
            Ui.pad(r, 0, 7, 0, 7);
            TextView num = Ui.one((i + 1) + ".", 14, Theme.MUTED, Theme.SEMI);
            num.setMinWidth(dp(24));
            r.addView(num);
            r.addView(Ui.dot(p.color, 18));
            TextView t = Ui.one(p.name, 15.5f, Theme.TEXT, Theme.MED);
            t.setPadding(dp(10), 0, 0, 0);
            r.addView(t, lp(0, WC, 1));
            if (i == 0) {
                r.addView(Ui.icon(Ico.DECK, 0, 18));
                TextView d = Ui.one("Reparte la 1.ª ronda", 12.5f, Theme.TEXT2, Theme.MED);
                d.setPadding(dp(6), 0, 0, 0);
                r.addView(d);
            }
            pc.addView(r);
        }
        body.addView(pc, Ui.cardLp());

        List<Integer> seq = Engine.sequence(rules, n);
        int max = 0, auc = 0;
        for (int c : seq) max = Math.max(max, c);
        body.addView(Ui.section("Rondas"));
        LinearLayout rc = Ui.card();
        TextView rt = Ui.tv(seq.size() + " rondas · máximo " + Ui.plural(max, "carta", "cartas") + " por jugador",
                15, Theme.TEXT, Theme.SEMI);
        rc.addView(rt);
        Flow f = new Flow(a, dp(6), dp(6));
        f.setPadding(0, dp(10), 0, dp(4));
        for (int c : seq) {
            boolean au = rules.auctionMax && c == max;
            if (au) auc++;
            TextView b = Ui.one(String.valueOf(c), 13, au ? 0xFFFFFFFF : Theme.TEXT2, Theme.SEMI);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.oval(au ? Theme.BLUE : Theme.SURFACE3));
            f.addView(b, new android.view.ViewGroup.LayoutParams(dp(28), dp(28)));
        }
        rc.addView(f);
        TextView leg = Ui.tv(auc > 0 ? "En azul, las " + auc + " rondas subastadas: quien se lleva la subasta elige el triunfo."
                : "Sin rondas subastadas.", 12.5f, Theme.MUTED, Theme.REG);
        leg.setPadding(0, dp(6), 0, 0);
        rc.addView(leg);
        body.addView(rc, Ui.cardLp());

        body.addView(Ui.section("Reglas" + (customized ? " (personalizadas)" : " · " + rules.name)));
        LinearLayout kc = Ui.card();
        Ui.pad(kc, 14, 4, 14, 4);
        List<String[]> lines = RulesText.lines(rules, mode, n);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) kc.addView(Ui.divider());
            kc.addView(Ui.kv(lines.get(i)[0], lines.get(i)[1]));
        }
        body.addView(kc, Ui.cardLp());
    }

    private void start() {
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> colors = new HashMap<>();
        for (String id : sel) {
            Player p = st.player(id);
            names.put(id, p.name);
            colors.put(id, p.color);
        }
        Rules r = rules.copy();
        if (customized) r.name = "Personalizadas";
        Game g = Engine.newGame(Store.newId("g"), r, mode, sel, names, colors, System.currentTimeMillis());
        st.addGame(g, true);
        a.popToRoot(0);
        a.refresh();
    }
}
