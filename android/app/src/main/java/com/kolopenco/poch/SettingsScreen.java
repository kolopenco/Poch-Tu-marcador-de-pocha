package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;
import static com.kolopenco.poch.Ui.lpm;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Rules;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;

/** Ajustes: lista de apartados. */
class SettingsScreen extends Screen {
    SettingsScreen(MainActivity a) { super(a); }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.header(Ui.icon(Ico.GEAR, Theme.TEXT, 26), "Ajustes", null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 2, 16, 20);
        Rules def = st.defaultRules();
        body.addView(Ui.listRow(badge(Ico.SLIDERS, Theme.BLUE_HI), "Perfiles de reglas",
                Ui.plural(st.profiles.size(), "perfil", "perfiles") + " · por defecto: " + def.name,
                () -> a.push(new ProfilesScreen(a))));
        long lb = st.lastBackup();
        body.addView(Ui.listRow(badge(Ico.BACKUP, Theme.GREEN), "Copias de seguridad",
                lb == 0 ? "Nunca se ha hecho una copia" : "Última copia: " + Ui.dateTime(lb),
                () -> a.push(new BackupScreen(a))));
        body.addView(Ui.listRow(badge(Ico.PEOPLE, Theme.ORANGE), "Jugadores",
                Ui.plural(st.players.size(), "jugador registrado", "jugadores registrados"), () -> a.selectTab(2)));
        body.addView(Ui.listRow(badge(Ico.INFO, Theme.TEXT2), "Acerca de Poch!",
                "Versión " + BuildConfig.VERSION_NAME, this::about));
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    static View badge(int icon, int color) {
        FrameLayout f = new FrameLayout(Ui.a);
        f.setBackground(Ui.round(Theme.alpha(color, 0.16f), 11));
        f.addView(Ui.icon(icon, color, 22), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        f.setLayoutParams(lp(dp(40), dp(40)));
        return f;
    }

    private void about() {
        a.info("Poch! " + BuildConfig.VERSION_NAME,
                "Marcador, historial y estadísticas para partidas de La Pocha.\n\nGratis, sin anuncios y sin Internet: todos los datos se guardan en el móvil. Haz copias de seguridad de vez en cuando.\n\nTipografía Inter (SIL Open Font License).");
    }
}

/** Lista de perfiles de reglas. */
class ProfilesScreen extends Screen {
    ProfilesScreen(MainActivity a) { super(a); }

    /** Guarda el perfil: sustituye el que tenga el mismo id o lo añade. */
    private void upsert(Rules edited) {
        Rules old = st.profile(edited.id);
        if (old != null) st.profiles.set(st.profiles.indexOf(old), edited);
        else st.profiles.add(edited);
        st.saveProfiles();
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Perfiles de reglas", a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);
        TextView t = Ui.tv("Guarda las variantes que jugáis. Al crear una partida se copia el perfil elegido: cambiarlo después no afecta a partidas ya empezadas.",
                13, Theme.MUTED, Theme.REG);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(dp(2), 0, 0, dp(12));
        body.addView(t);
        for (Rules r : st.profiles) {
            boolean def = r.id.equals(st.defaultProfile);
            LinearLayout row = Ui.listRow(SettingsScreen.badge(def ? Ico.STAR : Ico.SLIDERS, def ? Theme.GOLD : Theme.BLUE_HI),
                    r.name + (def ? " (por defecto)" : ""), RulesText.summary(r), () ->
                            a.push(new RulesEditor(a, r.copy(), true, this::upsert)));
            body.addView(row);
        }
        body.addView(Ui.space(6));
        body.addView(Ui.iconTextButton(Ico.PLUS, "Nuevo perfil", Theme.BLUE_TEXT, () -> {
            Rules r = st.defaultRules().copy();
            r.id = Store.newId("r");
            r.name = "Mi variante";
            a.push(new RulesEditor(a, r, true, this::upsert));
        }), lp(MW, WC));
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }
}

/** Editor de reglas (de un perfil o solo para una partida). */
class RulesEditor extends Screen {
    final Rules r;
    final boolean profile;
    final Consumer<Rules> onSave;
    int example = 5;
    private TextView preview, table;

