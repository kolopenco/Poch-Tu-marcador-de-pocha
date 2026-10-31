package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;
import static com.kolopenco.poch.Ui.lpm;

import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Res;
import com.kolopenco.poch.Model.Round;
import com.kolopenco.poch.Model.Snap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Marcador de una partida: en curso o en revisión desde el historial. */
class GameScreen extends Screen {
    final Game g;
    /** Revisión de una partida cerrada (desde el historial): sin «Guardar ronda». */
    final boolean review;
    int vi;
    boolean editing;
    Snap editSnap;

    private Views.RoundStrip strip;
    private TextView label;
    private LinearLayout chips, banner, cardsBox, footer;
    private TextView bannerText;
    private ImageView bannerIcon;
    private ScrollView cardsScroll;
    private final List<Views.PlayerCard> cards = new ArrayList<>();
    private int lastScrollH = -1;

    GameScreen(MainActivity a, Game g, boolean review, int startIdx) {
        super(a);
        this.g = g;
        this.review = review;
        this.vi = startIdx;
    }

    int maxSel() {
        if (review) return Math.max(0, Engine.savedCount(g) - 1);
        return Math.max(0, Math.min(Engine.current(g), g.rounds.size() - 1));
    }

    Round rd() { return g.rounds.get(vi); }

    boolean editable() {
        if (editing) return true;
        if (review) return false;
        Round r = rd();
        return !r.saved && vi == Engine.current(g);
    }

