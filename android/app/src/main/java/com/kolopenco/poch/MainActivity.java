package com.kolopenco.poch;

import static com.kolopenco.poch.Ui.MW;
import static com.kolopenco.poch.Ui.WC;
import static com.kolopenco.poch.Ui.dp;
import static com.kolopenco.poch.Ui.lp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kolopenco.poch.Model.Game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    Store store;
    int tab;
    private final List<ArrayDeque<Screen>> stacks = new ArrayList<>();
    private FrameLayout root, content, overlays;
    private LinearLayout nav, liveBanner;
    private TextView liveText;
    private final Views.NavItem[] navItems = new Views.NavItem[5];
    private Screen shown;
    private int navInset, imeInset;
    private Screen resultTarget;

    static final String[] TAB_LABELS = {"Partida", "Historial", "Jugadores", "Estadísticas", "Ajustes"};
    static final int[] TAB_ICONS = {Ico.TROPHY, Ico.LIST, Ico.PEOPLE, Ico.CHART, Ico.GEAR};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.a = this;
        Theme.D = getResources().getDisplayMetrics().density;
        loadFonts();
        store = new Store(this);

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        LinearLayout main = Ui.v();
        content = new FrameLayout(this);
        main.addView(content, lp(MW, 0, 1));
        main.addView(buildLiveBanner(), lp(MW, WC));
        main.addView(buildNav(), lp(MW, WC));
        root.addView(main, new FrameLayout.LayoutParams(MW, MW));
        overlays = new FrameLayout(this);
        root.addView(overlays, new FrameLayout.LayoutParams(MW, MW));
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom, ime;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets sb = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets im = ins.getInsets(WindowInsets.Type.ime());
                top = sb.top;
                bottom = sb.bottom;
                ime = im.bottom;
                root.setPadding(sb.left, 0, sb.right, 0);
            } else {
                top = ins.getSystemWindowInsetTop();
                bottom = ins.getStableInsetBottom();
                int sys = ins.getSystemWindowInsetBottom();
                ime = sys > bottom + dp(80) ? sys : 0;
            }
            content.setPadding(0, top, 0, 0);
            overlays.setPadding(0, top, 0, 0);
            navInset = bottom;
            imeInset = ime;
            applyBottomInsets();
            return ins;
        });

        for (int i = 0; i < 5; i++) stacks.add(new ArrayDeque<>());
        stacks.get(0).push(new GameTab(this));
        stacks.get(1).push(new HistoryScreen(this));
        stacks.get(2).push(new PlayersScreen(this));
        stacks.get(3).push(new StatsScreen(this));
        stacks.get(4).push(new SettingsScreen(this));
        int start = b == null ? 0 : b.getInt("tab", 0);
        tab = start;
        show();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    private void loadFonts() {
        try {
            Theme.REG = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.otf");
            Theme.MED = Typeface.createFromAsset(getAssets(), "fonts/Inter-Medium.otf");
            Theme.SEMI = Typeface.createFromAsset(getAssets(), "fonts/Inter-SemiBold.otf");
            Theme.BOLD = Typeface.createFromAsset(getAssets(), "fonts/Inter-Bold.otf");
            Theme.BLACK = Typeface.createFromAsset(getAssets(), "fonts/Inter-ExtraBold.otf");
        } catch (Exception ignored) { }
    }

    private void applyBottomInsets() {
        boolean kb = imeInset > navInset + dp(40);
        nav.setVisibility(kb ? View.GONE : View.VISIBLE);
        nav.setPadding(0, dp(4), 0, navInset + dp(4));
        if (kb) liveBanner.setVisibility(View.GONE);
        root.setPadding(root.getPaddingLeft(), 0, root.getPaddingRight(), kb ? imeInset : 0);
        if (!kb) updateChrome();
    }

    // ------------------------------------------------------------------ barra inferior

    private View buildNav() {
        nav = Ui.h();
        nav.setBackgroundColor(Theme.BG2);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            Views.NavItem it = new Views.NavItem(this);
            it.icon = TAB_ICONS[i];
            it.label = TAB_LABELS[i];
            it.setContentDescription(TAB_LABELS[i]);
            it.setOnClickListener(v -> selectTab(idx));
            navItems[i] = it;
            nav.addView(it, lp(0, dp(60), 1));
        }
        LinearLayout wrap = Ui.v();
        wrap.addView(Ui.divider());
        wrap.addView(nav, lp(MW, WC));
        return wrap;
    }

    private View buildLiveBanner() {
        liveBanner = Ui.h();
        liveBanner.setBackground(Ui.round(Theme.BLUE_BG, 12, Theme.alpha(Theme.BLUE, 0.7f), 1));
        Ui.pad(liveBanner, 12, 8, 8, 8);
        View dot = Ui.dot(Theme.BLUE_HI, 9);
        liveBanner.addView(dot);
        liveText = Ui.one("", 14, Theme.TEXT, Theme.MED);
        liveText.setPadding(dp(10), 0, 0, 0);
        liveBanner.addView(liveText, lp(0, WC, 1));
        liveBanner.addView(Ui.one("Ir al marcador", 13, Theme.BLUE_TEXT, Theme.SEMI));
        liveBanner.addView(Ui.icon(Ico.CHEVRON, Theme.BLUE_TEXT, 18));
        Ui.press(liveBanner, 12, () -> {
            stacks.get(0).clear();
            stacks.get(0).push(new GameTab(this));
            selectTab(0);
        });
        LinearLayout.LayoutParams p = Ui.lpm(MW, WC, 12, 0, 12, 8);
        liveBanner.setLayoutParams(p);
        FrameLayout f = new FrameLayout(this);
        f.addView(liveBanner);
        return f;
    }

    /** Actualiza pestañas y el aviso de partida en curso. */
    void updateChrome() {
        Game g = store.active();
        for (int i = 0; i < 5; i++) {
            navItems[i].selected = i == tab;
            navItems[i].live = i == 0 && g != null;
            navItems[i].invalidate();
        }
        boolean showBanner = g != null && tab != 0 && imeInset <= navInset + dp(40);
        liveBanner.setVisibility(showBanner ? View.VISIBLE : View.GONE);
        if (showBanner) {
            int cur = Math.min(Engine.current(g), g.rounds.size() - 1);
            liveText.setText("Partida en curso · Ronda " + (cur + 1) + " · " + g.n() + " jugadores");
        }
    }

    // ------------------------------------------------------------------ navegación

    void selectTab(int i) {
        hideKeyboard();
        if (i == tab) {
            ArrayDeque<Screen> s = stacks.get(i);
            if (s.size() > 1) {
                while (s.size() > 1) s.pop();
                show();
            }
            return;
        }
        tab = i;
        show();
    }

    void push(Screen s) {
        hideKeyboard();
        stacks.get(tab).push(s);
        show();
    }

    void pop() {
        hideKeyboard();
        ArrayDeque<Screen> s = stacks.get(tab);
        if (s.size() > 1) {
            s.pop();
            show();
        }
    }

    void popToRoot(int t) {
        ArrayDeque<Screen> s = stacks.get(t);
        while (s.size() > 1) s.pop();
    }

    /** Cambia de pestaña y abre una pantalla encima de su raíz. */
    void openIn(int t, Screen s) {
        popToRoot(t);
        stacks.get(t).push(s);
        tab = t;
        show();
    }

    /** Vuelve a dibujar la pantalla visible (tras cambios de datos). */
    void refresh() {
        show();
    }

    void show() {
        closeAllOverlays();
        if (shown != null) shown.rememberScroll();
        Screen top = stacks.get(tab).peek();
        content.removeAllViews();
        top.scroll = null;
        View v = top.build();
        content.addView(v, new FrameLayout.LayoutParams(MW, MW));
        top.restoreScroll();
        shown = top;
        updateChrome();
    }

    Screen current() {
        return stacks.get(tab).peek();
    }

    @Override public void onBackPressed() {
        if (overlays.getChildCount() > 0) {
            closeOverlay();
            return;
        }
        Screen top = current();
        if (top != null && top.onBack()) return;
        if (stacks.get(tab).size() > 1) {
            pop();
            return;
        }
        if (tab != 0) {
            selectTab(0);
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ archivos (copias)

    void startForResult(Screen target, Intent i, int req) {
        resultTarget = target;
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            toast("No se pudo abrir el selector de archivos");
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (resultTarget != null) resultTarget.onResult(req, res, data);
    }

    // ------------------------------------------------------------------ ventanas propias

    /** Capa de ventana: fondo oscurecido y contenido. Se apilan. */
    private FrameLayout layer(boolean dimClose) {
        FrameLayout l = new FrameLayout(this);
        View dim = new View(this);
        dim.setBackgroundColor(0xA6000000);
        l.addView(dim, new FrameLayout.LayoutParams(MW, MW));
        if (dimClose) dim.setOnClickListener(v -> closeOverlay());
        else dim.setOnClickListener(v -> { });
        overlays.addView(l, new FrameLayout.LayoutParams(MW, MW));
        dim.setAlpha(0);
        dim.animate().alpha(1).setDuration(160).start();
        return l;
    }

    void closeOverlay() {
        hideKeyboard();
        int n = overlays.getChildCount();
        if (n > 0) overlays.removeViewAt(n - 1);
    }

    void closeAllOverlays() {
        if (overlays != null) overlays.removeAllViews();
    }

    static final class Action {
        final String label;
        final int style; // 0 normal, 1 principal, 2 peligro
        final Runnable run;

        Action(String label, int style, Runnable run) {
            this.label = label;
            this.style = style;
            this.run = run;
        }
    }

    static Action act(String label, Runnable r) { return new Action(label, 0, r); }
    static Action main(String label, Runnable r) { return new Action(label, 1, r); }
    static Action danger(String label, Runnable r) { return new Action(label, 2, r); }

    /** Diálogo centrado con título, mensaje (o vista) y acciones. */
    LinearLayout dialog(String title, CharSequence msg, View extra, Action... actions) {
        hideKeyboard();
        FrameLayout l = layer(true);
        LinearLayout box = Ui.v();
        box.setBackground(Ui.round(Theme.SURFACE, 20, Theme.BORDER2, 1));
        Ui.pad(box, 20, 20, 20, 16);
        box.setElevation(dp(12));
        box.setClickable(true);
        if (title != null) {
            TextView t = Ui.tv(title, 19, Theme.TEXT, Theme.BOLD);
            box.addView(t);
        }
        if (msg != null) {
            TextView m = Ui.tv(msg, 15, Theme.TEXT2, Theme.REG);
            m.setLineSpacing(dp(3), 1);
            m.setPadding(0, dp(title == null ? 0 : 10), 0, 0);
            box.addView(m);
        }
        if (extra != null) {
            box.addView(Ui.space(14));
            box.addView(extra, lp(MW, WC));
        }
        box.addView(Ui.space(18));
        boolean stackButtons = actions.length > 2;
        for (Action ac : actions) if (ac.label.length() > 16) stackButtons = true;
        LinearLayout row = stackButtons ? Ui.v() : Ui.h();
        for (int i = actions.length - 1; i >= 0; i--) {
            // Orden: principal a la derecha (o arriba si van apiladas)
        }
        for (int i = 0; i < actions.length; i++) {
            Action ac = actions[stackButtons ? actions.length - 1 - i : i];
            TextView b;
            Runnable run = () -> {
                closeOverlay();
                if (ac.run != null) ac.run.run();
            };
            if (ac.style == 1) {
                b = Ui.primary(ac.label, run);
                b.setTextSize(16);
            } else if (ac.style == 2) {
                b = Ui.tv(ac.label, 16, Theme.TEXT, Theme.SEMI);
                b.setGravity(Gravity.CENTER);
                b.setMinHeight(dp(50));
                b.setBackground(Ui.grad(0xFFF2584E, 0xFFCF2F27, 14));
                Ui.press(b, 14, run);
            } else {
                b = Ui.tv(ac.label, 16, Theme.TEXT2, Theme.SEMI);
                b.setGravity(Gravity.CENTER);
                b.setMinHeight(dp(50));
                b.setBackground(Ui.round(Theme.SURFACE2, 14, Theme.BORDER2, 1));
                Ui.press(b, 14, run);
            }
            LinearLayout.LayoutParams p = stackButtons ? lp(MW, WC) : lp(0, WC, 1);
            if (i > 0) {
                if (stackButtons) p.topMargin = dp(8); else p.leftMargin = dp(10);
            }
            row.addView(b, p);
        }
        box.addView(row, lp(MW, WC));
        int maxW = Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(420));
        ScrollView sv = new MaxScroll(this, 0.85f);
        sv.addView(box);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(maxW, WC, Gravity.CENTER);
        l.addView(sv, bp);
        sv.setScaleX(0.94f);
        sv.setScaleY(0.94f);
        sv.setAlpha(0);
        sv.animate().scaleX(1).scaleY(1).alpha(1).setDuration(160).start();
        return box;
    }

    void confirm(String title, CharSequence msg, String yes, boolean dangerous, Runnable r) {
        dialog(title, msg, null, act("Cancelar", null), dangerous ? danger(yes, r) : main(yes, r));
    }

    void info(String title, CharSequence msg) {
        dialog(title, msg, null, main("Entendido", null));
    }

    interface TextResult { void done(String s); }

    /** Pide un texto (nombre de jugador, de perfil…). */
    void prompt(String title, String hint, String value, String ok, TextResult r) {
        EditText e = Ui.input(hint, value);
        e.setSelectAllOnFocus(true);
        dialog(title, null, e, act("Cancelar", null), main(ok, () -> r.done(e.getText().toString().trim())));
        e.requestFocus();
        e.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
        }, 220);
    }

    /** Hoja inferior con título y contenido desplazable. Devuelve la capa para poder cerrarla. */
    FrameLayout sheet(String title, View body) {
        hideKeyboard();
        FrameLayout l = layer(true);
        LinearLayout box = Ui.v();
        box.setBackground(Ui.round(Theme.SURFACE, 22, Theme.BORDER2, 1));
        box.setElevation(dp(14));
        box.setClickable(true);
        Ui.pad(box, 0, 8, 0, 0);
        View handle = new View(this);
        handle.setBackground(Ui.round(Theme.BORDER2, 3));
        LinearLayout.LayoutParams hp = lp(dp(40), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(handle, hp);
        LinearLayout head = Ui.h();
        Ui.pad(head, 20, 6, 8, 4);
        head.addView(Ui.one(title, 19, Theme.TEXT, Theme.BOLD), lp(0, WC, 1));
        head.addView(Ui.iconButton(Ico.CLOSE, Theme.TEXT2, 20, this::closeOverlay));
        box.addView(head);
        ScrollView sv = new MaxScroll(this, 0.78f);
        LinearLayout inner = Ui.v();
        Ui.pad(inner, 20, 4, 20, 20 + navInset / Theme.D);
        inner.addView(body, lp(MW, WC));
        sv.addView(inner);
        box.addView(sv, lp(MW, WC));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(MW, WC, Gravity.BOTTOM);
        bp.bottomMargin = -dp(22);
        Ui.pad(box, 0, 8, 0, 22);
        l.addView(box, bp);
        box.setTranslationY(dp(500));
        box.animate().translationY(0).setDuration(220).start();
        return l;
    }

    /** Menú flotante arriba a la derecha (como el «Menú de partida en curso» del mockup). */
    LinearLayout menuPanel() {
        FrameLayout l = layer(true);
        LinearLayout box = Ui.v();
        box.setBackground(Ui.round(Theme.SURFACE, 18, Theme.BORDER2, 1));
        box.setElevation(dp(14));
        box.setClickable(true);
        Ui.pad(box, 10, 6, 10, 10);
        LinearLayout head = Ui.h();
        head.addView(Ui.flex());
        head.addView(Ui.iconButton(Ico.CLOSE, Theme.TEXT2, 20, this::closeOverlay));
        box.addView(head);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(290), WC, Gravity.TOP | Gravity.END);
        bp.topMargin = dp(10);
        bp.rightMargin = dp(10);
        l.addView(box, bp);
        box.setAlpha(0);
        box.setTranslationY(-dp(10));
        box.animate().alpha(1).translationY(0).setDuration(160).start();
        return box;
    }

    /** Fila del menú flotante. */
    View menuItem(LinearLayout box, int icon, int color, String label, String sub, boolean enabled, Runnable r) {
        LinearLayout row = Ui.h();
        Ui.pad(row, 10, 10, 10, 10);
        row.setMinimumHeight(dp(50));
        row.setBackground(Ui.round(Theme.SURFACE2, 12));
        ImageView ic = Ui.icon(icon, color, 22);
        row.addView(ic);
        LinearLayout col = Ui.v();
        col.setPadding(dp(14), 0, 0, 0);
        TextView t = Ui.one(label, 15.5f, color == Theme.RED ? Theme.RED_HI : Theme.TEXT, Theme.MED);
        col.addView(t);
        if (sub != null) {
            TextView s = Ui.one(sub, 12, Theme.MUTED, Theme.REG);
            s.setPadding(0, dp(3), 0, 0);
            col.addView(s);
        }
        row.addView(col, lp(0, WC, 1));
        if (!enabled) row.setAlpha(0.5f);
        Ui.press(row, 12, () -> {
            closeOverlay();
            r.run();
        });
        LinearLayout.LayoutParams p = lp(MW, WC);
        p.topMargin = dp(6);
        box.addView(row, p);
        return row;
    }

    /** Aviso breve sobre la barra inferior. */
    void toast(String msg) {
        TextView t = Ui.tv(msg, 14.5f, Theme.TEXT, Theme.MED);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(0xF21F2B44, 14, Theme.BORDER2, 1));
        Ui.pad(t, 16, 12, 16, 12);
        t.setElevation(dp(10));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(WC, WC, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.bottomMargin = dp(84) + navInset;
        p.leftMargin = dp(24);
        p.rightMargin = dp(24);
        root.addView(t, p);
        t.setAlpha(0);
        t.animate().alpha(1).setDuration(150).start();
        t.postDelayed(() -> t.animate().alpha(0).setDuration(250).withEndAction(() -> root.removeView(t)).start(), 2300);
    }

    void hideKeyboard() {
        View f = getCurrentFocus();
        if (f == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        f.clearFocus();
    }

    /** ScrollView con altura máxima (fracción de la pantalla). */
    static final class MaxScroll extends ScrollView {
        final float frac;

        MaxScroll(Context c, float frac) {
            super(c);
            this.frac = frac;
            setOverScrollMode(OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(false);
        }

        @Override protected void onMeasure(int ws, int hs) {
            int max = (int) (getResources().getDisplayMetrics().heightPixels * frac);
            int mode = MeasureSpec.getMode(hs);
            int size = MeasureSpec.getSize(hs);
            if (mode == MeasureSpec.UNSPECIFIED || size > max) hs = MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
            super.onMeasure(ws, hs);
        }
    }

    /** ScrollView estándar de pantalla, sin efecto de rebote. */
    ScrollView screenScroll(View body) {
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        sv.addView(body, new ViewGroup.LayoutParams(MW, WC));
        return sv;
    }
}