    RulesEditor(MainActivity a, Rules r, boolean profile, Consumer<Rules> onSave) {
        super(a);
        this.r = r;
        this.profile = profile;
        this.onSave = onSave;
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader(profile ? "Editar perfil" : "Reglas de la partida", a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 2, 16, 20);

        if (profile) {
            EditText name = Ui.input("Nombre del perfil", r.name);
            name.addTextChangedListener(watch(s -> r.name = s));
            body.addView(name, lpm(MW, WC, 0, 4, 0, 0));
        }

        // Rondas
        body.addView(Ui.section("Rondas"));
        LinearLayout c1 = Ui.card();
        c1.addView(Ui.settingRow("Baraja", r.deck == 48 ? "Española de 48 (con 8 y 9)" : "Española de 40", null));
        c1.addView(Ui.segmented(new String[]{"40 cartas", "48 cartas"}, r.deck == 48 ? 1 : 0, i -> {
            r.deck = i == 1 ? 48 : 40;
            update();
        }), lpm(MW, WC, 0, 0, 0, 6));
        c1.addView(Ui.divider());
        c1.addView(Ui.settingRow("Secuencia", null, null));
        boolean custom = "custom".equals(r.seqType);
        c1.addView(Ui.segmented(new String[]{"Estándar", "Personalizada"}, custom ? 1 : 0, i -> {
            r.seqType = i == 1 ? "custom" : "standard";
            if (i == 1 && r.customSeq.trim().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int c : Engine.sequence(withStd(), example)) sb.append(sb.length() > 0 ? ", " : "").append(c);
                r.customSeq = sb.toString();
            }
            a.refresh();
        }), lpm(MW, WC, 0, 0, 0, 6));
        if (custom) {
            EditText seq = Ui.input("1, 2, 3, 4, 4, 3, 2, 1", r.customSeq);
            seq.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            seq.addTextChangedListener(watch(s -> {
                r.customSeq = s;
                update();
            }));
            c1.addView(seq, lpm(MW, WC, 0, 4, 0, 4));
            c1.addView(note("Cartas por jugador en cada ronda, separadas por comas. Si una ronda pide más cartas de las que hay, se limita a la baraja."));
            c1.addView(Ui.divider());
            c1.addView(Ui.settingRow("Subastadas las rondas con más cartas", null,
                    Ui.toggle(r.auctionMax, on -> { r.auctionMax = on; update(); })));
        } else {
            c1.addView(Ui.divider());
            c1.addView(Ui.settingRow("Máximo de cartas por jugador", "Se limita a baraja ÷ jugadores",
                    Ui.stepper(r.maxCards, 1, 12, null, v -> { r.maxCards = v; update(); })));
            c1.addView(Ui.divider());
            c1.addView(Ui.settingRow("Repeticiones del máximo", "0 = una por jugador",
                    Ui.stepper(r.repeatMax, 0, 12, null, v -> { r.repeatMax = v; update(); })));
            c1.addView(Ui.divider());
            c1.addView(Ui.settingRow("Rondas del máximo subastadas", null,
                    Ui.toggle(r.auctionMax, on -> { r.auctionMax = on; update(); })));
        }
        c1.addView(Ui.divider());
        c1.addView(Ui.settingRow("Ejemplo con", null, Ui.stepper(example, 2, Engine.MAX_PLAYERS, " jug.", v -> { example = v; update(); })));
        preview = Ui.tv("", 13, Theme.TEXT2, Theme.REG);
        preview.setLineSpacing(dp(2), 1);
        preview.setPadding(0, dp(4), 0, dp(4));
        c1.addView(preview);
        body.addView(c1, Ui.cardLp());

        // Puntuación
        body.addView(Ui.section("Puntuación"));
        LinearLayout c2 = Ui.card();
        c2.addView(Ui.settingRow("Base por acertar", null, Ui.stepper(r.base, 0, 50, null, v -> { r.base = v; update(); })));
        c2.addView(Ui.divider());
        c2.addView(Ui.settingRow("Puntos por baza acertada", null, Ui.stepper(r.perTrick, 0, 30, null, v -> { r.perTrick = v; update(); })));
        c2.addView(Ui.divider());
        c2.addView(Ui.settingRow("Penalización por baza de diferencia", null, Ui.stepper(r.penalty, 0, 30, null, v -> { r.penalty = v; update(); })));
        body.addView(c2, Ui.cardLp());