    @Override View build() {
        vi = Math.max(0, Math.min(vi, maxSel()));
        LinearLayout root = Ui.v();

        // Cabecera
        if (!review) {
            ImageView logo = new ImageView(a);
            logo.setImageResource(R.drawable.poch_icon);
            logo.setLayoutParams(lp(dp(42), dp(42)));
            FrameLayout menu = Ui.iconButton(Ico.DOTS, Theme.TEXT, 22, this::openMenu);
            String sub = "Partida en curso · " + g.n() + " jugadores" + (Model.FULL.equals(g.mode) ? " · completo" : "");
            root.addView(Ui.header(logo, "Pocha", sub, menu));
        } else {
            FrameLayout back = Ui.iconButton(Ico.BACK, Theme.TEXT, 24, this::leave);
            root.addView(Ui.header(back, "Rondas de la partida", HistoryScreen.title(st, g) + " · " + Ui.date(g.created)));
        }

        // Carrusel de rondas
        strip = new Views.RoundStrip(a);
        strip.listener = idx -> {
            if (editing) {
                a.toast("Guarda o cancela la corrección antes de cambiar de ronda");
                strip.set(cardsList(), editedList(), maxSel(), vi, true);
                return;
            }
            vi = idx;
            render(false);
        };
        root.addView(strip, lpm(MW, dp(58), 6, 0, 6, 0));

        label = Ui.one("", 15, Theme.TEXT, Theme.SEMI);
        label.setGravity(Gravity.CENTER);
        root.addView(label, lpm(MW, WC, 16, 2, 16, 8));

        chips = Ui.h();
        chips.setGravity(Gravity.CENTER);
        root.addView(chips, lpm(MW, WC, 16, 0, 16, 8));

        banner = Ui.h();
        banner.setGravity(Gravity.CENTER);
        banner.setBackground(Ui.round(Theme.BLUE_BG, 12, Theme.alpha(Theme.BLUE, 0.85f), 1.2f));
        Ui.pad(banner, 12, 9, 12, 9);
        bannerIcon = Ui.icon(Ico.GAVEL, Theme.GOLD, 19);
        ((Ico) bannerIcon.getDrawable()).rich = true;
        banner.addView(bannerIcon);
        bannerText = Ui.one("", 13.5f, Theme.TEXT, Theme.MED);
        bannerText.setPadding(dp(9), 0, 0, 0);
        banner.addView(bannerText);
        root.addView(banner, lpm(MW, WC, 16, 0, 16, 8));

        cardsScroll = new ScrollView(a);
        cardsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        cardsScroll.setVerticalScrollBarEnabled(false);
        cardsScroll.setFillViewport(true);
        cardsBox = Ui.v();
        cardsBox.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(cardsBox, 16, 0, 16, 4);
        cardsScroll.addView(cardsBox, new FrameLayout.LayoutParams(MW, WC));
        root.addView(cardsScroll, lp(MW, 0, 1));
        cardsScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != lastScrollH) {
                lastScrollH = b - t;
                v.post(this::sizeCards);
            }
        });

        cards.clear();
        for (String pid : g.order) {
            Views.PlayerCard pc = new Views.PlayerCard(a);
            final String p = pid;
            pc.listener = new Views.PlayerCard.Listener() {
                @Override public void step(int which, int delta) { onStep(p, which, delta); }
                @Override public void clear(int which) { onClear(p, which); }
                @Override public void valueTap(int which) { a.toast("Mantén pulsado el número para borrarlo"); }
                @Override public void auction() { onAuction(p); }
            };
            cards.add(pc);
            cardsBox.addView(pc, lpm(MW, dp(84), 0, 0, 0, 7));
        }

        footer = Ui.v();
        Ui.pad(footer, 16, 4, 16, 10);
        root.addView(footer, lp(MW, WC));

        render(false);
        return root;
    }

    private void leave() {
        if (editing) {
            cancelEdit();
            return;
        }
        a.pop();
    }

    @Override boolean onBack() {
        if (editing) {
            cancelEdit();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ medidas

    private void sizeCards() {
        if (cardsScroll == null || cards.isEmpty()) return;
        int n = cards.size();
        boolean full = Model.FULL.equals(g.mode);
        int gap = dp(n <= 4 ? 10 : n <= 6 ? 8 : 6);
        int avail = cardsScroll.getHeight() - cardsBox.getPaddingTop() - cardsBox.getPaddingBottom();
        int per = (avail - gap * (n - 1)) / n;
        // Con pocos jugadores las tarjetas crecen (y todo su contenido con ellas);
        // con muchos encogen hasta un mínimo legible y, si aun así no caben, se desplaza.
        int min = dp(full ? 70 : 62), max = dp(full ? 150 : 140);
        int h = Math.max(min, Math.min(max, per));
        for (int i = 0; i < n; i++) {
            Views.PlayerCard pc = cards.get(i);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) pc.getLayoutParams();
            int bm = i < n - 1 ? gap : 0;
            if (p.height != h || p.bottomMargin != bm) {
                p.height = h;
                p.bottomMargin = bm;
                pc.setLayoutParams(p);
            }
        }
    }

    // ------------------------------------------------------------------ pintar

    private List<Integer> cardsList() {
        List<Integer> l = new ArrayList<>();
        for (Round r : g.rounds) l.add(r.cards);
        return l;
    }

    private List<Boolean> editedList() {
        List<Boolean> l = new ArrayList<>();
        for (Round r : g.rounds) l.add(Engine.edited(r));
        return l;
    }

    void render(boolean animate) {
        Round r = rd();
        strip.set(cardsList(), editedList(), maxSel(), vi, animate);

        // Texto «Ronda X de Y · N cartas»
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (r.tiebreak) {
            int k = 0;
            for (int i = 0; i <= vi; i++) if (g.rounds.get(i).tiebreak) k++;
            sb.append("Desempate ").append(String.valueOf(k)).append(" · 1 carta");
        } else {
            sb.append("Ronda ").append(String.valueOf(vi + 1)).append(" de ")
                    .append(String.valueOf(Engine.regularCount(g))).append(" · ")
                    .append(Ui.plural(r.cards, "carta", "cartas"));
        }
        String extra = editing ? "  · corrigiendo" : Engine.edited(r) ? "  · corregida" : null;
        if (extra != null) {
            int s = sb.length();
            sb.append(extra);
            sb.setSpan(new ForegroundColorSpan(Theme.YELLOW), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        label.setText(sb);

        renderChips();

        // Aviso de ronda subastada o de desempate
        if (r.tiebreak) {
            banner.setVisibility(View.VISIBLE);
            bannerIcon.setImageDrawable(new Ico(Ico.CROWN, Theme.GOLD).rich());
            List<String> names = new ArrayList<>();
            for (String p : Engine.players(g, r)) names.add(g.name(p));
            bannerText.setText("Desempate: " + joinNames(names));
        } else if (r.auction) {
            banner.setVisibility(View.VISIBLE);
            bannerIcon.setImageDrawable(new Ico(Ico.GAVEL, Theme.GOLD).rich());
            if (editable()) bannerText.setText("Ronda subastada · el ganador elige triunfo");
            else if (r.winner != null) bannerText.setText("Ronda subastada · se la llevó " + g.name(r.winner));
            else bannerText.setText("Ronda subastada");
        } else {
            banner.setVisibility(View.GONE);
        }

        renderCards();
        renderFooter();
    }

    static String joinNames(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) s.append(i == names.size() - 1 ? " y " : ", ");
            s.append(names.get(i));
        }
        return s.toString();
    }

    private void renderChips() {
        chips.removeAllViews();
        Round r = rd();
        int shown = 0;
        for (int i = 0; i < 4; i++) {
            if (!g.rules.multOn[i]) continue;
            final String suit = Model.SUITS[i];
            Views.MultChip mc = new Views.MultChip(a);
            mc.suit = suit;
            mc.factor = g.rules.multF[i];
            mc.on = suit.equals(r.mult);
            mc.setAlpha(editable() || mc.on ? 1f : 0.55f);
            mc.setContentDescription(mc.label());
            mc.setOnClickListener(v -> onMult(suit));
            LinearLayout.LayoutParams p = lp(WC, WC);
            if (shown > 0) p.leftMargin = dp(10);
            chips.addView(mc, p);
            shown++;
        }
        chips.setVisibility(shown == 0 ? View.GONE : View.VISIBLE);
    }

    private void renderCards() {
        Round r = rd();
        boolean full = Model.FULL.equals(g.mode);
        boolean ed = editable();
        String dealer = Engine.dealer(g, vi);
        Set<String> changed = Engine.editedPlayers(g, r);
        for (int i = 0; i < g.order.size(); i++) {
            String p = g.order.get(i);
            Views.PlayerCard pc = cards.get(i);
            Res x = r.res.get(p);
            pc.name = g.name(p);
            pc.color = g.color(p);
            pc.score = Engine.total(g, vi, p);
            pc.delta = Engine.points(g, r, p);
            int[] af = Engine.af(g, vi, p);
            pc.hits = af[0];
            pc.misses = af[1];
            pc.dealer = p.equals(dealer);
            pc.full = full;
            pc.auction = r.auction;
            pc.won = p.equals(r.winner);
            pc.v = x == null ? null : x.v;
            pc.bid = x == null ? null : x.bid;
            pc.made = x == null ? null : x.made;
            pc.out = !r.plays(p);
            pc.editable = ed;
            pc.yellow = !editing && changed.contains(p);
            pc.readText = readText(g, x);
            pc.invalidate();
        }
    }

    static String readText(Game g, Res x) {
        if (x == null) return "Sin dato";
        if (Model.FULL.equals(g.mode)) {
            if (x.bid == null || x.made == null) return "Sin dato";
            return "Pedidas: " + x.bid + " · Hechas: " + x.made;
        }
        if (x.v == null) return "Sin dato";
        return x.v >= 0 ? "Acierto: " + x.v : "Fallo: " + Views.num(x.v);
    }

    private void renderFooter() {
        footer.removeAllViews();
        Round r = rd();
        if (editing) {
            List<String> pr = Engine.problems(g, r);
            footer.addView(hint(pr.isEmpty() ? "Revisa los cambios y guárdalos" : pr.get(0), !pr.isEmpty()));
            LinearLayout row = Ui.h();
            row.addView(Ui.secondary("Cancelar", Theme.TEXT2, this::cancelEdit), lp(0, WC, 1));
            TextView save = Ui.primary("Guardar cambios", this::commitEdit);
            Ui.setPrimaryEnabled(save, pr.isEmpty());
            LinearLayout.LayoutParams p = lp(0, WC, 1.4f);
            p.leftMargin = dp(10);
            row.addView(save, p);
            footer.addView(row);
            return;
        }
        if (editable()) {
            List<String> pr = Engine.problems(g, r);
            footer.addView(hint(pr.isEmpty() ? "" : pr.get(0), true));
            TextView save = Ui.primary("Guardar ronda", this::saveRound);
            Ui.setPrimaryEnabled(save, pr.isEmpty());
            footer.addView(save, lp(MW, WC));
            return;
        }
        // Ronda ya guardada: modo lectura
        String info;
        if (Engine.edited(r)) {
            long t = 0;
            for (Model.Revision rv : r.revs) t = Math.max(t, rv.at);
            info = "Corregida el " + Ui.dateTime(t);
        } else {
            info = r.savedAt > 0 ? "Guardada el " + Ui.dateTime(r.savedAt) : "";
        }
        footer.addView(hint(info, false));
        LinearLayout row = Ui.h();
        row.addView(Ui.iconTextButton(Ico.EDIT, "Editar ronda", Theme.YELLOW, this::startEdit), lp(0, WC, 1));
        if (Engine.canUndo(r)) {
            LinearLayout undo = Ui.iconTextButton(Ico.UNDO, "Deshacer", Theme.TEXT2, this::undo);
            LinearLayout.LayoutParams p = lp(0, WC, 1);
            p.leftMargin = dp(10);
            row.addView(undo, p);
        }
        if (!review) {
            TextView go;
            if (Engine.tieAtEnd(g) && !Model.TIE_SHARED.equals(g.rules.tie)) go = Ui.primary("Resolver empate", this::askTie);
            else if (Engine.finished(g)) go = Ui.primary("Clasificación", () -> a.push(new FinalScreen(a, g)));
            else go = Ui.primary("Ronda actual", () -> {
                vi = Engine.current(g);
                render(true);
            });
            LinearLayout.LayoutParams p = lp(0, WC, 1);
            p.leftMargin = dp(10);
            row.addView(go, p);
        }
        footer.addView(row);
    }

    private TextView hint(String s, boolean warn) {
        TextView t = Ui.one(s, 13, warn ? Theme.TEXT2 : Theme.MUTED, Theme.MED);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(24));
        t.setPadding(0, dp(2), 0, dp(6));
        if (warn && !s.isEmpty()) t.setCompoundDrawablePadding(dp(6));
        return t;
    }

    // ------------------------------------------------------------------ acciones

    private void changed() {
        if (!editing && !review) st.saveGame(g);
        renderCards();
        renderFooter();
    }

    private boolean guard() {
        if (editable()) return true;
        a.toast("Pulsa «Editar ronda» para corregir esta ronda");
        return false;
    }

    private void onStep(String p, int which, int delta) {
        if (!guard()) return;
        Round r = rd();
        Res x = r.r(p);
        if (Model.FULL.equals(g.mode)) {
            if (which == 0) x.bid = Engine.stepFull(x.bid, delta, r.cards);
            else x.made = Engine.stepFull(x.made, delta, r.cards);
        } else {
            x.v = Engine.stepSimple(x.v, delta, r.cards);
        }
        changed();
    }

    private void onClear(String p, int which) {
        if (!guard()) return;
        Res x = rd().r(p);
        if (Model.FULL.equals(g.mode)) {
            if (which == 0) x.bid = null; else x.made = null;
        } else {
            x.v = null;
        }
        changed();
    }

    private void onAuction(String p) {
        if (!guard()) return;
        Round r = rd();
        r.winner = p.equals(r.winner) ? null : p;
        changed();
    }

    private void onMult(String suit) {
        if (!guard()) return;
        Round r = rd();
        r.mult = suit.equals(r.mult) ? null : suit;
        renderChips();
        changed();
    }

    private void saveRound() {
        Round r = rd();
        if (!Engine.canSave(g, r)) return;
        Engine.save(g, r, System.currentTimeMillis(), false);
        st.saveGame(g);
        a.updateChrome();
        if (Engine.tieAtEnd(g)) {
            vi = Math.min(Engine.current(g), g.rounds.size() - 1);
            render(true);
            askTie();
            return;
        }
        if (Engine.finished(g)) {
            a.push(new FinalScreen(a, g));
            return;
        }
        vi = Engine.current(g);
        render(true);
        cardsScroll.smoothScrollTo(0, 0);
    }

    /** Empate en cabeza al terminar: se decide en ese momento si se comparte o se desempata. */
    void askTie() {
        List<String> lead = Engine.leaders(g);
        boolean again = g.rounds.get(g.rounds.size() - 1).tiebreak;
        a.dialog(again ? "Sigue el empate" : "Empate en cabeza",
                joinNames(names(lead)) + (again ? " siguen empatados a " : " empatan a ") + Engine.total(g, lead.get(0))
                        + " puntos. ¿Qué hacéis?\n\nDesempatar añade una ronda de una carta solo entre ellos.",
                null,
                MainActivity.act("Compartir victoria", () -> {
                    Engine.setTieRule(g, Model.TIE_SHARED);
                    st.saveGame(g);
                    a.push(new FinalScreen(a, g));
                }),
                MainActivity.main("Desempatar", () -> {
                    Engine.setTieRule(g, Model.TIE_BREAK);
                    st.saveGame(g);
                    vi = Math.min(Engine.current(g), g.rounds.size() - 1);
                    render(true);
                    a.updateChrome();
                }));
    }

    private List<String> names(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String p : ids) out.add(g.name(p));
        return out;
    }

    private void startEdit() {
        editing = true;
        editSnap = rd().snap();
        render(false);
    }

    private void cancelEdit() {
        rd().apply(editSnap);
        editing = false;
        render(false);
    }

    private void commitEdit() {
        Round r = rd();
        if (!Engine.canSave(g, r)) return;
        editing = false;
        boolean any = Engine.commitEdit(g, r, editSnap, System.currentTimeMillis());
        if (any) {
            st.saveGame(g);
            a.toast("Ronda corregida · totales recalculados");
        } else {
            a.toast("No había cambios");
        }
        render(false);
        a.updateChrome();
    }

    private void undo() {
        Round r = rd();
        if (Engine.undo(g, r, System.currentTimeMillis())) {
            st.saveGame(g);
            a.toast("Corrección deshecha");
            render(false);
        }
    }

    // ------------------------------------------------------------------ menú de partida

    private void openMenu() {
        LinearLayout box = a.menuPanel();
        int left = g.rounds.size() - Engine.savedCount(g);
        a.menuItem(box, Ico.PLAY, Theme.BLUE_HI, "Continuar partida", null, true, () -> { });
        boolean done = Engine.finished(g);
        a.menuItem(box, Ico.FLAG, Theme.ORANGE, "Finalizar partida",
                done ? "Ver la clasificación final" : "Faltan " + Ui.plural(left, "ronda", "rondas"), done, () -> {
                    if (Engine.finished(g)) a.push(new FinalScreen(a, g));
                    else a.info("Aún quedan rondas", "Faltan " + Ui.plural(left, "ronda", "rondas")
                            + ". Si no podéis terminar, usa «Finalizar anticipadamente»: se guardan las rondas jugadas, sin ganador.");
                });
        a.menuItem(box, Ico.PAUSE, Theme.GOLD, "Finalizar anticipadamente", "Sin victoria para nadie", true, this::finishEarly);
        a.menuItem(box, Ico.SLIDERS, Theme.TEXT2, "Reglas de esta partida", null, true,
                () -> a.push(new RulesViewScreen(a, g)));
        a.menuItem(box, Ico.TRASH, Theme.RED, "Descartar partida", null, true, this::discard);
    }

    private void finishEarly() {
        int saved = Engine.savedCount(g);
        a.confirm("Finalizar anticipadamente",
                "Se guardan las " + Ui.plural(saved, "ronda jugada", "rondas jugadas")
                        + " y cuentan para las estadísticas, pero nadie gana la partida.\n\nLa ronda que estaba a medias no se guarda.",
                "Finalizar", false, () -> {
                    for (Round r : g.rounds) if (!r.saved) { r.res.clear(); r.winner = null; r.mult = null; }
                    g.status = Model.EARLY;
                    g.closed = true;
                    g.ended = System.currentTimeMillis();
                    st.saveGame(g);
                    st.setActive(null);
                    a.popToRoot(0);
                    a.openIn(1, new HistoryDetail(a, g.id));
                    a.toast("Partida guardada sin victoria");
                });
    }

    private void discard() {
        a.confirm("Descartar partida",
                "Se borrará la partida en curso con todas sus rondas. No contará en el historial ni en las estadísticas.",
                "Descartar", true, () -> {
                    st.deleteGame(g);
                    a.popToRoot(0);
                    a.refresh();
                    a.toast("Partida descartada");
                });
    }
}

