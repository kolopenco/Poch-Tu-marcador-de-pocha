package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;
import static com.kolopenco.poch.Ui.lpm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Group;
import com.kolopenco.poch.Model.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Grupos de juego: lista, ayuda y utilidades comunes. */
class GroupsScreen extends Screen {
    static final String GUIDE = "https://github.com/kolopenco/Poch-Tu-marcador-de-pocha/blob/main/docs/grupo.md";

    GroupsScreen(MainActivity a) { super(a); }

    @Override boolean liveRefresh() { return true; }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Grupos de juego", a::pop, Ui.iconButton(Ico.INFO, Theme.TEXT2, 21, () -> help(a))));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);
        if (st.groups.isEmpty()) {
            LinearLayout c = Ui.card();
            Ui.pad(c, 18, 20, 18, 20);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.addView(Ui.icon(Ico.PEOPLE, Theme.BLUE_TEXT, 40));
            TextView t = Ui.tv("Jugad en grupo", 18, Theme.TEXT, Theme.BOLD);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, 0);
            c.addView(t);
            TextView s = Ui.tv("Compartid partidas y estadísticas entre vuestros móviles, sin pasaros archivos.", 14, Theme.TEXT2, Theme.REG);
            s.setGravity(Gravity.CENTER);
            s.setLineSpacing(dp(3), 1);
            s.setPadding(0, dp(6), 0, 0);
            c.addView(s);
            body.addView(c, Ui.cardLp());
        }
        for (Group g : st.groups) {
            Player me = st.player(g.me);
            String sub = (me != null ? "Tú: " + me.name : "Sin elegir quién eres")
                    + " · " + (g.lastSync == 0 ? "sin sincronizar" : "sincronizado " + Ui.dateTime(g.lastSync));
            body.addView(Ui.listRow(SettingsScreen.badge(Ico.PEOPLE, Theme.BLUE_HI), g.name, sub, () -> a.push(new GroupDetail(a, g.id))));
        }
        body.addView(Ui.space(6));
        body.addView(Ui.primary("Unirse con una invitación", () -> a.push(new JoinScreen(a, null))), lp(MW, WC));
        body.addView(Ui.space(10));
        body.addView(Ui.secondary("Crear un grupo nuevo", Theme.BLUE_TEXT, () -> a.push(new CreateGroupScreen(a))), lp(MW, WC));
        TextView n = Ui.tv("Para crear un grupo hace falta un proyecto gratuito de Firebase. Para unirse solo hace falta la invitación.",
                12.5f, Theme.MUTED, Theme.REG);
        n.setLineSpacing(dp(2), 1);
        n.setPadding(dp(2), dp(12), dp(2), 0);
        body.addView(n);
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    static void help(MainActivity a) {
        LinearLayout b = Ui.v();
        b.addView(para("Un grupo comparte jugadores, partidas y estadísticas entre vuestros móviles (Android y iPhone).", null));
        b.addView(para("abre o copia la invitación que te han mandado, pulsa «Unirse», di quién eres y listo. No hace falta cuenta.", "Si te invitan: "));
        b.addView(para("una sola persona del grupo crea un proyecto gratuito de Firebase (unos 10 minutos con la guía) y pega aquí su configuración. Los datos quedan en ese proyecto: solo entra quien tenga la invitación.", "Para crear un grupo: "));
        b.addView(para("al abrir la app baja lo nuevo, y cada partida se sube al cerrarla (o al corregirla después). La partida en curso no se comparte.", "Cuándo se sincroniza: "));
        b.addView(Ui.space(6));
        b.addView(Ui.iconTextButton(Ico.INFO, "Guía para crear un grupo", Theme.BLUE_TEXT, () -> open(a, GUIDE)), lp(MW, WC));
        a.sheet("Grupos de juego", b);
    }

    private static TextView para(String text, String bold) {
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder();
        if (bold != null) {
            s.append(bold);
            s.setSpan(new android.text.style.ForegroundColorSpan(Theme.TEXT), 0, bold.length(), 0);
            s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, bold.length(), 0);
        }
        s.append(text);
        TextView t = Ui.tv(s, 14.5f, Theme.TEXT2, Theme.REG);
        t.setLineSpacing(dp(3), 1);
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }

    static void open(MainActivity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            a.toast("No se pudo abrir el navegador");
        }
    }

    static String clipboard(MainActivity a) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return null;
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(a);
            return t == null ? null : t.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static void copy(MainActivity a, String text, String done) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Poch!", text));
            a.toast(done);
        } catch (Exception e) {
            a.toast("No se pudo copiar");
        }
    }

    /** Campo de texto cuyo contenido sobrevive a los redibujados. */
    static EditText field(String hint, String value, boolean multi, java.util.function.Consumer<String> onChange) {
        EditText e = Ui.input(hint, value);
        if (multi) {
            e.setSingleLine(false);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setMinLines(4);
            e.setMaxLines(8);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setTypeface(android.graphics.Typeface.MONOSPACE);
            e.setTextSize(13);
        }
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { onChange.accept(s.toString()); }
        });
        return e;
    }

    static TextView note(String s) {
        TextView t = Ui.tv(s, 13.5f, Theme.TEXT2, Theme.REG);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(dp(2), 0, dp(2), dp(10));
        return t;
    }

    /** Diálogo con la invitación: compartir o copiar. */
    static void invite(MainActivity a, Group g, boolean fresh) {
        String txt = Cloud.inviteText(g);
        a.dialog(fresh ? "Grupo creado" : "Invitar al grupo",
                (fresh ? "Ya está creado «" + g.name + "». " : "")
                        + "Manda la invitación a tus amigos (WhatsApp, correo…). Quien la tenga podrá entrar en el grupo, así que no la publiques.",
                null,
                MainActivity.act("Copiar", () -> copy(a, txt, "Invitación copiada")),
                MainActivity.main("Compartir", () -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, txt);
                    try { a.startActivity(Intent.createChooser(i, "Invitar al grupo")); } catch (Exception e) { copy(a, txt, "Invitación copiada"); }
                }));
    }

    // ------------------------------------------------------------------ ¿quién eres tú?

    /** Selector «¿Quién eres tú?»: uno de la lista o «Soy nuevo» con su nombre. */
    static final class Who {
        static final String NEW = "__new";
        String sel;
        String name = "";

        void build(MainActivity a, LinearLayout box, List<Player> list, String newSub) {
            for (Player p : list) box.addView(SetupScreen.option(p.name, null, p.id.equals(sel), () -> { sel = p.id; a.refresh(); }));
            box.addView(SetupScreen.option("Soy nuevo", newSub, NEW.equals(sel), () -> { sel = NEW; a.refresh(); }));
            if (NEW.equals(sel)) {
                EditText e = field("Tu nombre", name, false, s -> name = s);
                box.addView(e, lpm(MW, WC, 0, 0, 0, 8));
                if (name.isEmpty()) e.post(e::requestFocus);
            }
        }

        boolean isNew() { return NEW.equals(sel); }
    }
}