        // Multiplicadores
        body.addView(Ui.section("Multiplicadores por palo"));
        LinearLayout c3 = Ui.card();
        for (int i = 0; i < 4; i++) {
            final int k = i;
            if (i > 0) c3.addView(Ui.divider());
            LinearLayout row = Ui.h();
            Ui.pad(row, 0, 8, 0, 8);
            Ico ic = new Ico(Views.MultChip.suitIcon(Model.SUITS[i]), Views.MultChip.suitColor(Model.SUITS[i]));
            ic.rich = true;
            row.addView(Ui.icon(ic, 24));
            TextView t = Ui.one(Model.SUIT_NAMES[i], 15, Theme.TEXT, Theme.MED);
            t.setPadding(dp(10), 0, 0, 0);
            row.addView(t, lp(0, WC, 1));
            LinearLayout f = Ui.segmented(new String[]{"×2", "×3"}, r.multF[i] == 3 ? 1 : 0, j -> {
                r.multF[k] = j == 1 ? 3 : 2;
                update();
            });
            row.addView(f, lp(dp(100), WC));
            row.addView(Ui.hspace(10));
            row.addView(Ui.toggle(r.multOn[i], on -> { r.multOn[k] = on; update(); }));
            c3.addView(row);
        }
        c3.addView(Ui.divider());
        c3.addView(Ui.settingRow("Multiplicar también la base", "Por defecto solo se multiplican bazas y penalizaciones",
                Ui.toggle(r.multBase, on -> { r.multBase = on; update(); })));
        table = Ui.tv("", 13, Theme.TEXT2, Theme.REG);
        table.setLineSpacing(dp(3), 1);
        table.setPadding(0, dp(6), 0, dp(4));
        c3.addView(table);
        body.addView(c3, Ui.cardLp());

        // Pujas y empates
        body.addView(Ui.section("Pujas y empates"));
        LinearLayout c4 = Ui.card();
        c4.addView(Ui.settingRow("Prohibir que las pedidas sumen las bazas",
                "El último en pedir no puede cuadrar la suma. En el modo sencillo, para guardar alguien tiene que haber fallado.",
                Ui.toggle(r.forbidSum, on -> r.forbidSum = on)));
        c4.addView(Ui.divider());
        c4.addView(Ui.settingRow("Si hay empate en cabeza", null, null));
        c4.addView(Ui.segmented(new String[]{"Victoria compartida", "Desempate"}, Model.TIE_BREAK.equals(r.tie) ? 1 : 0, i ->
                r.tie = i == 1 ? Model.TIE_BREAK : Model.TIE_SHARED), lpm(MW, WC, 0, 0, 0, 4));
        c4.addView(note("Compartida: cada empatado suma 1/k de victoria. Desempate: rondas de una carta solo entre los empatados hasta que haya un ganador. Si acabáis empatados, la app pregunta igualmente qué hacéis."));
        body.addView(c4, Ui.cardLp());

