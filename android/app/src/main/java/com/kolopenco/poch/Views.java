package com.kolopenco.poch;

import static com.kolopenco.poch.Theme.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Vistas dibujadas a mano (Canvas) para las piezas clave del mockup. */
final class Views {
    private Views() {}

    static final String MINUS = "−";

    static String signed(int v) {
        if (v > 0) return "+" + v;
        if (v < 0) return MINUS + (-v);
        return "±0";
    }

    static String num(int v) {
        return v < 0 ? MINUS + (-v) : String.valueOf(v);
    }

    static Paint text(Typeface tf, float sizeDp, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(dp(sizeDp));
        p.setColor(color);
        return p;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    static Paint stroke(int color, float wDp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(wDp));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    static void icon(Canvas c, int type, int color, boolean rich, float cx, float cy, float size) {
        Ico ic = new Ico(type, color);
        ic.rich = rich;
        ic.setBounds(Math.round(cx - size / 2), Math.round(cy - size / 2),
                Math.round(cx + size / 2), Math.round(cy + size / 2));
        ic.draw(c);
    }

    /** Recorta el texto con «…» para que quepa en el ancho dado. */
    static String fit(Paint p, String s, float max) {
        if (p.measureText(s) <= max) return s;
        String e = "…";
        int n = s.length();
        while (n > 1 && p.measureText(s.substring(0, n) + e) > max) n--;
        return s.substring(0, n) + e;
    }

    // ===================================================================== tarjeta de jugador

    /** Tarjeta de un jugador en el marcador (nombre, A/F, total, variación, subasta y botones). */
    static final class PlayerCard extends View {
        interface Listener {
            /** which: 0 = valor sencillo o pedidas, 1 = hechas. */
            void step(int which, int delta);
            void clear(int which);
            void valueTap(int which);
            void auction();
        }

        String name = "";
        int color = 0xFF1E9E54;
        int score;
        Integer delta;
        int hits, misses;
        boolean dealer;
        boolean full;
        boolean auction, won;
        Integer v, bid, made;
        boolean editable = true;
        /** Texto del modo lectura («Acierto: 3», «Pedidas: 3 · Hechas: 1»…). */
        String readText;
        /** Puntos de esta ronda corregidos: se pintan en amarillo. */
        boolean yellow;
        /** No juega esta ronda (desempate). */
        boolean out;
        Listener listener;

        private final RectF rect = new RectF(), tmp = new RectF();
        private final RectF[] minus = {new RectF(), new RectF()};
        private final RectF[] plus = {new RectF(), new RectF()};
        private final RectF[] val = {new RectF(), new RectF()};
        private final RectF check = new RectF();
        private int pressed = -1; // 0..5 botones, 6 subasta
        private boolean longFired;
        private final Runnable longPress = new Runnable() {
            @Override public void run() {
                if (pressed == 2 || pressed == 5) {
                    longFired = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (listener != null) listener.clear(pressed == 2 ? 0 : 1);
                    pressed = -1;
                    invalidate();
                }
            }
        };

        PlayerCard(Context c) { super(c); }

        /** Escala de la tarjeta: 1 con la altura de diseño; crece o encoge con el sitio disponible. */
        private float k = 1f;

        /** Altura de diseño (dp) a la que k = 1. */
        static float baseHeight(boolean full) { return full ? 92 : 84; }

        private float s(float v) { return dp(v) * k; }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            k = Math.max(0.7f, Math.min(1.6f, h / dp(baseHeight(full))));
            float kp = Math.min(k, 1.25f);
            float kb = Math.min(k, 1.3f);              // los botones crecen menos que el resto
            float bh = dp(26) * kb;
            float my = h - bh / 2 - dp(4) * k;         // centro de la fila de botones
            float pad = dp(14) * kp;
            rect.set(0, 0, w, h);
            float rad = dp(14) * Math.min(k, 1.2f);

            // Fondo: degradado del color del jugador, brillo arriba y borde fino
            Paint bg = fill(color);
            bg.setShader(new LinearGradient(0, 0, 0, h, Theme.lighten(color, 0.10f),
                    Theme.darken(color, 0.20f), Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, bg);
            Paint sheen = fill(0);
            sheen.setShader(new LinearGradient(0, 0, 0, h * 0.55f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, sheen);
            tmp.set(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f));
            c.drawRoundRect(tmp, rad, rad, stroke(0x2EFFFFFF, 1));

            int shadow = 0x59000000;

            // Nombre y marca del repartidor
            Paint np = text(Theme.SEMI, 17 * k, 0xFFFFFFFF);
            np.setShadowLayer(dp(2), 0, dp(1), shadow);
            String nm = fit(np, name, w * 0.34f);
            c.drawText(nm, pad, s(25), np);
            if (dealer) icon(c, Ico.DECK, 0xFFFFFFFF, false, pad + np.measureText(nm) + s(15), s(19), s(19));

            // A / F (rondas acertadas y falladas hasta esta ronda)
            Paint ap = text(Theme.MED, 12.5f * k, 0xE6FFFFFF);
            ap.setShadowLayer(dp(1.5f), 0, dp(0.5f), shadow);
            c.drawText("A: " + hits + "   F: " + misses, pad, s(45), ap);

            // Total acumulado
            float cx = w * 0.48f;
            Paint sp = text(Theme.BOLD, 33 * k, yellow ? Theme.YELLOW : 0xFFFFFFFF);
            sp.setTextAlign(Paint.Align.CENTER);
            sp.setShadowLayer(dp(3), 0, dp(1), 0x66000000);
            if (yellow) {
                float tw = sp.measureText(num(score));
                tmp.set(cx - tw / 2 - s(8), s(12), cx + tw / 2 + s(8), s(46));
                c.drawRoundRect(tmp, s(9), s(9), fill(0x8C0A1222));
                c.drawRoundRect(tmp, s(9), s(9), stroke(Theme.alpha(Theme.YELLOW, 0.7f), 1.2f));
            }
            c.drawText(num(score), cx, s(40), sp);

            // Variación de la ronda
            float right = w - pad;
            if (delta != null) {
                String d = signed(delta);
                Paint dpnt = text(Theme.BOLD, 19 * k, yellow ? Theme.YELLOW : delta >= 0 ? Theme.GREEN_HI : Theme.RED_HI);
                dpnt.setTextAlign(Paint.Align.RIGHT);
                float tw = dpnt.measureText(d);
                tmp.set(right - tw - s(8), s(9), right + s(6), s(34));
                c.drawRoundRect(tmp, s(8), s(8), fill(yellow ? 0x8C0A1222 : delta >= 0 ? 0x30000000 : 0x4A2A0000));
                c.drawText(d, right - s(1), s(28), dpnt);
            }

            // Check de subasta (solo en rondas subastadas)
            check.setEmpty();
            if (auction && !out) {
                Paint lp = text(Theme.MED, 12.5f * k, 0xFFFFFFFF);
                lp.setShadowLayer(dp(1.5f), 0, dp(0.5f), shadow);
                String lab = "Subasta";
                float lw = lp.measureText(lab);
                float r = s(9.5f);
                float cy = full && editable ? s(47) : s(52);
                if (editable) cy = Math.max(s(36) + r, Math.min(cy, my - bh / 2 - r - dp(4)));
                float ccx = right - lw - s(6) - r;
                boolean faint = !editable && !won;
                if (won) {
                    c.drawCircle(ccx, cy, r + s(1), fill(0xFFFFFFFF));
                    c.drawCircle(ccx, cy, r - s(0.5f), fill(Theme.GREEN));
                    Path ck = new Path();
                    ck.moveTo(ccx - r * 0.45f, cy + r * 0.02f);
                    ck.lineTo(ccx - r * 0.1f, cy + r * 0.38f);
                    ck.lineTo(ccx + r * 0.5f, cy - r * 0.32f);
                    c.drawPath(ck, stroke(0xFFFFFFFF, 2.2f * k));
                } else {
                    c.drawCircle(ccx, cy, r, fill(0x26000000));
                    c.drawCircle(ccx, cy, r, stroke(faint ? 0x80FFFFFF : 0xFFFFFFFF, 1.8f * k));
                }
                if (faint) lp.setColor(0x99FFFFFF);
                c.drawText(lab, right - lw, cy + s(4.5f), lp);
                if (editable) check.set(ccx - r - s(10), cy - s(17), w, cy + s(15));
            }

            // Controles o texto de lectura
            for (RectF rf : minus) rf.setEmpty();
            for (RectF rf : plus) rf.setEmpty();
            for (RectF rf : val) rf.setEmpty();
            if (out) {
                drawPill(c, "No juega el desempate", cx, my);
            } else if (!editable) {
                if (readText != null) drawPill(c, readText, cx, my);
            } else if (!full) {
                layoutStepper(0, cx, my, dp(32) * kb, dp(46) * kb, bh);
                drawStepper(c, 0, v == null ? "\u2014" : num(v), v == null);
            } else {
                float lw = dp(30) * kb, bw = dp(28) * kb, vw = dp(36) * kb, gap = dp(3) * kb, mid = dp(16) * kb;
                float group = lw + dp(4) * kb + bw * 2 + vw + gap * 2;
                float total = group * 2 + mid;
                float f = Math.min(1f, (w - 2 * dp(10)) / total);
                float x0 = w / 2 - total * f / 2;
                Paint lp = text(Theme.MED, 11.5f * kb * Math.max(0.85f, f), 0xE6FFFFFF);
                lp.setShadowLayer(dp(1.5f), 0, dp(0.5f), shadow);
                String[] labs = {"Pide", "Hace"};
                Integer[] vals = {bid, made};
                for (int i = 0; i < 2; i++) {
                    float gx = x0 + i * (group + mid) * f;
                    c.drawText(labs[i], gx, my + dp(4) * kb, lp);
                    float scx = gx + (lw + dp(4) * kb) * f + (bw * 2 + vw + gap * 2) * f / 2;
                    layoutStepper(i, scx, my, bw * f, vw * f, bh);
                    drawStepper(c, i, vals[i] == null ? "\u2014" : String.valueOf(vals[i]), vals[i] == null);
                }
            }

            if (out) c.drawRoundRect(rect, rad, rad, fill(0x7A080F1D));
        }

        private void drawPill(Canvas c, String str, float cx, float cy) {
            Paint tp = text(Theme.SEMI, 13.5f * k, 0xFFFFFFFF);
            tp.setTextAlign(Paint.Align.CENTER);
            float tw = tp.measureText(str);
            tmp.set(cx - tw / 2 - s(12), cy - s(12.5f), cx + tw / 2 + s(12), cy + s(12.5f));
            c.drawRoundRect(tmp, s(7), s(7), fill(0x3D000000));
            c.drawText(str, cx, cy + s(4.8f), tp);
        }

        private void layoutStepper(int i, float cx, float cy, float bw, float vw, float bh) {
            float gap = s(3);
            val[i].set(cx - vw / 2, cy - bh / 2, cx + vw / 2, cy + bh / 2);
            minus[i].set(val[i].left - gap - bw, cy - bh / 2, val[i].left - gap, cy + bh / 2);
            plus[i].set(val[i].right + gap, cy - bh / 2, val[i].right + gap + bw, cy + bh / 2);
        }

        private void drawStepper(Canvas c, int i, String value, boolean empty) {
            float r = s(6);
            int base = i * 3;
            c.drawRoundRect(minus[i], r, r, fill(pressed == base ? 0x55FFFFFF : 0x30000000));
            c.drawRoundRect(val[i], r, r, fill(pressed == base + 2 ? 0x40FFFFFF : 0x4D000000));
            c.drawRoundRect(plus[i], r, r, fill(pressed == base + 1 ? 0x55FFFFFF : 0x30000000));
            Paint ln = stroke(0xFFFFFFFF, 2 * k);
            float sz = Math.min(s(5.5f), minus[i].width() * 0.3f);
            c.drawLine(minus[i].centerX() - sz, minus[i].centerY(), minus[i].centerX() + sz, minus[i].centerY(), ln);
            c.drawLine(plus[i].centerX() - sz, plus[i].centerY(), plus[i].centerX() + sz, plus[i].centerY(), ln);
            c.drawLine(plus[i].centerX(), plus[i].centerY() - sz, plus[i].centerX(), plus[i].centerY() + sz, ln);
            float fs = Math.min(17.5f * k, val[i].height() / dp(1) * 0.68f);
            Paint vp = text(Theme.BOLD, fs, empty ? 0xB3FFFFFF : 0xFFFFFFFF);
            vp.setTextAlign(Paint.Align.CENTER);
            c.drawText(value, val[i].centerX(), val[i].centerY() + dp(fs) * 0.355f, vp);
        }

        private int hitTest(float x, float y) {
            float slop = dp(6);
            for (int i = 0; i < 2; i++) {
                if (grow(minus[i], slop).contains(x, y)) return i * 3;
                if (grow(plus[i], slop).contains(x, y)) return i * 3 + 1;
                if (grow(val[i], 0).contains(x, y)) return i * 3 + 2;
            }
            if (!check.isEmpty() && check.contains(x, y)) return 6;
            return -1;
        }

        private RectF grow(RectF r, float s) {
            if (r.isEmpty()) { tmp.setEmpty(); return tmp; }
            tmp.set(r.left - s, r.top - s, r.right + s, r.bottom + s);
            return tmp;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!editable || out) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = hitTest(e.getX(), e.getY());
                    longFired = false;
                    if (pressed < 0) return false;
                    if (pressed == 2 || pressed == 5) postDelayed(longPress, 450);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (pressed >= 0 && hitTest(e.getX(), e.getY()) != pressed) {
                        removeCallbacks(longPress);
                        pressed = -1;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    removeCallbacks(longPress);
                    int p = pressed;
                    pressed = -1;
                    invalidate();
                    if (p < 0 || longFired || listener == null) return true;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (p == 6) listener.auction();
                    else if (p % 3 == 2) listener.valueTap(p / 3);
                    else listener.step(p / 3, p % 3 == 0 ? -1 : +1);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPress);
                    pressed = -1;
                    invalidate();
                    return true;
            }
            return true;
        }
    }

    // ===================================================================== carrusel de rondas

    /** Selector de rondas: círculo blanco con las cartas de la ronda vista y vecinas en gris. */
    static final class RoundStrip extends View {
        interface Listener { void select(int idx); }

        List<Integer> cards = new ArrayList<>();
        List<Boolean> edited = new ArrayList<>();
        /** Última ronda a la que se puede ir (la que se está jugando). */
        int maxSel;
        int sel;
        Listener listener;

        private float pos;
        private float animFrom, animTo;
        private long animStart;
        private boolean animating;
        private float downX, downPos;
        private boolean dragging;

        RoundStrip(Context c) { super(c); }

        void set(List<Integer> cards, List<Boolean> edited, int maxSel, int sel, boolean animate) {
            this.cards = cards;
            this.edited = edited;
            this.maxSel = maxSel;
            if (animate && sel != this.sel) {
                animFrom = pos;
                animTo = sel;
                animStart = System.currentTimeMillis();
                animating = true;
            } else {
                pos = sel;
                animating = false;
            }
            this.sel = sel;
            invalidate();
        }

        private float bigGap() { return dp(48); }
        private float smallGap() { return dp(31); }

        private float offset(float d) {
            float a = Math.abs(d);
            float o = a <= 1 ? a * bigGap() : bigGap() + (a - 1) * smallGap();
            return d < 0 ? -o : o;
        }

        @Override protected void onDraw(Canvas c) {
            if (animating) {
                float t = Math.min(1f, (System.currentTimeMillis() - animStart) / 220f);
                float e = 1 - (1 - t) * (1 - t) * (1 - t);
                pos = animFrom + (animTo - animFrom) * e;
                if (t >= 1) animating = false; else postInvalidateOnAnimation();
            }
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h / 2;
            float edgeL = dp(34), edgeR = w - dp(34);

            // Flechas
            icon(c, Ico.CHEVRON_LEFT, sel > 0 ? 0xFFE6ECF5 : 0x40E6ECF5, false, dp(16), cy, dp(24));
            icon(c, Ico.CHEVRON, sel < maxSel ? 0xFFE6ECF5 : 0x40E6ECF5, false, w - dp(16), cy, dp(24));

            c.save();
            c.clipRect(edgeL, 0, edgeR, h);
            Paint tp = text(Theme.SEMI, 18, Theme.TEXT2);
            tp.setTextAlign(Paint.Align.CENTER);
            int first = Math.max(0, (int) Math.floor(pos) - 6), last = Math.min(cards.size() - 1, (int) Math.ceil(pos) + 6);
            for (int i = first; i <= last; i++) {
                float d = i - pos;
                float x = cx + offset(d);
                if (x < edgeL - dp(20) || x > edgeR + dp(20)) continue;
                float near = Math.max(0, 1 - Math.abs(d)); // 1 en el centro
                float edgeFade = Math.min(1f, Math.min(x - edgeL, edgeR - x) / dp(22));
                edgeFade = Math.max(0, edgeFade);
                boolean ed = i < edited.size() && edited.get(i);
                boolean future = i > maxSel;
                float rBig = dp(24), rSmall = dp(14);
                float r = rSmall + (rBig - rSmall) * near;
                if (near > 0.01f) {
                    Paint cp = fill(ed ? Theme.YELLOW : 0xFFFFFFFF);
                    cp.setAlpha(Math.round(255 * near));
                    cp.setShadowLayer(dp(6), 0, dp(2), Theme.alpha(0xFF000000, 0.35f * near));
                    c.drawCircle(x, cy, r, cp);
                }
                if (ed && near < 0.99f) {
                    Paint yp = fill(Theme.YELLOW);
                    yp.setAlpha(Math.round(255 * (1 - near) * edgeFade));
                    c.drawCircle(x, cy, rSmall, yp);
                }
                float ts = dp(18) + (dp(26) - dp(18)) * near;
                tp.setTextSize(ts);
                tp.setTypeface(near > 0.5f ? Theme.BOLD : Theme.SEMI);
                int col;
                if (near > 0.5f || ed) col = 0xFF0A1222;
                else col = future ? Theme.FAINT : Theme.TEXT2;
                tp.setColor(col);
                tp.setAlpha(Math.round(255 * Math.max(near, edgeFade)));
                c.drawText(String.valueOf(cards.get(i)), x, cy + ts * 0.36f, tp);
            }
            c.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downPos = pos;
                    dragging = false;
                    animating = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - downX;
                    if (!dragging && Math.abs(dx) > dp(8)) dragging = true;
                    if (dragging) {
                        pos = Math.max(-0.3f, Math.min(maxSel + 0.3f, downPos - dx / smallGap()));
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    int target;
                    if (dragging) {
                        target = Math.round(pos);
                    } else {
                        float x = e.getX(), w = getWidth();
                        if (x < dp(34)) target = sel - 1;
                        else if (x > w - dp(34)) target = sel + 1;
                        else {
                            target = sel;
                            float best = Float.MAX_VALUE;
                            for (int i = 0; i < cards.size(); i++) {
                                float d = Math.abs(w / 2 + offset(i - pos) - x);
                                if (d < best) { best = d; target = i; }
                            }
                        }
                    }
                    target = Math.max(0, Math.min(maxSel, Math.min(cards.size() - 1, target)));
                    animFrom = pos;
                    animTo = target;
                    animStart = System.currentTimeMillis();
                    animating = true;
                    boolean changed = target != sel;
                    sel = target;
                    invalidate();
                    if (changed) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        if (listener != null) listener.select(target);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    pos = sel;
                    invalidate();
                    return true;
            }
            return true;
        }
    }

    // ===================================================================== chip de multiplicador

    /** «Oros ×2» con moneda e interruptor. */
    static final class MultChip extends View {
        String suit = "oros";
        int factor = 2;
        boolean on;

        MultChip(Context c) { super(c); }

        String label() { return Model.suitName(suit) + " ×" + factor; }

        static int suitIcon(String s) {
            switch (s) {
                case "copas": return Ico.CUP;
                case "espadas": return Ico.SWORD;
                case "bastos": return Ico.CLUB;
                default: return Ico.COIN;
            }
        }

        static int suitColor(String s) {
            switch (s) {
                case "copas": return 0xFFE2453A;
                case "espadas": return 0xFF5AA8F0;
                case "bastos": return 0xFF4FB548;
                default: return Theme.GOLD;
            }
        }

        @Override protected void onMeasure(int ws, int hs) {
            Paint tp = text(Theme.SEMI, 14, 0xFFFFFFFF);
            float w = dp(10) + dp(22) + dp(8) + tp.measureText(label()) + dp(12) + dp(36) + dp(10);
            setMeasuredDimension(Math.round(w), Math.round(dp(38)));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            int accent = suitColor(suit);
            RectF r = new RectF(dp(1), dp(1), w - dp(1), h - dp(1));
            float rad = dp(10);
            Paint bg = fill(on ? Theme.alpha(Theme.darken(accent, 0.6f), 0.55f) : Theme.SURFACE2);
            c.drawRoundRect(r, rad, rad, bg);
            c.drawRoundRect(r, rad, rad, stroke(on ? accent : Theme.BORDER2, on ? 1.6f : 1.2f));
            float x = dp(10);
            icon(c, suitIcon(suit), accent, true, x + dp(11), h / 2, dp(22));
            x += dp(22) + dp(8);
            Paint tp = text(Theme.SEMI, 14, on ? 0xFFFFFFFF : Theme.TEXT2);
            c.drawText(label(), x, h / 2 + dp(5), tp);
            // Interruptor
            float sw = dp(36), sh = dp(20);
            float sx = w - dp(10) - sw, sy = h / 2 - sh / 2;
            RectF tr = new RectF(sx, sy, sx + sw, sy + sh);
            c.drawRoundRect(tr, sh / 2, sh / 2, fill(on ? Theme.BLUE : 0xFF3A4762));
            float kx = on ? sx + sw - sh / 2 : sx + sh / 2;
            Paint kp = fill(0xFFFFFFFF);
            kp.setShadowLayer(dp(2), 0, dp(1), 0x55000000);
            c.drawCircle(kx, h / 2, sh / 2 - dp(2.5f), kp);
        }
    }

    // ===================================================================== pestañas

    /** Botón de la barra inferior: icono (en pastilla azul si está activo) y etiqueta. */
    static final class NavItem extends View {
        int icon;
        String label = "";
        boolean selected;
        /** Partida en curso: punto azul vivo cuando no es la pestaña activa. */
        boolean live;
        private final long born = System.currentTimeMillis();

        NavItem(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth();
            float cx = w / 2, iy = dp(20);
            int muted = 0xFF8996AD;
            if (selected) {
                RectF pill = new RectF(cx - dp(27), iy - dp(15), cx + dp(27), iy + dp(15));
                Paint pp = fill(Theme.BLUE);
                pp.setShader(new LinearGradient(0, pill.top, 0, pill.bottom, Theme.BLUE_HI, Theme.BLUE_LO, Shader.TileMode.CLAMP));
                c.drawRoundRect(pill, dp(15), dp(15), pp);
            }
            int ic = selected ? 0xFFFFFFFF : live ? Theme.BLUE_TEXT : muted;
            icon(c, icon, ic, false, cx, iy, dp(23));
            if (live && !selected) {
                float t = ((System.currentTimeMillis() - born) % 1400) / 1400f;
                float dx = cx + dp(13), dy = iy - dp(10);
                Paint halo = fill(Theme.BLUE_HI);
                halo.setAlpha(Math.round(150 * (1 - t)));
                c.drawCircle(dx, dy, dp(4) + dp(6) * t, halo);
                c.drawCircle(dx, dy, dp(4.2f), fill(Theme.BG));
                c.drawCircle(dx, dy, dp(3.2f), fill(Theme.BLUE_HI));
                postInvalidateOnAnimation();
            }
            Paint tp = text(selected ? Theme.SEMI : Theme.MED, 11.5f, selected ? Theme.BLUE_TEXT : live ? Theme.BLUE_TEXT : muted);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(label, cx, dp(52), tp);
        }
    }

    // ===================================================================== gráficos y adornos

    /** Círculo de color de un jugador. */
    static final class Dot extends View {
        int color;
        String ring; // no usado

        Dot(Context c, int color) { super(c); this.color = color; }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float r = s / 2;
            Paint p = fill(color);
            p.setShader(new RadialGradient(s * 0.36f, s * 0.3f, s * 0.85f,
                    Theme.lighten(color, 0.28f), Theme.darken(color, 0.12f), Shader.TileMode.CLAMP));
            c.drawCircle(getWidth() / 2f, getHeight() / 2f, r, p);
        }
    }

    /** Anillo de porcentaje con el valor en el centro. */
    static final class Ring extends View {
        float pct; // 0..100, NaN = sin datos
        int color = Theme.GREEN;

        Ring(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float sw = s * 0.12f;
            RectF r = new RectF(sw / 2 + dp(1), sw / 2 + dp(1), s - sw / 2 - dp(1), s - sw / 2 - dp(1));
            Paint tr = stroke(Theme.SURFACE3, 1);
            tr.setStrokeWidth(sw);
            c.drawArc(r, 0, 360, false, tr);
            if (!Float.isNaN(pct) && pct > 0) {
                Paint ap = stroke(color, 1);
                ap.setStrokeWidth(sw);
                ap.setShader(new LinearGradient(0, 0, s, s, Theme.lighten(color, 0.25f), color, Shader.TileMode.CLAMP));
                c.drawArc(r, -90, 360 * Math.min(100, pct) / 100f, false, ap);
            }
            Paint tp = text(Theme.BOLD, 0, 0xFFFFFFFF);
            tp.setTextSize(s * 0.27f);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(Float.isNaN(pct) ? "—" : Math.round(pct) + "%", s / 2, s / 2 + s * 0.095f, tp);
        }
    }

    /** Fila de barra horizontal: color, nombre, barra proporcional y valor. */
    static final class BarRow extends View {
        int color;
        String name = "", value = "";
        float frac;

        BarRow(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cy = h / 2;
            c.drawCircle(dp(7), cy, dp(6), fill(color));
            Paint np = text(Theme.MED, 13.5f, Theme.TEXT2);
            float nameW = dp(70);
            c.drawText(fit(np, name, nameW), dp(20), cy + dp(4.8f), np);
            float x0 = dp(20) + nameW + dp(4), x1 = w - dp(46);
            float bh = dp(13);
            RectF tr = new RectF(x0, cy - bh / 2, x1, cy + bh / 2);
            c.drawRoundRect(tr, bh / 2, bh / 2, fill(0x22FFFFFF));
            if (frac > 0) {
                RectF fr = new RectF(x0, cy - bh / 2, x0 + Math.max(bh, (x1 - x0) * Math.min(1, frac)), cy + bh / 2);
                Paint fp = fill(color);
                fp.setShader(new LinearGradient(0, fr.top, 0, fr.bottom, Theme.lighten(color, 0.18f), Theme.darken(color, 0.12f), Shader.TileMode.CLAMP));
                c.drawRoundRect(fr, bh / 2, bh / 2, fp);
            }
            Paint vp = text(Theme.SEMI, 14, 0xFFFFFFFF);
            vp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(value, w - dp(2), cy + dp(5), vp);
        }
    }

    /** Barra partida en verde (aciertos) y rojo (fallos) con los porcentajes dentro. */
    static final class SplitBar extends View {
        int color;
        String name = "";
        float hitPct = Float.NaN;
        String leftLabel, rightLabel;

        SplitBar(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cy = h / 2;
            c.drawCircle(dp(7), cy, dp(6), fill(color));
            Paint np = text(Theme.MED, 13.5f, Theme.TEXT2);
            float nameW = dp(70);
            c.drawText(fit(np, name, nameW), dp(20), cy + dp(4.8f), np);
            float x0 = dp(20) + nameW + dp(4), x1 = w - dp(2);
            float bh = dp(17);
            RectF all = new RectF(x0, cy - bh / 2, x1, cy + bh / 2);
            if (Float.isNaN(hitPct)) {
                c.drawRoundRect(all, dp(4), dp(4), fill(0x22FFFFFF));
                Paint tp = text(Theme.MED, 11.5f, Theme.MUTED);
                tp.setTextAlign(Paint.Align.CENTER);
                c.drawText("Sin datos", all.centerX(), cy + dp(4), tp);
                return;
            }
            float split = x0 + (x1 - x0) * Math.max(0, Math.min(100, hitPct)) / 100f;
            c.save();
            c.clipRect(x0, all.top, split, all.bottom);
            Paint gp = fill(Theme.GREEN);
            gp.setShader(new LinearGradient(0, all.top, 0, all.bottom, 0xFF3FD87C, 0xFF1EA457, Shader.TileMode.CLAMP));
            c.drawRoundRect(all, dp(4), dp(4), gp);
            c.restore();
            c.save();
            c.clipRect(split, all.top, x1, all.bottom);
            Paint rp = fill(Theme.RED);
            rp.setShader(new LinearGradient(0, all.top, 0, all.bottom, 0xFFFF5A50, 0xFFD3322A, Shader.TileMode.CLAMP));
            c.drawRoundRect(all, dp(4), dp(4), rp);
            c.restore();
            Paint tp = text(Theme.BOLD, 11.5f, 0xFFFFFFFF);
            tp.setTextAlign(Paint.Align.CENTER);
            String l = leftLabel != null ? leftLabel : Math.round(hitPct) + "%";
            String r = rightLabel != null ? rightLabel : Math.round(100 - hitPct) + "%";
            if (split - x0 > tp.measureText(l) + dp(6)) c.drawText(l, (x0 + split) / 2, cy + dp(4), tp);
            if (x1 - split > tp.measureText(r) + dp(6)) c.drawText(r, (split + x1) / 2, cy + dp(4), tp);
        }
    }

    /** Insignia cuadrada o redonda con un número (puestos). */
    static final class Badge extends View {
        String text = "1";
        int color = Theme.SURFACE3;
        int textColor = 0xFFFFFFFF;
        boolean round;
        boolean medal;

        Badge(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(0, 0, w, h);
            Paint p = fill(color);
            if (medal) p.setShader(new LinearGradient(0, 0, 0, h, Theme.lighten(color, 0.3f), Theme.darken(color, 0.15f), Shader.TileMode.CLAMP));
            if (round) c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2, p);
            else c.drawRoundRect(r, dp(5), dp(5), p);
            if (medal) c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - dp(1), stroke(0x66FFFFFF, 1));
            Paint tp = text(Theme.BOLD, 0, textColor);
            tp.setTextSize(h * 0.52f);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(text, w / 2, h / 2 + h * 0.18f, tp);
        }
    }

    /** Interruptor de ajustes. */
    static final class Toggle extends View {
        interface Listener { void changed(boolean on); }

        boolean on;
        Listener listener;

        Toggle(Context c) { super(c); }

        @Override protected void onMeasure(int a, int b) {
            setMeasuredDimension(Math.round(dp(46)), Math.round(dp(28)));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float th = dp(24), tw = dp(42);
            RectF r = new RectF(w / 2 - tw / 2, h / 2 - th / 2, w / 2 + tw / 2, h / 2 + th / 2);
            c.drawRoundRect(r, th / 2, th / 2, fill(on ? Theme.BLUE : 0xFF3A4762));
            Paint kp = fill(0xFFFFFFFF);
            kp.setShadowLayer(dp(2), 0, dp(1), 0x55000000);
            c.drawCircle(on ? r.right - th / 2 : r.left + th / 2, h / 2, th / 2 - dp(3), kp);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                on = !on;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                if (listener != null) listener.changed(on);
            }
            return true;
        }
    }
}