/** Crear un grupo con un proyecto de Firebase propio. */
class CreateGroupScreen extends Screen {
    String cfg = "", name = "";
    final GroupsScreen.Who who = new GroupsScreen.Who();
    boolean busy;

    CreateGroupScreen(MainActivity a) {
        super(a);
        for (Group g : a.store.groups) if (g.me != null && a.store.player(g.me) != null) who.sel = g.me;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Crear grupo", a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);

        LinearLayout banner = Ui.h();
        banner.setBackground(Ui.round(Theme.BLUE_BG, 12, Theme.alpha(Theme.BLUE, 0.7f), 1));
        Ui.pad(banner, 12, 10, 12, 10);
        banner.addView(Ui.icon(Ico.INFO, Theme.BLUE_TEXT, 20));
        TextView bt = Ui.tv("Antes necesitas un proyecto gratuito de Firebase preparado. Toca aquí para ver la guía paso a paso.", 13.5f, Theme.TEXT, Theme.REG);
        bt.setLineSpacing(dp(2), 1);
        bt.setPadding(dp(10), 0, 0, 0);
        banner.addView(bt, lp(0, WC, 1));
        Ui.press(banner, 12, () -> GroupsScreen.open(a, GroupsScreen.GUIDE));
        body.addView(banner, lpm(MW, WC, 0, 0, 0, 6));

        body.addView(Ui.section("Configuración de Firebase"));
        body.addView(GroupsScreen.field("Pega aquí el bloque firebaseConfig = { apiKey: \"...\", projectId: \"...\", ... }", cfg, true, s -> cfg = s), lp(MW, WC));
        LinearLayout pr = Ui.h();
        pr.setPadding(0, dp(8), 0, 0);
        pr.addView(Ui.chip("Pegar", false, () -> {
            String c = GroupsScreen.clipboard(a);
            if (c == null) { a.toast("No hay nada copiado"); return; }
            cfg = c;
            a.refresh();
        }));
        body.addView(pr);

        body.addView(Ui.section("Nombre del grupo"));
        body.addView(GroupsScreen.field("Ej.: La pocha de los viernes", name, false, s -> name = s), lp(MW, WC));

        body.addView(Ui.section("¿Quién eres tú?"));
        LinearLayout wb = Ui.v();
        who.build(a, wb, st.playersSorted(), "Crea tu jugador");
        body.addView(wb);

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        LinearLayout foot = Ui.v();
        Ui.pad(foot, 16, 6, 16, 12);
        TextView b = Ui.primary(busy ? "Creando…" : "Crear grupo", this::create);
        Ui.setPrimaryEnabled(b, !busy);
        foot.addView(b, lp(MW, WC));
        root.addView(foot);
        return root;
    }