        if (profile) {
            LinearLayout acts = Ui.h();
            boolean isDef = r.id.equals(st.defaultProfile);
            acts.addView(Ui.iconTextButton(Ico.STAR, isDef ? "Es el perfil por defecto" : "Usar por defecto",
                    Theme.GOLD, () -> {
                        save(false);
                        st.defaultProfile = r.id;
                        st.saveProfiles();
                        a.toast("Perfil por defecto: " + r.name);
                        a.refresh();
                    }), lp(0, WC, 1));
            if (st.profiles.size() > 1 && st.profile(r.id) != null) {
                LinearLayout.LayoutParams p = lp(WC, WC);
                p.leftMargin = dp(10);
                acts.addView(Ui.iconTextButton(Ico.TRASH, "Borrar", Theme.RED_HI, () ->
                        a.confirm("Borrar perfil", "Se borrará «" + r.name + "». Las partidas jugadas con él no cambian.",
                                "Borrar", true, () -> {
                                    st.profiles.remove(st.profile(r.id));
                                    if (r.id.equals(st.defaultProfile)) st.defaultProfile = st.profiles.get(0).id;
                                    st.saveProfiles();
                                    a.pop();
                                })), p);
            }
            body.addView(acts, lpm(MW, WC, 0, 6, 0, 0));
        }

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));

        LinearLayout foot = Ui.v();
        Ui.pad(foot, 16, 6, 16, 12);
        foot.addView(Ui.primary(profile ? "Guardar perfil" : "Usar estas reglas", () -> save(true)), lp(MW, WC));
        root.addView(foot);
        update();
        return root;
    }

    private Rules withStd() {
        Rules x = r.copy();
        x.seqType = "standard";
        return x;
    }

    private void save(boolean leave) {
        if (r.name == null || r.name.trim().isEmpty()) r.name = "Sin nombre";
        if ("custom".equals(r.seqType) && Engine.sequence(withCustomOnly(), 4).isEmpty()) {
            a.toast("Escribe la secuencia de rondas");
            return;
        }
        onSave.accept(r.copy());
        if (leave) {
            a.pop();
            a.toast(profile ? "Perfil guardado" : "Reglas aplicadas a esta partida");
        }
    }

    private Rules withCustomOnly() {
        return r;
    }

    private void update() {
        if (preview == null) return;
        List<Integer> seq = Engine.sequence(r, example);
        int max = 0, auc = 0;
        for (int c : seq) max = Math.max(max, c);
        if (r.auctionMax) for (int c : seq) if (c == max) auc++;
        StringBuilder sb = new StringBuilder();
        for (int c : seq) sb.append(sb.length() > 0 ? "-" : "").append(c);
        preview.setText(seq.size() + " rondas, " + auc + " subastadas:\n" + sb);
        int f = 2;
        String suit = "Oros";
        for (int i = 0; i < 4; i++) if (r.multOn[i]) { f = r.multF[i]; suit = Model.SUIT_NAMES[i]; break; }
        int base = r.base, bx = r.base * (r.multBase ? f : 1);
        table.setText("Ejemplos (normal · " + suit + " ×" + f + "):\n"
                + "Acertar 0: +" + base + " · +" + bx + "\n"
                + "Acertar 3: +" + (base + 3 * r.perTrick) + " · +" + (bx + 3 * r.perTrick * f) + "\n"
                + "Fallar por 1: −" + r.penalty + " · −" + (r.penalty * f) + "\n"
                + "Fallar por 2: −" + (2 * r.penalty) + " · −" + (2 * r.penalty * f));
    }

    private TextView note(String s) {
        TextView t = Ui.tv(s, 12.5f, Theme.MUTED, Theme.REG);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(0, dp(2), 0, dp(6));
        return t;
    }

    static TextWatcher watch(Consumer<String> c) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) { c.accept(s.toString()); }
        };
    }
}

/** Copias de seguridad: exportar, importar con verificación y volver atrás. */
class BackupScreen extends Screen {
    static final int REQ_EXPORT = 41, REQ_IMPORT = 42;

    BackupScreen(MainActivity a) { super(a); }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Copias de seguridad", a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);

        LinearLayout info = Ui.card();
        int closed = st.closedGames().size();
        info.addView(Ui.kv("Jugadores", String.valueOf(st.players.size())));
        info.addView(Ui.divider());
        info.addView(Ui.kv("Partidas", st.games.size() + (st.active() != null ? " (1 en curso)" : "")));
        info.addView(Ui.divider());
        info.addView(Ui.kv("Partidas en estadísticas", String.valueOf(closed)));
        info.addView(Ui.divider());
        info.addView(Ui.kv("Perfiles de reglas", String.valueOf(st.profiles.size())));
        info.addView(Ui.divider());
        long lb = st.lastBackup();
        info.addView(Ui.kv("Última copia", lb == 0 ? "Nunca" : Ui.dateTime(lb)));
        body.addView(info, Ui.cardLp());

        TextView t = Ui.tv("La copia es un archivo con jugadores, partidas (también la que está en curso), reglas y estadísticas. Puedes guardarla en el móvil o en Google Drive.",
                13, Theme.MUTED, Theme.REG);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(dp(2), dp(2), 0, dp(14));
        body.addView(t);

