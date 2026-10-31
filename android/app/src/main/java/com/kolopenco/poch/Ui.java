package com.kolopenco.poch;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.function.IntConsumer;

/** Piezas de interfaz hechas por código con el estilo del mockup. */
final class Ui {
    private Ui() {}

    static MainActivity a;

    static final int MW = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    static int dp(float v) { return Theme.dpi(v); }

    // ------------------------------------------------------------------ fechas

    private static final String[] MONTHS = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

    static String date(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    static String dateTime(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return date(t) + " · " + String.format(java.util.Locale.ROOT, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static String fmt1(double v) {
        if (Double.isNaN(v)) return "—";
        String s = String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
        return s.endsWith(",0") ? s.substring(0, s.length() - 2) : s;
    }

    static String pct(double v) {
        return Double.isNaN(v) ? "—" : Math.round(v) + "%";
    }

    static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    // ------------------------------------------------------------------ fondos

    static GradientDrawable round(int fill, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(rDp));
        return g;
    }

    static GradientDrawable round(int fill, float rDp, int stroke, float swDp) {
        GradientDrawable g = round(fill, rDp);
        g.setStroke(Math.max(1, dp(swDp)), stroke);
        return g;
    }

    static GradientDrawable grad(int top, int bottom, float rDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(dp(rDp));
        return g;
    }

    static GradientDrawable oval(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    /** Efecto al pulsar (onda) con las esquinas indicadas. */
    static void press(View v, float rDp, Runnable r) {
        v.setForeground(new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), null, round(0xFFFFFFFF, rDp)));
        v.setClickable(true);
        v.setOnClickListener(x -> r.run());
    }

    // ------------------------------------------------------------------ disposición

    static LinearLayout v() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout h() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams lpm(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    static View space(float hDp) {
        View s = new View(a);
        s.setLayoutParams(lp(1, dp(hDp)));
        return s;
    }

    static View hspace(float wDp) {
        View s = new View(a);
        s.setLayoutParams(lp(dp(wDp), 1));
        return s;
    }

    static View flex() {
        View s = new View(a);
        s.setLayoutParams(lp(0, 1, 1));
        return s;
    }

    static View divider() {
        View d = new View(a);
        d.setBackgroundColor(Theme.BORDER);
        d.setLayoutParams(lp(MW, Math.max(1, dp(0.8f))));
        return d;
    }

    static void pad(View v, float l, float t, float r, float b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    // ------------------------------------------------------------------ textos

    static TextView tv(CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView one(CharSequence s, float sp, int color, Typeface tf) {
        TextView t = tv(s, sp, color, tf);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static TextView section(String s) {
        TextView t = tv(s, 15.5f, Theme.TEXT, Theme.SEMI);
        t.setLayoutParams(lpm(MW, WC, 2, 18, 2, 10));
        return t;
    }

    static ImageView icon(int type, int color, float sizeDp) {
        return icon(new Ico(type, color), sizeDp);
    }

    static ImageView icon(Drawable d, float sizeDp) {
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(d);
        iv.setLayoutParams(lp(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    /** Botón redondo de icono (atrás, menú, editar…). */
    static FrameLayout iconButton(int type, int color, float iconDp, Runnable r) {
        FrameLayout f = new FrameLayout(a);
        f.setLayoutParams(lp(dp(44), dp(44)));
        ImageView iv = icon(type, color, iconDp);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER);
        f.addView(iv, p);
        press(f, 22, r);
        return f;
    }

    // ------------------------------------------------------------------ cabecera

    /**
     * Cabecera del mockup: icono o flecha atrás, título grande, subtítulo opcional y acciones a la derecha.
     */
    static LinearLayout header(View lead, String title, String sub, View... right) {
        LinearLayout row = h();
        pad(row, 12, 10, 8, 8);
        row.setMinimumHeight(dp(64));
        if (lead != null) {
            row.addView(lead);
            row.addView(hspace(lead instanceof FrameLayout ? 2 : 12));
        } else {
            row.addView(hspace(6));
        }
        LinearLayout col = v();
        TextView t = one(title, 22, Theme.TEXT, Theme.BOLD);
        col.addView(t);
        if (sub != null) {
            TextView s = one(sub, 13, Theme.TEXT2, Theme.REG);
            s.setPadding(0, dp(3), 0, 0);
            col.addView(s);
        }
        row.addView(col, lp(0, WC, 1));
        for (View v : right) if (v != null) row.addView(v);
        return row;
    }

    /** Cabecera centrada con flecha atrás (pantallas de detalle). */
    static FrameLayout centerHeader(String title, Runnable back, View right) {
        FrameLayout f = new FrameLayout(a);
        f.setMinimumHeight(dp(58));
        pad(f, 4, 6, 4, 2);
        if (back != null) {
            FrameLayout b = iconButton(Ico.BACK, Theme.TEXT, 24, back);
            f.addView(b, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL));
        }
        TextView t = one(title, 19, Theme.TEXT, Theme.BOLD);
        t.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(WC, WC, Gravity.CENTER);
        tp.leftMargin = dp(56);
        tp.rightMargin = dp(56);
        f.addView(t, tp);
        if (right != null) f.addView(right, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL));
        return f;
    }

    // ------------------------------------------------------------------ botones

    static TextView primary(String text, Runnable r) {
        TextView b = tv(text, 17, Theme.TEXT, Theme.SEMI);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(52));
        pad(b, 16, 0, 16, 0);
        b.setTag(r);
        b.setOnClickListener(x -> {
            if (b.isEnabled()) r.run();
        });
        setPrimaryEnabled(b, true);
        return b;
    }

    static void setPrimaryEnabled(TextView b, boolean on) {
        b.setEnabled(on);
        if (on) {
            b.setBackground(grad(Theme.BLUE_HI, Theme.BLUE_LO, 14));
            b.setTextColor(Theme.TEXT);
            b.setForeground(new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), null, round(0xFFFFFFFF, 14)));
            b.setElevation(dp(3));
        } else {
            b.setBackground(round(Theme.SURFACE2, 14, Theme.BORDER, 1));
            b.setTextColor(Theme.FAINT);
            b.setForeground(null);
            b.setElevation(0);
        }
    }

    /** Botón con borde (acción secundaria). */
    static TextView secondary(String text, int accent, Runnable r) {
        TextView b = tv(text, 15.5f, accent, Theme.SEMI);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(52));
        pad(b, 14, 0, 14, 0);
        b.setBackground(round(Theme.SURFACE, 14, Theme.alpha(accent, 0.6f), 1.4f));
        press(b, 14, r);
        return b;
    }

    /** Botón con icono y texto (para filas de acciones). */
    static LinearLayout iconTextButton(int icon, String text, int accent, Runnable r) {
        LinearLayout b = h();
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(52));
        pad(b, 14, 0, 14, 0);
        b.setBackground(round(Theme.SURFACE, 14, Theme.alpha(accent, 0.6f), 1.4f));
        b.addView(icon(icon, accent, 20));
        TextView t = tv(text, 15.5f, accent, Theme.SEMI);
        t.setPadding(dp(8), 0, 0, 0);
        b.addView(t);
        press(b, 14, r);
        return b;
    }