    private void create() {
        if (busy) return;
        String[] c = Cloud.parseFirebaseConfig(cfg);
        if (c == null) {
            a.info("Falta la configuración", "No encuentro el apiKey y el projectId en lo que has pegado. Copia el bloque firebaseConfig entero desde Firebase (Configuración del proyecto › Tus apps).");
            return;
        }
        String n = name.trim();
        if (n.isEmpty()) { a.toast("Ponle un nombre al grupo"); return; }
        if (who.sel == null) { a.toast("Dinos quién eres"); return; }
        String me;
        if (who.isNew()) {
            String nn = who.name.trim();
            if (nn.isEmpty()) { a.toast("Escribe tu nombre"); return; }
            Player ex = null;
            for (Player p : st.players) if (Backup.norm(p.name).equals(Backup.norm(nn))) ex = p;
            me = ex != null ? ex.id : st.addPlayer(nn, st.nextColor()).id;
        } else {
            me = who.sel;
        }
        busy = true;
        a.hideKeyboard();
        a.refresh();
        Sync.run(a, () -> Cloud.createGroup(Sync.host(a), c[0], c[1], n, me), g -> {
            busy = false;
            st.saveLastGroup(g.id);
            a.pop();
            a.push(new GroupDetail(a, g.id));
            GroupsScreen.invite(a, g, true);
        }, err -> {
            busy = false;
            a.refresh();
            a.info("No se pudo crear el grupo", err);
        });
    }
}

/** Unirse a un grupo: pegar la invitación → quién eres y emparejar jugadores. */
class JoinScreen extends Screen {
    String text = "";
    boolean busy;
    Cloud.JoinData fetched;
    Map<String, String> map;
    final GroupsScreen.Who who = new GroupsScreen.Who();

    JoinScreen(MainActivity a, String prefill) {
        super(a);
        if (prefill != null) text = prefill;
        else {
            String c = GroupsScreen.clipboard(a);
            if (c != null && Cloud.parseInvite(c) != null) text = c;
        }
    }