        body.addView(Ui.primary("Exportar copia", this::export), lp(MW, WC));
        body.addView(Ui.space(10));
        body.addView(Ui.iconTextButton(Ico.UPLOAD, "Importar copia", Theme.BLUE_TEXT, this::pickImport), lp(MW, WC));

        if (st.hasPrevData()) {
            body.addView(Ui.section("Volver atrás"));
            LinearLayout c = Ui.card();
            TextView pt = Ui.tv("Antes de la última restauración (" + Ui.dateTime(st.prevDataTime())
                    + ") se guardaron tus datos de entonces. Puedes recuperarlos si la copia no era la buena.",
                    13.5f, Theme.TEXT2, Theme.REG);
            pt.setLineSpacing(dp(2), 1);
            c.addView(pt);
            c.addView(Ui.space(10));
            c.addView(Ui.iconTextButton(Ico.UNDO, "Recuperar datos anteriores", Theme.YELLOW, this::restorePrev), lp(MW, WC));
            body.addView(c, Ui.cardLp());
        }
        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        return root;
    }

    private void export() {
        Calendar c = Calendar.getInstance();
        String name = String.format(java.util.Locale.ROOT, "poch-copia-%04d-%02d-%02d.json",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, name);
        a.startForResult(this, i, REQ_EXPORT);
    }

    private void pickImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        a.startForResult(this, i, REQ_IMPORT);
    }

    @Override void onResult(int req, int res, Intent data) {
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            try (OutputStream os = a.getContentResolver().openOutputStream(uri, "wt")) {
                String json = st.exportJson();
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
                // Verificación: la copia escrita se puede leer de nuevo
                Backup.Data check = Backup.parse(read(uri));
                if (check.games.size() != st.games.size() || check.players.size() != st.players.size())
                    throw new Exception("La copia escrita no coincide");
                st.markBackup();
                a.refresh();
                a.info("Copia guardada", "Copia comprobada: " + Ui.plural(check.players.size(), "jugador", "jugadores")
                        + " y " + Ui.plural(check.games.size(), "partida", "partidas") + ".");
            } catch (Exception e) {
                a.info("No se pudo guardar", "Error al escribir la copia: " + e.getMessage());
            }
        } else if (req == REQ_IMPORT) {
            try {
                Backup.Data d = Backup.parse(read(uri));
                confirmImport(d);
            } catch (Exception e) {
                a.info("No se puede importar", e.getMessage());
            }
        }
    }

    private String read(Uri uri) throws Exception {
        try (InputStream is = a.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("No se pudo abrir el archivo");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) > 0) {
                sb.append(buf, 0, n);
                if (sb.length() > 40_000_000) throw new Exception("El archivo es demasiado grande");
            }
            return sb.toString();
        }
    }

    private void confirmImport(Backup.Data d) {
        String msg = "Copia" + (d.exported > 0 ? " del " + Ui.dateTime(d.exported) : "") + ":\n"
                + "\u2022 " + Ui.plural(d.players.size(), "jugador", "jugadores") + "\n"
                + "\u2022 " + Ui.plural(d.games.size(), "partida", "partidas") + (d.activeGame != null ? " (1 en curso)" : "") + "\n"
                + "\u2022 " + Ui.plural(d.profiles.size(), "perfil de reglas", "perfiles de reglas") + "\n\n"
                + "Mezclar: añade sus jugadores y partidas a los tuyos (podrás unir cada jugador con uno tuyo). "
                + "Para pasaros partidas entre vosotros.\n\n"
                + "Sustituir: borra tus datos actuales y deja solo los de la copia.";
        a.dialog("Importar copia", msg, null,
                MainActivity.act("Cancelar", null),
                MainActivity.danger("Sustituir todo", () -> a.confirm("Sustituir todo",
                        "Se reemplazarán TODOS tus datos (" + Ui.plural(st.players.size(), "jugador", "jugadores") + ", "
                                + Ui.plural(st.games.size(), "partida", "partidas") + "). Se guardan aparte por si quieres volver atrás.",
                        "Sustituir", true, () -> apply(d, true, "Copia restaurada"))),
                MainActivity.main("Mezclar", () -> a.push(new MergeScreen(a, d))));
    }

    private void apply(Backup.Data d, boolean keepPrev, String title) {
        try {
            boolean ok = st.replaceAll(d, keepPrev);
            a.popToRoot(0);
            stacksRefresh();
            if (ok) {
                a.info(title, "Restauración verificada: " + Ui.plural(st.players.size(), "jugador", "jugadores") + ", "
                        + Ui.plural(st.games.size(), "partida", "partidas") + " y "
                        + Ui.plural(st.profiles.size(), "perfil", "perfiles") + ". Todo coincide con la copia.");
            } else {
                a.info("Revisa los datos", "Los datos se han restaurado, pero la comprobación final no coincide del todo con la copia. Si ves algo raro, usa «Recuperar datos anteriores».");
            }
        } catch (Exception e) {
            a.info("No se pudo restaurar", "Error: " + e.getMessage());
        }
    }

    private void stacksRefresh() {
        a.refresh();
    }

    private void restorePrev() {
        try {
            Backup.Data d = st.prevData();
            a.confirm("Recuperar datos anteriores",
                    "Se volverá a los datos que tenías antes de la última restauración (" + Ui.plural(d.players.size(), "jugador", "jugadores")
                            + ", " + Ui.plural(d.games.size(), "partida", "partidas") + "). Lo actual se guarda aparte.",
                    "Recuperar", true, () -> apply(d, true, "Datos recuperados"));
        } catch (Exception e) {
            a.info("No disponible", "No se pudieron leer los datos anteriores.");
            st.dropPrevData();
            a.refresh();
        }
    }
}