    // ------------------------------------------------------------------ pestañas segmentadas y chips

    /** Barra «General | Rondas | Subastadas» con la opción activa en azul. */
    static LinearLayout segmented(String[] labels, int sel, IntConsumer onSel) {
        LinearLayout bar = h();
        bar.setBackground(round(Theme.SURFACE, 12, Theme.BORDER, 1));
        pad(bar, 3, 3, 3, 3);
        TextView[] items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = one(labels[i], 14, Theme.TEXT2, Theme.MED);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(38));
            items[i] = t;
            press(t, 10, () -> {
                for (int k = 0; k < items.length; k++) styleSeg(items[k], k == idx);
                onSel.accept(idx);
            });
            styleSeg(t, i == sel);
            bar.addView(t, lp(0, WC, 1));
        }
        return bar;
    }

    private static void styleSeg(TextView t, boolean on) {
        t.setBackground(on ? grad(Theme.BLUE_HI, Theme.BLUE_LO, 10) : null);
        t.setTextColor(on ? Theme.TEXT : Theme.TEXT2);
        t.setTypeface(on ? Theme.SEMI : Theme.MED);
    }

    static TextView chip(String text, boolean on, Runnable r) {
        TextView t = one(text, 13.5f, on ? Theme.TEXT : Theme.TEXT2, on ? Theme.SEMI : Theme.MED);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(36));
        pad(t, 13, 0, 13, 0);
        t.setBackground(on ? grad(Theme.BLUE_HI, Theme.BLUE_LO, 10) : round(Theme.SURFACE, 10, Theme.BORDER2, 1));
        press(t, 10, r);
        return t;
    }

    /** Etiqueta pequeña de estado («En curso», «Subastada»…). */
    static TextView tag(String text, int bg, int fg) {
        TextView t = one(text, 11.5f, fg, Theme.SEMI);
        pad(t, 8, 3, 8, 3);
        t.setBackground(round(bg, 7));
        return t;
    }

    // ------------------------------------------------------------------ tarjetas y filas

    static LinearLayout card() {
        LinearLayout c = v();
        c.setBackground(round(Theme.SURFACE, 14, Theme.BORDER, 1));
        pad(c, 14, 12, 14, 12);
        return c;
    }

    static LinearLayout.LayoutParams cardLp() {
        return lpm(MW, WC, 0, 0, 0, 10);
    }

    /** Fila de lista: elemento inicial, título, subtítulo y flecha. */
    static LinearLayout listRow(View lead, String title, String sub, Runnable click) {
        LinearLayout row = h();
        row.setBackground(round(Theme.SURFACE, 14, Theme.BORDER, 1));
        pad(row, 14, 10, 10, 10);
        row.setMinimumHeight(dp(62));
        if (lead != null) {
            row.addView(lead);
            row.addView(hspace(14));
        }
        LinearLayout col = v();
        col.addView(one(title, 16, Theme.TEXT, Theme.SEMI));
        if (sub != null) {
            TextView s = one(sub, 13, Theme.MUTED, Theme.REG);
            s.setPadding(0, dp(4), 0, 0);
            col.addView(s);
        }
        row.addView(col, lp(0, WC, 1));
        if (click != null) {
            row.addView(icon(Ico.CHEVRON, Theme.TEXT2, 20));
            press(row, 14, click);
        }
        row.setLayoutParams(lpm(MW, WC, 0, 0, 0, 8));
        return row;
    }

    static View dot(int color, float sizeDp) {
        Views.Dot d = new Views.Dot(a, color);
        d.setLayoutParams(lp(dp(sizeDp), dp(sizeDp)));
        return d;
    }

    /** Recuadro de estadística: icono, valor grande y etiqueta. */
    static LinearLayout tile(int icon, int iconColor, boolean rich, String value, String label) {
        LinearLayout t = v();
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(round(Theme.SURFACE2, 12, Theme.BORDER, 1));
        pad(t, 4, 10, 4, 10);
        if (icon != 0) {
            Ico ic = new Ico(icon, iconColor);
            ic.rich = rich;
            t.addView(icon(ic, 24));
            t.addView(space(5));
        }
        TextView v = one(value, 20, Theme.TEXT, Theme.BOLD);
        v.setGravity(Gravity.CENTER);
        t.addView(v, lp(MW, WC));
        TextView l = tv(label, 11, Theme.MUTED, Theme.REG);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(4), 0, 0);
        l.setMaxLines(2);
        t.addView(l, lp(MW, WC));
        return t;
    }

    static LinearLayout tileRow(View... tiles) {
        LinearLayout r = h();
        r.setGravity(Gravity.TOP);
        for (int i = 0; i < tiles.length; i++) {
            LinearLayout.LayoutParams p = lp(0, MW, 1);
            if (i > 0) p.leftMargin = dp(8);
            r.addView(tiles[i], p);
        }
        return r;
    }

    /** Fila clave/valor (configuración de reglas). */
    static LinearLayout kv(String k, String v) {
        LinearLayout r = h();
        pad(r, 0, 9, 0, 9);
        TextView kt = tv(k, 14, Theme.TEXT2, Theme.REG);
        r.addView(kt, lp(0, WC, 1));
        TextView vt = tv(v, 14, Theme.TEXT, Theme.SEMI);
        vt.setGravity(Gravity.END);
        vt.setPadding(dp(10), 0, 0, 0);
        r.addView(vt, lp(WC, WC));
        return r;
    }

    static EditText input(String hint, String value) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.FAINT);
        e.setTypeface(Theme.MED);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setBackground(round(Theme.SURFACE2, 12, Theme.BORDER2, 1));
        pad(e, 14, 12, 14, 12);
        return e;
    }

    /** Selector numérico − valor + para ajustes. */
    static LinearLayout stepper(int value, int min, int max, String suffix, IntConsumer onChange) {
        LinearLayout r = h();
        final int[] val = {value};
        TextView t = one("", 16, Theme.TEXT, Theme.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(54));
        Runnable show = () -> t.setText(val[0] + (suffix == null ? "" : suffix));
        show.run();
        FrameLayout minus = smallSquare(Ico.MINUS, () -> {
            if (val[0] > min) { val[0]--; show.run(); onChange.accept(val[0]); }
        });
        FrameLayout plus = smallSquare(Ico.PLUS, () -> {
            if (val[0] < max) { val[0]++; show.run(); onChange.accept(val[0]); }
        });
        r.addView(minus);
        r.addView(t);
        r.addView(plus);
        return r;
    }

    static FrameLayout smallSquare(int icon, Runnable r) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(round(Theme.SURFACE3, 9, Theme.BORDER2, 1));
        f.addView(icon(icon, Theme.TEXT, 18), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        f.setLayoutParams(lp(dp(36), dp(36)));
        press(f, 9, r);
        return f;
    }

    /** Fila de ajuste: título, explicación y control a la derecha. */
    static LinearLayout settingRow(String title, String sub, View control) {
        LinearLayout r = h();
        pad(r, 0, 10, 0, 10);
        LinearLayout col = v();
        col.addView(tv(title, 15, Theme.TEXT, Theme.MED));
        if (sub != null) {
            TextView s = tv(sub, 12.5f, Theme.MUTED, Theme.REG);
            s.setPadding(0, dp(3), dp(8), 0);
            col.addView(s);
        }
        r.addView(col, lp(0, WC, 1));
        if (control != null) r.addView(control);
        return r;
    }

    static Views.Toggle toggle(boolean on, Views.Toggle.Listener l) {
        Views.Toggle t = new Views.Toggle(a);
        t.on = on;
        t.listener = l;
        return t;
    }
}