    @Override boolean onBack() {
        if (fetched != null && !busy) {
            fetched = null;
            a.refresh();
            return true;
        }
        return false;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Unirse a un grupo", () -> { if (!onBack()) a.pop(); }, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);
        String btn;
        Runnable act;
        if (fetched == null) {
            body.addView(GroupsScreen.note("Pega la invitación que te han mandado (el enlace o el mensaje entero)."));
            body.addView(GroupsScreen.field("https://…#unirse=…", text, true, s -> text = s), lp(MW, WC));
            LinearLayout pr = Ui.h();
            pr.setPadding(0, dp(8), 0, 0);
            pr.addView(Ui.chip("Pegar", false, () -> {
                String c = GroupsScreen.clipboard(a);
                if (c == null) { a.toast("No hay nada copiado"); return; }
                text = c;
                a.refresh();
            }));
            body.addView(pr);
            btn = busy ? "Entrando…" : "Continuar";
            act = this::fetch;
        } else {
            Group g = fetched.group;
            LinearLayout c = Ui.card();
            LinearLayout row = Ui.h();
            row.addView(Ui.icon(Ico.PEOPLE, Theme.BLUE_TEXT, 28));
            LinearLayout col = Ui.v();
            col.setPadding(dp(12), 0, 0, 0);
            col.addView(Ui.one(g.name, 17, Theme.TEXT, Theme.BOLD));
            TextView s = Ui.one(Ui.plural(fetched.players.size(), "jugador", "jugadores") + " · " + Ui.plural(fetched.games.size(), "partida", "partidas"), 13, Theme.TEXT2, Theme.REG);
            s.setPadding(0, dp(3), 0, 0);
            col.addView(s);
            row.addView(col, lp(0, WC, 1));
            c.addView(row);
            body.addView(c, Ui.cardLp());

            body.addView(Ui.section("¿Quién eres tú?"));
            List<Player> gp = new ArrayList<>(fetched.players);
            Collections.sort(gp, (x, y) -> x.name.compareToIgnoreCase(y.name));
            LinearLayout wb = Ui.v();
            who.build(a, wb, gp, "No estoy en la lista");
            body.addView(wb);

            if (!st.players.isEmpty() && !fetched.players.isEmpty()) {
                body.addView(Ui.section("Une los jugadores con los tuyos"));
                body.addView(GroupsScreen.note("Si ya tenías a alguno en tu móvil, únelo para que sus partidas y estadísticas se sumen."));
                for (Player p : fetched.players) {
                    String t = map.get(p.id);
                    Player mine = t == null ? null : st.player(t);
                    LinearLayout r = Ui.h();
                    r.setBackground(Ui.round(Theme.SURFACE, 14, mine != null ? Theme.alpha(Theme.GREEN, 0.6f) : Theme.BORDER, 1));
                    Ui.pad(r, 12, 10, 10, 10);
                    r.setMinimumHeight(dp(64));
                    r.addView(Ui.dot(p.color, 26));
                    LinearLayout cl = Ui.v();
                    cl.setPadding(dp(12), 0, dp(6), 0);
                    cl.addView(Ui.one(p.name, 16, Theme.TEXT, Theme.SEMI));
                    TextView sub = Ui.one(mine != null ? "Es tu jugador " + mine.name : "Se añade a tus jugadores", 13,
                            mine != null ? Theme.GREEN : Theme.BLUE_TEXT, Theme.MED);
                    sub.setPadding(0, dp(4), 0, 0);
                    cl.addView(sub);
                    r.addView(cl, lp(0, WC, 1));
                    r.addView(Ui.icon(Ico.CHEVRON_DOWN, Theme.TEXT2, 20));
                    Ui.press(r, 14, () -> choose(p));
                    body.addView(r, lpm(MW, WC, 0, 0, 0, 8));
                }
            }
            btn = busy ? "Uniendo…" : "Unirme al grupo";
            act = this::apply;
        }
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        LinearLayout foot = Ui.v();
        Ui.pad(foot, 16, 6, 16, 12);
        TextView b = Ui.primary(btn, act);
        Ui.setPrimaryEnabled(b, !busy);
        foot.addView(b, lp(MW, WC));
        root.addView(foot);
        return root;
    }

    private void fetch() {
        if (busy) return;
        Group inv = Cloud.parseInvite(text);
        if (inv == null) {
            a.info("Invitación no válida", "No reconozco la invitación. Copia el enlace completo que te mandaron.");
            return;
        }
        Group old = st.group(inv.id);
        if (old != null) {
            a.info("Ya estás en este grupo", "«" + old.name + "» ya está en tu lista de grupos.");
            return;
        }
        busy = true;
        a.hideKeyboard();
        a.refresh();
        Sync.run(a, () -> Cloud.joinFetch(Sync.host(a), inv), d -> {
            busy = false;
            fetched = d;
            Backup.Data mine = st.snapshot();
            Backup.Data in = new Backup.Data();
            in.players.addAll(d.players);
            in.games.addAll(d.games);
            map = Backup.suggest(mine, in);
            scrollY = 0;
            a.refresh();
        }, err -> {
            busy = false;
            a.refresh();
            a.info("No se pudo entrar", err);
        });
    }

    private void choose(Player p) {
        LinearLayout body = Ui.v();
        String cur = map.get(p.id);
        body.addView(SetupScreen.option("Añadir como jugador nuevo", null, cur == null, () -> {
            map.put(p.id, null);
            a.closeOverlay();
            a.refresh();
        }));
        Set<String> taken = new HashSet<>();
        for (Map.Entry<String, String> e : map.entrySet())
            if (e.getValue() != null && !e.getKey().equals(p.id)) taken.add(e.getValue());
        for (Player q : st.playersSorted()) {
            boolean busyQ = taken.contains(q.id);
            LinearLayout o = SetupScreen.option("Es " + q.name, busyQ ? "Ya unido con otro jugador del grupo" : null, q.id.equals(cur), () -> {
                if (busyQ) { a.toast(q.name + " ya está unido con otro jugador del grupo"); return; }
                map.put(p.id, q.id);
                a.closeOverlay();
                a.refresh();
            });
            if (busyQ) o.setAlpha(0.5f);
            body.addView(o);
        }
        a.sheet("¿Quién es " + p.name + "?", body);
    }