/** Mezclar una copia con mis datos: emparejar cada jugador de la copia con uno mío o añadirlo como nuevo. */
class MergeScreen extends Screen {
    final Backup.Data in;
    final java.util.Map<String, String> map;

    MergeScreen(MainActivity a, Backup.Data in) {
        super(a);
        this.in = in;
        this.map = Backup.suggest(a.store.snapshot(), in);
    }

    @Override View build() {
        LinearLayout root = Ui.v();
        root.addView(Ui.centerHeader("Mezclar copia", a::pop, null));
        LinearLayout body = Ui.v();
        Ui.pad(body, 16, 4, 16, 20);

        int games = 0, live = 0;
        for (Model.Game g : in.games) if (Model.ACTIVE.equals(g.status)) live++; else games++;
        TextView t = Ui.tv("La copia trae " + Ui.plural(games, "partida", "partidas")
                        + (live > 0 ? " (y " + Ui.plural(live, "partida en curso", "partidas en curso") + ", que no se importan)" : "")
                        + ". Une cada jugador de la copia con el tuyo si es la misma persona, así sus estadísticas se suman. "
                        + "Si no lo tienes, se añade como jugador nuevo.",
                13.5f, Theme.TEXT2, Theme.REG);
        t.setLineSpacing(dp(2), 1);
        t.setPadding(dp(2), 0, 0, dp(6));
        body.addView(t);
        body.addView(Ui.section("Jugadores de la copia"));

        java.util.Set<String> mineIds = new java.util.HashSet<>();
        for (Model.Player p : st.players) mineIds.add(p.id);
        for (Model.Player p : in.players) {
            String target = map.get(p.id);
            boolean sameId = mineIds.contains(p.id) && p.id.equals(target);
            Model.Player mine = target == null ? null : st.player(target);
            LinearLayout row = Ui.h();
            row.setBackground(Ui.round(Theme.SURFACE, 14, mine != null ? Theme.alpha(Theme.GREEN, 0.6f) : Theme.BORDER, 1));
            Ui.pad(row, 12, 10, 10, 10);
            row.setMinimumHeight(dp(64));
            row.addView(Ui.dot(p.color, 26));
            LinearLayout col = Ui.v();
            col.setPadding(dp(12), 0, dp(6), 0);
            col.addView(Ui.one(p.name, 16, Theme.TEXT, Theme.SEMI));
            TextView sub;
            if (mine != null) {
                sub = Ui.one((sameId ? "Es tu jugador " : "Se une con ") + mine.name, 13, Theme.GREEN, Theme.MED);
            } else {
                sub = Ui.one("Se añade como jugador nuevo", 13, Theme.BLUE_TEXT, Theme.MED);
            }
            sub.setPadding(0, dp(4), 0, 0);
            col.addView(sub);
            row.addView(col, lp(0, WC, 1));
            if (mine != null) {
                row.addView(Ui.dot(mine.color, 18));
                row.addView(Ui.hspace(8));
            }
            row.addView(Ui.icon(Ico.CHEVRON_DOWN, Theme.TEXT2, 20));
            Ui.press(row, 14, () -> choose(p));
            body.addView(row, lpm(MW, WC, 0, 0, 0, 8));
        }
        if (in.players.isEmpty()) body.addView(Ui.tv("La copia no tiene jugadores.", 14, Theme.MUTED, Theme.REG));

        ScrollView sv = a.screenScroll(body);
        scroll = sv;
        root.addView(sv, lp(MW, 0, 1));
        LinearLayout foot = Ui.v();
        Ui.pad(foot, 16, 6, 16, 12);
        foot.addView(Ui.primary("Mezclar", this::doMerge), lp(MW, WC));
        root.addView(foot);
        return root;
    }

