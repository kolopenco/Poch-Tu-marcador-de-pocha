package com.kolopenco.poch;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Iconos dibujados a mano sobre una rejilla de 24×24. */
final class Ico extends Drawable {
    static final int TROPHY = 1, LIST = 2, PEOPLE = 3, CHART = 4, GEAR = 5, BACK = 6, CHEVRON = 7,
            PLUS = 8, MINUS = 9, SEARCH = 10, EDIT = 11, GAVEL = 12, CROWN = 13, PAUSE = 14, COIN = 15,
            CUP = 16, SWORD = 17, CLUB = 18, DECK = 19, CHECK = 20, CLOSE = 21, DOTS = 22, FILTER = 23,
            GAMEPAD = 24, STAR = 25, TARGET = 26, SHARE = 27, FLAG = 28, PLAY = 29, TRASH = 30, DRAG = 31,
            DOWNLOAD = 32, UPLOAD = 33, SLIDERS = 34, INFO = 35, UNDO = 36, SYNC = 37, CHEVRON_LEFT = 38,
            CARDS = 39, PERSON_PLUS = 40, CHEVRON_DOWN = 41, MEDAL = 42, CHART_LINE = 43, CLOCK = 44,
            BACKUP = 45, ALERT = 46, HANDSHAKE = 47;

    final int type;
    int color;
    /** Relleno con degradado y brillo (trofeos de color, monedas…). */
    boolean rich;
    float stroke = 2f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private int alpha = 255;

    Ico(int type, int color) {
        this.type = type;
        this.color = color;
    }

    Ico rich() { rich = true; return this; }