    private void apply() {
        if (busy) return;
        if (who.sel == null) { a.toast("Dinos quién eres"); return; }
        String meRemote = null;
        Player meNew = null;
        if (who.isNew()) {
            String n = who.name.trim();
            if (n.isEmpty()) { a.toast("Escribe tu nombre"); return; }
            for (Player p : fetched.players)
                if (Backup.norm(p.name).equals(Backup.norm(n))) { a.toast("Ya hay alguien llamado " + p.name + " en el grupo: elígelo en la lista"); return; }
            Player ex = null;
            for (Player p : st.players) if (Backup.norm(p.name).equals(Backup.norm(n))) ex = p;
            if (ex != null && map.containsValue(ex.id)) { a.toast(ex.name + " ya está unido con otro jugador del grupo"); return; }
            if (ex != null) meRemote = ex.id;          // ya estaba en mi móvil: ese soy yo
            else {
                meNew = new Player();
                meNew.id = Store.newId("j");
                meNew.name = n;
                meNew.color = st.nextColor();
                meNew.created = System.currentTimeMillis();
                meNew.updated = meNew.created;
            }
        } else {
            meRemote = who.sel;
        }
        busy = true;
        a.refresh();
        final String mr = meRemote;
        final Player mn = meNew;
        final Group g = fetched.group;
        Sync.run(a, () -> Cloud.joinApply(Sync.host(a), g, map, mr, mn), r -> {
            busy = false;
            st.saveLastGroup(g.id);
            a.popToRoot(4);
            a.openIn(4, new GroupsScreen(a));
            a.info("¡Ya estás en «" + g.name + "»!", "Se han descargado " + Ui.plural(r.newGames, "partida", "partidas")
                    + ". A partir de ahora, al abrir la app se bajan las novedades y cada partida se sube al cerrarla.\n\nAl crear una partida, elige el grupo para que se comparta.");
        }, err -> {
            busy = false;
            a.refresh();
            a.info("No se pudo unir", err);
        });
    }
}

/** Detalle de un grupo: invitar, sincronizar, miembros y opciones. */
class GroupDetail extends Screen {
    final String id;
    List<Cloud.Member> members;
    boolean loading, syncing;
    String membersError;

    GroupDetail(MainActivity a, String id) {
        super(a);
        this.id = id;
    }

    @Override boolean liveRefresh() { return true; }

