package com.kolopenco.poch;

import android.graphics.Color;
import android.graphics.Typeface;

/** Colores, tipografías y medidas del mockup aprobado. */
final class Theme {
    private Theme() {}

    static float D = 3f; // densidad de pantalla (px por dp)

    static float dp(float v) { return v * D; }
    static int dpi(float v) { return Math.round(v * D); }

    // Tipografía Inter (OFL). Se cargan desde assets al arrancar.
    static Typeface REG = Typeface.DEFAULT;
    static Typeface MED = Typeface.DEFAULT;
    static Typeface SEMI = Typeface.DEFAULT_BOLD;
    static Typeface BOLD = Typeface.DEFAULT_BOLD;
    static Typeface BLACK = Typeface.DEFAULT_BOLD;

    // Fondo y superficies
    static final int BG = 0xFF080F1D;
    static final int BG2 = 0xFF0C1526;
    static final int SURFACE = 0xFF121B2C;
    static final int SURFACE2 = 0xFF18233A;
    static final int SURFACE3 = 0xFF1F2B44;
    static final int BORDER = 0xFF223050;
    static final int BORDER2 = 0xFF2C3B5E;

    // Texto
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT2 = 0xFFB4BFD2;
    static final int MUTED = 0xFF7F8CA6;
    static final int FAINT = 0xFF4E5B75;

    // Acentos
    static final int BLUE = 0xFF1E7BF2;
    static final int BLUE_HI = 0xFF3A92FF;
    static final int BLUE_LO = 0xFF1460D6;
    static final int BLUE_TEXT = 0xFF4AA0FF;
    static final int BLUE_BG = 0xFF0E2547;
    static final int GREEN = 0xFF2BC46A;
    static final int GREEN_HI = 0xFF8EF56A;
    static final int RED = 0xFFF0473C;
    static final int RED_HI = 0xFFFF6A5C;
    static final int GOLD = 0xFFF2B41F;
    static final int GOLD_HI = 0xFFFFD45A;
    static final int YELLOW = 0xFFFFC83A;
    static final int SILVER = 0xFFC9D2DE;
    static final int BRONZE = 0xFFD3874A;
    static final int ORANGE = 0xFFF28B24;

    /** Colores de jugador, en el orden en que se asignan. */
    static final int[] PLAYER_COLORS = {
            0xFF1E9E54, // verde
            0xFFE2531C, // naranja
            0xFF1C6FE3, // azul
            0xFF2D9BEA, // celeste
            0xFFE3A214, // dorado
            0xFF8C44D6, // morado
            0xFFEE5AAE, // rosa
            0xFFE2353A, // rojo
            0xFF33CDB0, // turquesa
            0xFFA294F0, // lavanda
            0xFF7A9A1E, // oliva
            0xFFB86A3C, // canela
    };

    static int lighten(int c, float f) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return Color.argb(Color.alpha(c), r, g, b);
    }

    static int darken(int c, float f) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * (1 - f)),
                (int) (Color.green(c) * (1 - f)), (int) (Color.blue(c) * (1 - f)));
    }

    static int alpha(int c, float a) {
        return (c & 0x00FFFFFF) | (Math.round(255 * Math.max(0, Math.min(1, a))) << 24);
    }

    /** Luminosidad aproximada (0..1), para decidir texto claro u oscuro. */
    static float luma(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }
}
