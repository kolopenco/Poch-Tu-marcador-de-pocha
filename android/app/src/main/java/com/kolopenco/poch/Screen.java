package com.kolopenco.poch;

import android.content.Intent;
import android.view.View;
import android.widget.ScrollView;

/** Una pantalla de la app. Se reconstruye cada vez que vuelve a mostrarse. */
abstract class Screen {
    final MainActivity a;
    final Store st;
    /** ScrollView principal, para conservar la posición al volver. */
    ScrollView scroll;
    int scrollY;

    Screen(MainActivity a) {
        this.a = a;
        this.st = a.store;
    }

    abstract View build();

    /** Devuelve true si la pantalla gestiona el botón atrás por sí misma. */
    boolean onBack() { return false; }

    void onResult(int request, int result, Intent data) { }

    void rememberScroll() {
        if (scroll != null) scrollY = scroll.getScrollY();
    }

    void restoreScroll() {
        if (scroll != null && scrollY > 0) {
            final ScrollView s = scroll;
            final int y = scrollY;
            s.post(() -> s.scrollTo(0, y));
        }
    }
}