/** Raíz de la pestaña Partida: el marcador si hay partida, o el inicio si no. */
class GameTab extends Screen {
    /** Al volver de la clasificación tras elegir desempate, ir directamente a la nueva ronda. */
    static boolean jumpToCurrent;
    private GameScreen inner;

    GameTab(MainActivity a) { super(a); }

    @Override View build() {
        Game g = st.active();
        if (g != null) {
            if (inner == null || inner.g != g) inner = new GameScreen(a, g, false, Engine.current(g));
            else if (!inner.editing) inner.vi = jumpToCurrent ? inner.maxSel() : Math.min(inner.vi, inner.maxSel());
            jumpToCurrent = false;
            View v = inner.build();
            scroll = null;
            return v;
        }
        inner = null;
        return empty();
    }

    @Override boolean onBack() {
        return inner != null && inner.onBack();
    }

    private View empty() {
        LinearLayout root = Ui.v();
        ImageView logo = new ImageView(a);
        logo.setImageResource(R.drawable.poch_icon);
        logo.setLayoutParams(lp(dp(42), dp(42)));
        root.addView(Ui.header(logo, "Pocha", "Sin partida en curso"));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 8, 16, 24);

        LinearLayout hero = Ui.v();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(Ui.round(Theme.SURFACE, 20, Theme.BORDER, 1));
        Ui.pad(hero, 20, 26, 20, 22);
        ImageView big = new ImageView(a);
        big.setImageResource(R.drawable.poch_icon);
        hero.addView(big, lp(dp(132), dp(132)));
        TextView t = Ui.tv("Marcador de La Pocha", 21, Theme.TEXT, Theme.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, 0);
        hero.addView(t);
        TextView s = Ui.tv("Apunta cada ronda con pocos toques: la app calcula los puntos, los repartos y las subastas.",
                14.5f, Theme.TEXT2, Theme.REG);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(dp(3), 1);
        s.setPadding(dp(6), dp(8), dp(6), dp(18));
        hero.addView(s);
        hero.addView(Ui.primary("Nueva partida", () -> a.push(new SetupScreen(a))), lp(MW, WC));
        body.addView(hero, lp(MW, WC));