    @Override public int getIntrinsicWidth() { return Theme.dpi(24); }
    @Override public int getIntrinsicHeight() { return Theme.dpi(24); }
    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        int save = c.save();
        c.translate(b.exactCenterX() - 12 * s, b.exactCenterY() - 12 * s);
        c.scale(s, s);
        paint(c, type, color, rich, stroke, alpha);
        c.restoreToCount(save);
    }

    // ------------------------------------------------------------------ dibujo

    private Paint fill(int col) {
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        p.setAlpha(Math.round(((col >>> 24) / 255f) * alpha));
        return p;
    }

    private Paint line(int col, float w) {
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(col);
        p.setAlpha(Math.round(((col >>> 24) / 255f) * alpha));
        return p;
    }

    /** Relleno con degradado vertical (claro arriba, oscuro abajo). */
    private Paint richFill(int col, float top, float bottom) {
        fill(col);
        p.setShader(new LinearGradient(0, top, 0, bottom,
                Theme.lighten(col, 0.35f), Theme.darken(col, 0.18f), Shader.TileMode.CLAMP));
        return p;
    }

    private void paint(Canvas c, int t, int col, boolean rich, float sw, int a) {
        Path P = path;
        P.reset();
        switch (t) {
            case TROPHY: {
                P.moveTo(6.4f, 3.4f); P.lineTo(17.6f, 3.4f); P.lineTo(17.6f, 8.2f);
                P.cubicTo(17.6f, 11.6f, 15.1f, 14.1f, 12f, 14.1f);
                P.cubicTo(8.9f, 14.1f, 6.4f, 11.6f, 6.4f, 8.2f); P.close();
                c.drawPath(P, rich ? richFill(col, 3, 14) : fill(col));
                Path h = new Path();
                h.moveTo(6.6f, 5.3f); h.lineTo(3.9f, 5.3f); h.cubicTo(3.6f, 8.6f, 5.0f, 10.6f, 7.5f, 10.9f);
                h.moveTo(17.4f, 5.3f); h.lineTo(20.1f, 5.3f); h.cubicTo(20.4f, 8.6f, 19.0f, 10.6f, 16.5f, 10.9f);
                c.drawPath(h, line(rich ? Theme.darken(col, 0.05f) : col, 1.7f));
                Path st = new Path();
                st.addRect(10.9f, 13.6f, 13.1f, 17.4f, Path.Direction.CW);
                r.set(7.2f, 17.2f, 16.8f, 20.6f);
                st.addRoundRect(r, 1.2f, 1.2f, Path.Direction.CW);
                c.drawPath(st, rich ? richFill(col, 13, 21) : fill(col));
                if (rich) {
                    Path hi = new Path();
                    hi.moveTo(8.6f, 5f); hi.lineTo(8.6f, 8.2f); hi.cubicTo(8.6f, 9.6f, 9.2f, 10.8f, 10.2f, 11.6f);
                    c.drawPath(hi, line(Theme.alpha(0xFFFFFFFF, 0.55f), 1.1f));
                }
                break;
            }
            case MEDAL: {
                P.moveTo(7, 2.5f); P.lineTo(10, 2.5f); P.lineTo(13, 9); P.lineTo(10, 10); P.close();
                P.moveTo(17, 2.5f); P.lineTo(14, 2.5f); P.lineTo(11, 9); P.lineTo(14, 10); P.close();
                c.drawPath(P, fill(Theme.darken(col, 0.25f)));
                c.drawCircle(12, 15, 6.2f, rich ? richFill(col, 9, 21) : fill(col));
                c.drawCircle(12, 15, 4.1f, line(Theme.alpha(0xFFFFFFFF, 0.45f), 1.1f));
                break;
            }
            case LIST: {
                for (int i = 0; i < 3; i++) {
                    float y = 6 + i * 6;
                    r.set(3.2f, y - 1.5f, 6.2f, y + 1.5f);
                    c.drawRoundRect(r, 0.7f, 0.7f, fill(col));
                    c.drawLine(9.2f, y, 20.6f, y, line(col, 2.2f));
                }
                break;
            }
            case PEOPLE: {
                c.drawCircle(16.6f, 7.4f, 2.7f, fill(col));
                P.moveTo(14.4f, 12.7f); P.cubicTo(15.1f, 12.35f, 15.8f, 12.2f, 16.6f, 12.2f);
                P.cubicTo(19.4f, 12.2f, 21.2f, 14.4f, 21.2f, 17.4f); P.lineTo(21.2f, 18.2f); P.lineTo(17.3f, 18.2f);
                P.cubicTo(17.2f, 15.9f, 16.2f, 14f, 14.4f, 12.7f); P.close();
                c.drawPath(P, fill(col));
                c.drawCircle(9.4f, 7.8f, 3.4f, fill(col));
                Path b = new Path();
                b.moveTo(2.8f, 19.4f); b.lineTo(2.8f, 18.6f);
                b.cubicTo(2.8f, 15.2f, 5.6f, 13f, 9.4f, 13f);
                b.cubicTo(13.2f, 13f, 16f, 15.2f, 16f, 18.6f); b.lineTo(16f, 19.4f); b.close();
                c.drawPath(b, fill(col));
                break;
            }
            case PERSON_PLUS: {
                c.drawCircle(9.4f, 7.8f, 3.4f, fill(col));
                P.moveTo(2.8f, 19.4f); P.lineTo(2.8f, 18.6f);
                P.cubicTo(2.8f, 15.2f, 5.6f, 13f, 9.4f, 13f);
                P.cubicTo(13.2f, 13f, 16f, 15.2f, 16f, 18.6f); P.lineTo(16f, 19.4f); P.close();
                c.drawPath(P, fill(col));
                c.drawLine(19, 7, 19, 13, line(col, 2.1f));
                c.drawLine(16, 10, 22, 10, line(col, 2.1f));
                break;
            }
            case CHART: {
                r.set(3.6f, 11.5f, 8f, 20.4f); c.drawRoundRect(r, 1f, 1f, fill(col));
                r.set(9.8f, 3.6f, 14.2f, 20.4f); c.drawRoundRect(r, 1f, 1f, fill(col));
                r.set(16f, 8f, 20.4f, 20.4f); c.drawRoundRect(r, 1f, 1f, fill(col));
                break;
            }
            case CHART_LINE: {
                P.moveTo(3.5f, 17); P.lineTo(9, 11); P.lineTo(13, 14.5f); P.lineTo(20.5f, 6.5f);
                c.drawPath(P, line(col, sw));
                break;
            }
            case GEAR: {
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, 12, 12);
                    r.set(10.2f, 1.6f, 13.8f, 6f);
                    c.drawRoundRect(r, 1f, 1f, fill(col));
                    c.restore();
                }
                P.addCircle(12, 12, 7.8f, Path.Direction.CW);
                P.addCircle(12, 12, 3.1f, Path.Direction.CW);
                P.setFillType(Path.FillType.EVEN_ODD);
                c.drawPath(P, fill(col));
                break;
            }
            case BACK: {
                c.drawLine(19.5f, 12, 5, 12, line(col, sw));
                P.moveTo(11, 5.8f); P.lineTo(4.8f, 12); P.lineTo(11, 18.2f);
                c.drawPath(P, line(col, sw));
                break;
            }
            case CHEVRON: {
                P.moveTo(9, 5.5f); P.lineTo(15.5f, 12); P.lineTo(9, 18.5f);
                c.drawPath(P, line(col, sw));
                break;
            }
            case CHEVRON_LEFT: {
                P.moveTo(15, 5.5f); P.lineTo(8.5f, 12); P.lineTo(15, 18.5f);
                c.drawPath(P, line(col, sw));
                break;
            }
            case CHEVRON_DOWN: {
                P.moveTo(5.5f, 9); P.lineTo(12, 15.5f); P.lineTo(18.5f, 9);
                c.drawPath(P, line(col, sw));
                break;
            }
            case PLUS:
                c.drawLine(12, 5, 12, 19, line(col, sw));
                c.drawLine(5, 12, 19, 12, line(col, sw));
                break;
            case MINUS:
                c.drawLine(5, 12, 19, 12, line(col, sw));
                break;
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6.3f, line(col, sw));
                c.drawLine(15.3f, 15.3f, 20.2f, 20.2f, line(col, sw));
                break;
            case EDIT: {
                P.moveTo(14.6f, 4.4f); P.lineTo(19.6f, 9.4f); P.lineTo(9.2f, 19.8f); P.lineTo(4.2f, 19.8f);
                P.lineTo(4.2f, 14.8f); P.close();
                c.drawPath(P, line(col, sw));
                c.drawLine(12.4f, 6.6f, 17.4f, 11.6f, line(col, sw));
                break;
            }
            case GAVEL: {
                c.save();
                c.rotate(-45, 12, 12);
                r.set(5.5f, 3.2f, 15.5f, 8.4f);
                c.drawRoundRect(r, 1.3f, 1.3f, rich ? richFill(col, 3, 9) : fill(col));
                r.set(4.4f, 2.5f, 6.4f, 9.1f);
                c.drawRoundRect(r, 0.8f, 0.8f, fill(Theme.darken(col, 0.15f)));
                r.set(14.6f, 2.5f, 16.6f, 9.1f);
                c.drawRoundRect(r, 0.8f, 0.8f, fill(Theme.darken(col, 0.15f)));
                r.set(9.4f, 8f, 11.6f, 20.5f);
                c.drawRoundRect(r, 1f, 1f, fill(col));
                c.restore();
                r.set(12.5f, 18.6f, 21f, 21.2f);
                c.drawRoundRect(r, 1.1f, 1.1f, fill(Theme.darken(col, 0.1f)));
                break;
            }
            case CROWN: {
                P.moveTo(3.2f, 8.2f); P.lineTo(7.6f, 12.4f); P.lineTo(12, 5.2f); P.lineTo(16.4f, 12.4f);
                P.lineTo(20.8f, 8.2f); P.lineTo(19f, 17.6f); P.lineTo(5f, 17.6f); P.close();
                c.drawPath(P, rich ? richFill(col, 5, 18) : fill(col));
                c.drawCircle(3.2f, 7.6f, 1.5f, fill(col));
                c.drawCircle(12f, 4.4f, 1.5f, fill(col));
                c.drawCircle(20.8f, 7.6f, 1.5f, fill(col));
                r.set(5f, 18.6f, 19f, 21f);
                c.drawRoundRect(r, 0.9f, 0.9f, rich ? richFill(col, 18, 21) : fill(col));
                break;
            }
            case PAUSE:
                r.set(6.6f, 5f, 10.2f, 19f); c.drawRoundRect(r, 1.1f, 1.1f, fill(col));
                r.set(13.8f, 5f, 17.4f, 19f); c.drawRoundRect(r, 1.1f, 1.1f, fill(col));
                break;
            case COIN: {
                c.drawCircle(12, 12, 9.6f, fill(0xFFB9780A));
                Paint g = fill(0xFFF5B623);
                g.setShader(new LinearGradient(0, 3, 0, 21, 0xFFFFE07A, 0xFFE09A12, Shader.TileMode.CLAMP));
                c.drawCircle(12, 12, 8.6f, g);
                c.drawCircle(12, 12, 5.9f, line(0xFFC27E0C, 1.2f));
                c.drawCircle(12, 12, 2.2f, fill(0xFFD48C10));
                c.drawCircle(9.2f, 8.6f, 1.3f, fill(0x99FFFFFF));
                break;
            }
            case CUP: {
                P.moveTo(5.8f, 3.6f); P.lineTo(18.2f, 3.6f); P.cubicTo(18.2f, 8.8f, 15.6f, 11.6f, 12f, 11.6f);
                P.cubicTo(8.4f, 11.6f, 5.8f, 8.8f, 5.8f, 3.6f); P.close();
                c.drawPath(P, rich ? richFill(col, 3, 12) : fill(col));
                r.set(10.9f, 11f, 13.1f, 16.6f); c.drawRect(r, fill(col));
                P.reset();
                P.moveTo(7.4f, 20.4f); P.cubicTo(7.8f, 17.6f, 9.6f, 16.2f, 12f, 16.2f);
                P.cubicTo(14.4f, 16.2f, 16.2f, 17.6f, 16.6f, 20.4f); P.close();
                c.drawPath(P, rich ? richFill(col, 16, 21) : fill(col));
                break;
            }
            case SWORD: {
                P.moveTo(12, 1.8f); P.lineTo(13.5f, 4.6f); P.lineTo(13.4f, 15.2f); P.lineTo(10.6f, 15.2f);
                P.lineTo(10.5f, 4.6f); P.close();
                c.drawPath(P, rich ? richFill(col, 2, 15) : fill(col));
                c.drawLine(6.8f, 15.8f, 17.2f, 15.8f, line(col, 2.2f));
                c.drawLine(12, 16.4f, 12, 20.4f, line(col, 2.4f));
                c.drawCircle(12, 21.4f, 1.3f, fill(col));
                break;
            }
            case CLUB: {
                c.drawLine(8.2f, 20.4f, 15.8f, 4f, line(col, 4.4f));
                c.drawCircle(10.9f, 14.3f, 1.4f, fill(Theme.darken(col, 0.25f)));
                c.drawCircle(13.4f, 9.2f, 1.3f, fill(Theme.darken(col, 0.25f)));
                c.drawLine(12.4f, 11.5f, 16.8f, 10.4f, line(col, 1.6f));
                c.drawLine(10.2f, 16.6f, 6.6f, 15.2f, line(col, 1.6f));
                break;
            }
            case DECK: {
                // Dos naipes superpuestos: marca del repartidor
                c.save();
                c.rotate(-15, 12, 13);
                r.set(4.2f, 4.2f, 13.8f, 19.4f);
                c.drawRoundRect(r, 1.6f, 1.6f, fill(0xFFE8EDF5));
                c.drawRoundRect(r, 1.6f, 1.6f, line(0x66000000, 0.9f));
                c.restore();
                c.save();
                c.rotate(12, 12, 13);
                r.set(10f, 3.6f, 19.6f, 18.8f);
                c.drawRoundRect(r, 1.6f, 1.6f, fill(0xFFFFFFFF));
                c.drawRoundRect(r, 1.6f, 1.6f, line(0x66000000, 0.9f));
                c.drawCircle(14.8f, 11.2f, 2.6f, fill(0xFFE2A21A));
                c.drawCircle(14.8f, 11.2f, 1.5f, line(0xFFB97A08, 0.7f));
                c.restore();
                break;
            }
            case CARDS: {
                for (int i = -1; i <= 1; i++) {
                    c.save();
                    c.rotate(i * 16, 12, 22);
                    r.set(8f, 4f, 16f, 17f);
                    c.drawRoundRect(r, 1.4f, 1.4f, fill(0xFFFFFFFF));
                    c.drawRoundRect(r, 1.4f, 1.4f, line(0x55000000, 0.8f));
                    c.restore();
                }
                c.drawCircle(12, 9.4f, 2.1f, fill(0xFFE2A21A));
                break;
            }
            case CHECK:
                P.moveTo(5, 12.6f); P.lineTo(9.8f, 17.2f); P.lineTo(19.2f, 7.2f);
                c.drawPath(P, line(col, sw));
                break;
            case CLOSE:
                c.drawLine(6.2f, 6.2f, 17.8f, 17.8f, line(col, sw));
                c.drawLine(17.8f, 6.2f, 6.2f, 17.8f, line(col, sw));
                break;
            case DOTS:
                c.drawCircle(12, 5, 2f, fill(col));
                c.drawCircle(12, 12, 2f, fill(col));
                c.drawCircle(12, 19, 2f, fill(col));
                break;
            case FILTER:
                P.moveTo(3.8f, 5f); P.lineTo(20.2f, 5f); P.lineTo(14f, 12.4f); P.lineTo(14f, 18.6f);
                P.lineTo(10f, 20.2f); P.lineTo(10f, 12.4f); P.close();
                c.drawPath(P, line(col, 1.8f));
                break;
            case GAMEPAD: {
                r.set(2.2f, 6.6f, 21.8f, 17.6f);
                P.addRoundRect(r, 5.4f, 5.4f, Path.Direction.CW);
                float cx = 7.6f, cy = 12.1f, a1 = 0.9f, a2 = 3f;
                P.moveTo(cx - a1, cy - a2); P.lineTo(cx + a1, cy - a2); P.lineTo(cx + a1, cy - a1);
                P.lineTo(cx + a2, cy - a1); P.lineTo(cx + a2, cy + a1); P.lineTo(cx + a1, cy + a1);
                P.lineTo(cx + a1, cy + a2); P.lineTo(cx - a1, cy + a2); P.lineTo(cx - a1, cy + a1);
                P.lineTo(cx - a2, cy + a1); P.lineTo(cx - a2, cy - a1); P.lineTo(cx - a1, cy - a1); P.close();
                P.addCircle(15.6f, 10.6f, 1.25f, Path.Direction.CW);
                P.addCircle(17.9f, 13.3f, 1.25f, Path.Direction.CW);
                P.setFillType(Path.FillType.EVEN_ODD);
                c.drawPath(P, fill(col));
                break;
            }
            case STAR: {
                for (int i = 0; i < 10; i++) {
                    double ang = Math.toRadians(-90 + i * 36);
                    float rad = (i % 2 == 0) ? 9.6f : 4.1f;
                    float x = 12 + (float) Math.cos(ang) * rad, y = 12.6f + (float) Math.sin(ang) * rad;
                    if (i == 0) P.moveTo(x, y); else P.lineTo(x, y);
                }
                P.close();
                c.drawPath(P, rich ? richFill(col, 3, 21) : fill(col));
                break;
            }
            case TARGET:
                c.drawCircle(12, 12, 8.6f, line(col, 2f));
                c.drawCircle(12, 12, 4.8f, line(col, 2f));
                c.drawCircle(12, 12, 1.6f, fill(col));
                c.drawLine(12, 12, 19.5f, 4.5f, line(Theme.lighten(col, 0.5f), 1.4f));
                P.moveTo(19.5f, 4.5f); P.lineTo(21.2f, 3.6f); P.lineTo(20.4f, 5.4f);
                c.drawPath(P, line(Theme.lighten(col, 0.5f), 1.4f));
                break;
            case SHARE:
                c.drawLine(17.6f, 5.6f, 6.4f, 12, line(col, 1.8f));
                c.drawLine(6.4f, 12, 17.6f, 18.4f, line(col, 1.8f));
                c.drawCircle(17.6f, 5.4f, 2.7f, fill(col));
                c.drawCircle(6.4f, 12f, 2.7f, fill(col));
                c.drawCircle(17.6f, 18.6f, 2.7f, fill(col));
                break;
            case FLAG:
                c.drawLine(5.6f, 3.4f, 5.6f, 21, line(col, 2f));
                P.moveTo(6.2f, 4f); P.lineTo(18.6f, 4f); P.lineTo(15.4f, 8.3f); P.lineTo(18.6f, 12.6f);
                P.lineTo(6.2f, 12.6f); P.close();
                c.drawPath(P, fill(col));
                break;
            case PLAY:
                P.moveTo(7.2f, 4.4f); P.lineTo(19.2f, 12f); P.lineTo(7.2f, 19.6f); P.close();
                c.drawPath(P, fill(col));
                c.drawPath(P, line(col, 1.6f));
                break;
            case TRASH:
                c.drawLine(4, 6.4f, 20, 6.4f, line(col, 1.9f));
                P.moveTo(9.2f, 6f); P.lineTo(9.2f, 3.8f); P.lineTo(14.8f, 3.8f); P.lineTo(14.8f, 6f);
                c.drawPath(P, line(col, 1.9f));
                P.reset();
                P.moveTo(6.2f, 6.8f); P.lineTo(7.2f, 20.4f); P.lineTo(16.8f, 20.4f); P.lineTo(17.8f, 6.8f);
                c.drawPath(P, line(col, 1.9f));
                c.drawLine(10.3f, 10.2f, 10.3f, 16.8f, line(col, 1.6f));
                c.drawLine(13.7f, 10.2f, 13.7f, 16.8f, line(col, 1.6f));
                break;
            case DRAG:
                for (int i = 0; i < 3; i++) c.drawLine(5, 7.5f + i * 4.5f, 19, 7.5f + i * 4.5f, line(col, 2f));
                break;
            case DOWNLOAD:
                c.drawLine(12, 3.6f, 12, 14.6f, line(col, sw));
                P.moveTo(7.4f, 10.2f); P.lineTo(12, 14.8f); P.lineTo(16.6f, 10.2f);
                c.drawPath(P, line(col, sw));
                P.reset();
                P.moveTo(4.2f, 15.6f); P.lineTo(4.2f, 19.8f); P.lineTo(19.8f, 19.8f); P.lineTo(19.8f, 15.6f);
                c.drawPath(P, line(col, sw));
                break;
            case UPLOAD:
                c.drawLine(12, 15, 12, 4, line(col, sw));
                P.moveTo(7.4f, 8.4f); P.lineTo(12, 3.8f); P.lineTo(16.6f, 8.4f);
                c.drawPath(P, line(col, sw));
                P.reset();
                P.moveTo(4.2f, 15.6f); P.lineTo(4.2f, 19.8f); P.lineTo(19.8f, 19.8f); P.lineTo(19.8f, 15.6f);
                c.drawPath(P, line(col, sw));
                break;
            case SLIDERS:
                c.drawLine(4, 7, 20, 7, line(col, 1.8f));
                c.drawLine(4, 12, 20, 12, line(col, 1.8f));
                c.drawLine(4, 17, 20, 17, line(col, 1.8f));
                c.drawCircle(15, 7, 2.4f, fill(col));
                c.drawCircle(8.5f, 12, 2.4f, fill(col));
                c.drawCircle(13, 17, 2.4f, fill(col));
                break;
            case INFO:
                c.drawCircle(12, 12, 8.8f, line(col, 1.8f));
                c.drawLine(12, 11, 12, 16.6f, line(col, 2.1f));
                c.drawCircle(12, 7.7f, 1.3f, fill(col));
                break;
            case ALERT:
                P.moveTo(12, 3.4f); P.lineTo(21, 19.6f); P.lineTo(3, 19.6f); P.close();
                c.drawPath(P, line(col, 1.8f));
                c.drawLine(12, 9.4f, 12, 14, line(col, 2f));
                c.drawCircle(12, 16.7f, 1.2f, fill(col));
                break;
            case UNDO:
                P.moveTo(8.4f, 4.8f); P.lineTo(4.2f, 9f); P.lineTo(8.4f, 13.2f);
                c.drawPath(P, line(col, sw));
                P.reset();
                P.moveTo(4.6f, 9f); P.lineTo(14.2f, 9f); P.cubicTo(17.6f, 9f, 20f, 11.5f, 20f, 14.6f);
                P.cubicTo(20f, 17.7f, 17.6f, 20f, 14.4f, 20f); P.lineTo(9f, 20f);
                c.drawPath(P, line(col, sw));
                break;
            case SYNC: {
                r.set(5f, 5f, 19f, 19f);
                c.drawArc(r, 200, 290, false, line(col, 2.3f));
                double end = Math.toRadians(490);
                float ex = 12 + 7 * (float) Math.cos(end), ey = 12 + 7 * (float) Math.sin(end);
                double dir = end + Math.PI / 2; // sentido de avance (horario)
                float dx = (float) Math.cos(dir), dy = (float) Math.sin(dir);
                float tx = ex + dx * 1.6f, ty = ey + dy * 1.6f;
                P.moveTo(tx - dx * 4 - dy * 3.4f, ty - dy * 4 + dx * 3.4f);
                P.lineTo(tx, ty);
                P.lineTo(tx - dx * 4 + dy * 3.4f, ty - dy * 4 - dx * 3.4f);
                c.drawPath(P, line(col, 2.3f));
                break;
            }
            case CLOCK:
                c.drawCircle(12, 12, 8.6f, line(col, 1.9f));
                P.moveTo(12, 7.2f); P.lineTo(12, 12.2f); P.lineTo(15.4f, 14.2f);
                c.drawPath(P, line(col, 1.9f));
                break;
            case BACKUP: {
                P.moveTo(7, 18.5f); P.cubicTo(4.2f, 18.5f, 2.6f, 16.6f, 2.6f, 14.4f);
                P.cubicTo(2.6f, 12.1f, 4.4f, 10.4f, 6.6f, 10.4f);
                P.cubicTo(7.3f, 7.4f, 9.6f, 5.4f, 12.6f, 5.4f);
                P.cubicTo(16.2f, 5.4f, 18.6f, 8f, 18.8f, 11f);
                P.cubicTo(20.4f, 11.2f, 21.6f, 12.7f, 21.6f, 14.7f);
                P.cubicTo(21.6f, 16.8f, 20f, 18.5f, 17.6f, 18.5f); P.close();
                c.drawPath(P, line(col, 1.8f));
                c.drawLine(12, 16.6f, 12, 10.6f, line(col, 1.8f));
                P.reset();
                P.moveTo(9.6f, 12.8f); P.lineTo(12, 10.4f); P.lineTo(14.4f, 12.8f);
                c.drawPath(P, line(col, 1.8f));
                break;
            }
            case HANDSHAKE: {
                // Empate: dos medias lunas enfrentadas
                c.drawCircle(8.4f, 12, 5.6f, line(col, 2f));
                c.drawCircle(15.6f, 12, 5.6f, line(col, 2f));
                break;
            }
            default:
                c.drawCircle(12, 12, 6, fill(col));
        }
    }
}
