package com.kolopenco.poch;

import android.os.Handler;
import android.os.Looper;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Group;
import com.kolopenco.poch.Model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Une Cloud (sincronización) con los datos del móvil y la interfaz. */
final class Sync {
    private Sync() {}

    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static boolean busy, again;

    static final class AppHost implements Cloud.Host {
        final Store st;

        AppHost(Store st) { this.st = st; }

        @Override public List<Player> players() { return st.players; }
        @Override public Map<String, Game> games() { return st.games; }
        @Override public List<Group> groups() { return st.groups; }
        @Override public void save() { st.saveAll(); }

        @Override public void main(Runnable r) throws Exception {
            if (Looper.myLooper() == Looper.getMainLooper()) { r.run(); return; }
            final Throwable[] err = new Throwable[1];
            CountDownLatch done = new CountDownLatch(1);
            UI.post(() -> {
                try { r.run(); } catch (Throwable t) { err[0] = t; }
                done.countDown();
            });
            done.await();
            if (err[0] != null) {
                Throwable t = err[0];
                if (t instanceof RuntimeException && t.getCause() instanceof Exception) throw (Exception) t.getCause();
                if (t instanceof Exception) throw (Exception) t;
                throw new RuntimeException(t);
            }
        }
    }

    static AppHost host(MainActivity a) { return new AppHost(a.store); }

    interface Work<T> { T run() throws Exception; }
    interface Done<T> { void ok(T v); }

    /** Hace {@code w} en segundo plano y vuelve a la interfaz con el resultado o el error explicado. */
    static <T> void run(MainActivity a, Work<T> w, Done<T> ok, Done<String> fail) {
        NET.execute(() -> {
            try {
                T v = w.run();
                UI.post(() -> ok.ok(v));
            } catch (Exception e) {
                String m = Cloud.explain(e);
                UI.post(() -> fail.ok(m));
            }
        });
    }

    // ------------------------------------------------------------------ cambios locales

    /** Partida cerrada o corregida: se marca para subir y se sincroniza. */
    static void changed(MainActivity a, Game g) {
        if (g.group == null) return;
        Cloud.markGame(host(a), g);
        quiet(a, false);
    }

    static void deleted(MainActivity a, Game g) {
        if (g.group == null) return;
        Cloud.markDeleted(host(a), g);
        quiet(a, false);
    }

    static void playerChanged(MainActivity a, Player p) {
        if (a.store.groups.isEmpty()) return;
        Cloud.markPlayer(host(a), p);
        quiet(a, false);
    }

    // ------------------------------------------------------------------ sincronización silenciosa

    /** Sincroniza todos los grupos en segundo plano. Si llega algo nuevo, avisa (y redibuja si se puede). */
    static void quiet(MainActivity a, boolean announce) {
        if (a.store.groups.isEmpty()) return;
        synchronized (Sync.class) {
            if (busy) { again = true; return; }
            busy = true;
        }
        AppHost h = host(a);
        List<Group> gs = new ArrayList<>(a.store.groups);
        NET.execute(() -> {
            int news = 0, upd = 0, players = 0;
            String error = null;
            for (Group g : gs) {
                try {
                    Cloud.SyncResult r = Cloud.syncGroup(h, g);
                    news += r.newGames; upd += r.updatedGames; players += r.players;
                } catch (Exception e) {
                    if (!(e instanceof Cloud.CloudException && ((Cloud.CloudException) e).offline)) error = g.name + ": " + Cloud.explain(e);
                }
            }
            final int n = news, u = upd, pl = players;
            final String err = error;
            UI.post(() -> {
                boolean rerun;
                synchronized (Sync.class) { busy = false; rerun = again; again = false; }
                if (n + u + pl > 0) {
                    StringBuilder s = new StringBuilder();
                    if (n > 0) s.append(Ui.plural(n, "partida nueva", "partidas nuevas"));
                    if (u > 0) s.append(s.length() > 0 ? " · " : "").append(Ui.plural(u, "partida actualizada", "partidas actualizadas"));
                    if (n + u == 0) s.append(Ui.plural(pl, "jugador nuevo", "jugadores nuevos"));
                    a.toast("Grupo: " + s);
                    a.refreshIfIdle();
                } else if (err != null && announce) {
                    a.toast("No se pudo sincronizar · " + err);
                }
                if (rerun) quiet(a, false);
            });
        });
    }
}