        List<Game> recent = st.gamesByDate();
        if (!recent.isEmpty()) {
            body.addView(Ui.section("Últimas partidas"));
            int k = 0;
            for (Game g : recent) {
                if (k++ >= 3) break;
                body.addView(HistoryScreen.gameRow(a, st, g, () -> a.openIn(1, new HistoryDetail(a, g.id))));
            }
        } else if (st.players.isEmpty()) {
            body.addView(Ui.space(14));
            TextView tip = Ui.tv("Al crear la partida podrás añadir a los jugadores. Se guardan para las siguientes y para sus estadísticas.",
                    13.5f, Theme.MUTED, Theme.REG);
            tip.setGravity(Gravity.CENTER);
            tip.setLineSpacing(dp(2), 1);
            body.addView(tip);
        }
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }
}

/** Clasificación final al terminar la última ronda (antes de cerrar la partida). */
class FinalScreen extends Screen {
    final Game g;

    FinalScreen(MainActivity a, Game g) {
        super(a);
        this.g = g;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Clasificación final", a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 6, 16, 16);

        List<String> w = Engine.winners(g);
        List<String> wn = new ArrayList<>();
        for (String p : w) wn.add(g.name(p));
        LinearLayout hero = Ui.h();
        hero.setBackground(Ui.round(Theme.SURFACE, 16, Theme.BORDER, 1));
        Ui.pad(hero, 14, 14, 14, 14);
        FrameLayout crown = new FrameLayout(a);
        crown.setBackground(Ui.oval(0x332F2410));
        crown.addView(Ui.icon(new Ico(Ico.CROWN, Theme.GOLD).rich(), 34),
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        hero.addView(crown, lp(dp(58), dp(58)));
        LinearLayout col = Ui.v();
        col.setPadding(dp(14), 0, 0, 0);
        String title = w.size() == 1 ? "Gana " + wn.get(0) : "Victoria compartida";
        col.addView(Ui.tv(title, 19, Theme.TEXT, Theme.BOLD));
        String sub = g.n() + " jugadores · " + Ui.plural(Engine.savedCount(g), "ronda", "rondas");
        if (w.size() > 1) sub = GameScreen.joinNames(wn) + " · " + sub;
        TextView s1 = Ui.tv(sub, 13.5f, Theme.TEXT2, Theme.REG);
        s1.setPadding(0, dp(4), 0, 0);
        col.addView(s1);
        TextView s2 = Ui.tv(Ui.dateTime(g.created), 13, Theme.MUTED, Theme.REG);
        s2.setPadding(0, dp(3), 0, 0);
        col.addView(s2);
        hero.addView(col, lp(0, WC, 1));
        body.addView(hero, lpm(MW, WC, 0, 0, 0, 12));

        body.addView(standings(a, g));

        TextView note = Ui.tv("Antes de cerrarla puedes revisar y corregir cualquier ronda. Al cerrarla pasa al historial y cuenta para las estadísticas.",
                13, Theme.MUTED, Theme.REG);
        note.setLineSpacing(dp(2), 1);
        note.setPadding(dp(2), dp(14), dp(2), 0);
        body.addView(note);

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));