    @Override View build() {
        Group g = st.group(id);
        LinearLayout root = Ui.v();
        if (g == null) {
            root.addView(Ui.centerHeader("Grupo", a::pop, null));
            root.addView(Ui.tv("Ya no estás en este grupo.", 15, Theme.MUTED, Theme.REG));
            return root;
        }
        root.addView(Ui.centerHeader(g.name, a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);

        int games = 0;
        for (Model.Game x : st.games.values()) if (g.id.equals(x.group)) games++;
        LinearLayout c = Ui.card();
        Ui.pad(c, 14, 4, 14, 4);
        Player me = st.player(g.me);
        c.addView(Ui.kv("Tú eres", me != null ? me.name : "—"));
        c.addView(Ui.divider());
        c.addView(Ui.kv("Partidas del grupo", String.valueOf(games)));
        c.addView(Ui.divider());
        c.addView(Ui.kv("Última sincronización", g.lastSync == 0 ? "Nunca" : Ui.dateTime(g.lastSync)));
        if (!g.pending.isEmpty() || !g.deleted.isEmpty()) {
            c.addView(Ui.divider());
            c.addView(Ui.kv("Pendiente de subir", Ui.plural(g.pending.size() + g.deleted.size(), "cambio", "cambios")));
        }
        body.addView(c, Ui.cardLp());

        body.addView(Ui.primary("Invitar amigos", () -> GroupsScreen.invite(a, g, false)), lp(MW, WC));
        body.addView(Ui.space(10));
        LinearLayout sync = Ui.iconTextButton(Ico.SYNC, syncing ? "Sincronizando…" : "Sincronizar ahora", Theme.BLUE_TEXT, () -> syncNow(g));
        body.addView(sync, lp(MW, WC));

        body.addView(Ui.section("Móviles en el grupo"));
        if (members == null && !loading && membersError == null) loadMembers(g);
        if (members == null) {
            body.addView(GroupsScreen.note(membersError != null ? membersError : "Cargando…"));
        } else {
            for (Cloud.Member m : members) {
                boolean mine = m.uid.equals(g.uid);
                String nm = m.name == null || m.name.isEmpty() ? "Sin nombre" : m.name;
                LinearLayout r = Ui.h();
                r.setBackground(Ui.round(Theme.SURFACE, 14, Theme.BORDER, 1));
                Ui.pad(r, 14, 10, 10, 10);
                r.setMinimumHeight(dp(54));
                r.addView(Ui.icon(Ico.PEOPLE, mine ? Theme.BLUE_TEXT : Theme.TEXT2, 22));
                TextView t = Ui.one(nm + (mine ? " (este móvil)" : ""), 15.5f, Theme.TEXT, Theme.MED);
                t.setPadding(dp(12), 0, 0, 0);
                r.addView(t, lp(0, WC, 1));
                if (g.owner && !mine) {
                    r.addView(Ui.chip("Quitar", false, () -> a.confirm("Quitar del grupo",
                            "Este móvil dejará de ver las partidas del grupo. Si tiene la invitación podría volver a entrar: cambia la invitación para evitarlo.",
                            "Quitar", true, () -> Sync.run(a, () -> { Cloud.removeMember(Sync.host(a), g, m.uid); return true; },
                                    v -> { members = null; a.refresh(); a.toast(nm + " ya no está en el grupo"); },
                                    err -> a.info("No se pudo quitar", err)))));
                }
                body.addView(r, lpm(MW, WC, 0, 0, 0, 8));
            }
        }

        body.addView(Ui.section("Opciones"));
        if (g.owner) {
            body.addView(Ui.secondary("Cambiar el nombre", Theme.TEXT2, () -> a.prompt("Nombre del grupo", "Nombre", g.name, "Guardar", n -> {
                if (n.isEmpty()) return;
                Sync.run(a, () -> { Cloud.rename(Sync.host(a), g, n); return true; }, v -> a.refresh(), err -> a.info("No se pudo cambiar", err));
            })), lp(MW, WC));
            body.addView(Ui.space(10));
            body.addView(Ui.secondary("Cambiar la invitación", Theme.GOLD, () -> a.confirm("Cambiar la invitación",
                    "La invitación antigua dejará de servir para entrar. Quien ya está en el grupo sigue dentro.", "Cambiar", false,
                    () -> Sync.run(a, () -> { Cloud.rotateKey(Sync.host(a), g); return true; },
                            v -> GroupsScreen.invite(a, g, false), err -> a.info("No se pudo cambiar", err)))), lp(MW, WC));
            body.addView(Ui.space(10));
        }
        body.addView(Ui.secondary("Salir del grupo", Theme.RED_HI, () -> a.confirm("Salir del grupo",
                "Dejarás de recibir sus partidas. Las que ya tienes se quedan en tu móvil.", "Salir", true,
                () -> Sync.run(a, () -> { Cloud.leave(Sync.host(a), g); return true; }, v -> {
                    if (g.id.equals(st.lastGroup)) st.saveLastGroup(null);
                    a.pop();
                    a.toast("Has salido de «" + g.name + "»");
                }, err -> a.info("No se pudo salir", err)))), lp(MW, WC));

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    private void loadMembers(Group g) {
        loading = true;
        membersError = null;
        Sync.run(a, () -> Cloud.members(Sync.host(a), g), list -> {
            loading = false;
            members = list;
            if (a.current() == this) a.refreshIfIdle();
        }, err -> {
            loading = false;
            membersError = err;
            members = null;
            if (a.current() == this) a.refreshIfIdle();
        });
    }

    private void syncNow(Group g) {
        if (syncing) return;
        syncing = true;
        a.refresh();
        Sync.run(a, () -> Cloud.syncGroup(Sync.host(a), g), r -> {
            syncing = false;
            members = null;
            membersError = null;
            a.refresh();
            int n = r.newGames + r.updatedGames;
            a.toast(n + r.uploaded == 0 ? "Todo al día" : (r.newGames > 0 ? Ui.plural(r.newGames, "partida nueva", "partidas nuevas") + " · " : "")
                    + (r.updatedGames > 0 ? Ui.plural(r.updatedGames, "actualizada", "actualizadas") + " · " : "")
                    + Ui.plural(r.uploaded, "subida", "subidas"));
        }, err -> {
            syncing = false;
            a.refresh();
            a.info("No se pudo sincronizar", err);
        });
    }
}