    /** Hoja para elegir con qué jugador mío se une (o nuevo). */
    private void choose(Model.Player p) {
        LinearLayout body = Ui.v();
        String cur = map.get(p.id);
        body.addView(SetupScreen.option("Añadir como jugador nuevo", "Se crea «" + p.name + "» con sus partidas.", cur == null, () -> {
            map.put(p.id, null);
            a.closeOverlay();
            a.refresh();
        }));
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, String> e : map.entrySet())
            if (e.getValue() != null && !e.getKey().equals(p.id)) taken.add(e.getValue());
        for (Model.Player q : st.playersSorted()) {
            boolean busy = taken.contains(q.id);
            String other = null;
            if (busy) for (Model.Player x : in.players) if (q.id.equals(map.get(x.id))) other = x.name;
            LinearLayout o = SetupScreen.option("Es " + q.name, busy ? "Ya unido con «" + other + "»" : null, q.id.equals(cur), () -> {
                if (busy) {
                    a.toast(q.name + " ya está unido con otro jugador de la copia");
                    return;
                }
                map.put(p.id, q.id);
                a.closeOverlay();
                a.refresh();
            });
            if (busy) o.setAlpha(0.5f);
            body.addView(o);
        }
        a.sheet("¿Quién es " + p.name + "?", body);
    }

    private void doMerge() {
        try {
            Backup.MergeResult r = Backup.merge(st.snapshot(), in, map);
            boolean ok = st.replaceAll(r.data, true);
            a.popToRoot(a.tab);
            a.refresh();
            StringBuilder sb = new StringBuilder();
            sb.append("\u2022 ").append(Ui.plural(r.newGames, "partida nueva", "partidas nuevas")).append('\n');
            if (r.updatedGames > 0) sb.append("\u2022 ").append(Ui.plural(r.updatedGames, "partida actualizada", "partidas actualizadas")).append('\n');
            if (r.sameGames > 0) sb.append("\u2022 ").append(Ui.plural(r.sameGames, "partida que ya tenías", "partidas que ya tenías")).append('\n');
            if (r.skippedActive > 0) sb.append("\u2022 ").append(Ui.plural(r.skippedActive, "partida en curso no importada", "partidas en curso no importadas")).append('\n');
            sb.append("\u2022 ").append(Ui.plural(r.newPlayers, "jugador nuevo", "jugadores nuevos")).append(", ")
                    .append(r.mergedPlayers).append(" unidos con los tuyos");
            if (r.newProfiles > 0) sb.append("\n\u2022 ").append(Ui.plural(r.newProfiles, "perfil de reglas nuevo", "perfiles de reglas nuevos"));
            if (!ok) sb.append("\n\nLa comprobación final no coincide del todo. Si ves algo raro, usa «Recuperar datos anteriores» en Copias de seguridad.");
            else sb.append("\n\nSi algo no te convence, en Copias de seguridad puedes «Recuperar datos anteriores».");
            a.info("Copia mezclada", sb.toString());
        } catch (Exception e) {
            a.info("No se pudo mezclar", e.getMessage());
        }
    }
}