        if (w.size() > 1) {
            LinearLayout tie = Ui.iconTextButton(Ico.CROWN, "Mejor desempatar", Theme.GOLD, () -> {
                Engine.setTieRule(g, Model.TIE_BREAK);
                st.saveGame(g);
                GameTab.jumpToCurrent = true;
                a.pop();
                a.toast("Ronda de desempate a una carta");
            });
            LinearLayout tw = Ui.v();
            Ui.pad(tw, 16, 6, 16, 0);
            tw.addView(tie, lp(MW, WC));
            root.addView(tw);
        }
        LinearLayout foot = Ui.h();
        Ui.pad(foot, 16, 8, 16, 12);
        foot.addView(Ui.secondary("Revisar rondas", Theme.TEXT2, a::pop), lp(0, WC, 1));
        TextView close = Ui.primary("Cerrar partida", this::close);
        LinearLayout.LayoutParams p = lp(0, WC, 1.2f);
        p.leftMargin = dp(10);
        foot.addView(close, p);
        root.addView(foot);
        return root;
    }

    private void close() {
        g.status = Model.COMPLETED;
        g.closed = true;
        g.ended = System.currentTimeMillis();
        st.saveGame(g);
        st.setActive(null);
        a.popToRoot(0);
        a.openIn(1, new HistoryDetail(a, g.id));
        a.toast("Partida guardada en el historial");
    }

    static final int[] MEDALS = {Theme.GOLD, Theme.SILVER, Theme.BRONZE};

    /** Lista de la clasificación con medallas para los tres primeros. */
    static LinearLayout standings(MainActivity a, Game g) {
        LinearLayout box = Ui.v();
        boolean early = Model.EARLY.equals(g.status);
        for (Engine.Rank r : Engine.standings(g)) {
            LinearLayout row = Ui.h();
            Ui.pad(row, 10, 9, 14, 9);
            row.setMinimumHeight(dp(56));
            int medal = !early && r.pos <= 3 ? MEDALS[r.pos - 1] : 0;
            if (medal != 0) {
                GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{Theme.alpha(medal, 0.42f), Theme.alpha(medal, 0.10f)});
                bg.setCornerRadius(dp(12));
                bg.setStroke(dp(1), Theme.alpha(medal, 0.55f));
                row.setBackground(bg);
            } else {
                row.setBackground(Ui.round(Theme.SURFACE, 12, Theme.BORDER, 1));
            }
            Views.Badge b = new Views.Badge(a);
            b.text = String.valueOf(r.pos);
            b.round = true;
            if (medal != 0) {
                b.medal = true;
                b.color = medal;
            } else {
                b.color = Theme.SURFACE3;
            }
            row.addView(b, lp(dp(30), dp(30)));
            row.addView(Ui.hspace(10));
            row.addView(Ui.dot(g.color(r.pid), 12));
            LinearLayout col = Ui.v();
            col.setPadding(dp(10), 0, 0, 0);
            col.addView(Ui.one(g.name(r.pid), 16.5f, Theme.TEXT, Theme.SEMI));
            int[] af = Engine.af(g, g.rounds.size() - 1, r.pid);
            TextView sub = Ui.one(Ui.plural(af[0], "acierto", "aciertos") + " · " + Ui.plural(af[1], "fallo", "fallos"),
                    12.5f, Theme.TEXT2, Theme.REG);
            sub.setPadding(0, dp(3), 0, 0);
            col.addView(sub);
            row.addView(col, lp(0, WC, 1));
            row.addView(Ui.one(Views.num(r.total), 20, Theme.TEXT, Theme.BOLD));
            box.addView(row, lpm(MW, WC, 0, 0, 0, 7));
        }
        return box;
    }
}
