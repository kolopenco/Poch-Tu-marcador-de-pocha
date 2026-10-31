package com.kolopenco.poch;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Res;
import com.kolopenco.poch.Model.Revision;
import com.kolopenco.poch.Model.Round;
import com.kolopenco.poch.Model.Rules;
import com.kolopenco.poch.Model.Snap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reglas de la Pocha: rondas, puntos, validaciones, clasificación y estadísticas. Java puro. */
final class Engine {
    private Engine() {}

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 10;

    // ---------------------------------------------------------------- rondas

    /** Máximo de cartas por jugador: min(máximo deseado, baraja / jugadores). */
    static int maxCards(Rules r, int n) {
        if (n <= 0) return 1;
        return Math.max(1, Math.min(r.maxCards, r.deck / n));
    }

    /** Lista de cartas por jugador de cada ronda. */
    static List<Integer> sequence(Rules r, int n) {
        List<Integer> out = new ArrayList<>();
        int cap = Math.max(1, r.deck / Math.max(1, n));
        if ("custom".equals(r.seqType)) {
            for (String t : r.customSeq.split("[^0-9]+")) {
                if (t.isEmpty()) continue;
                try {
                    int c = Integer.parseInt(t);
                    if (c >= 1) out.add(Math.min(c, cap));
                } catch (NumberFormatException ignored) { }
            }
            if (!out.isEmpty()) return out;
        }
        int m = maxCards(r, n);
        int rep = r.repeatMax > 0 ? r.repeatMax : n;
        for (int c = 1; c < m; c++) out.add(c);
        for (int i = 0; i < rep; i++) out.add(m);
        for (int c = m - 1; c >= 1; c--) out.add(c);
        return out;
    }

    static Game newGame(String id, Rules rules, String mode, List<String> order,
                        Map<String, String> names, Map<String, Integer> colors, long now) {
        Game g = new Game();
        g.id = id;
        g.created = now;
        g.mode = Model.FULL.equals(mode) ? Model.FULL : Model.SIMPLE;
        g.rules = rules.copy();
        g.order.addAll(order);
        for (String p : order) {
            g.names.put(p, names.get(p));
            Integer c = colors.get(p);
            g.colors.put(p, c == null ? 0xFF607080 : c);
        }
        List<Integer> seq = sequence(g.rules, order.size());
        int top = 0;
        for (int c : seq) top = Math.max(top, c);
        for (int c : seq) {
            Round rd = new Round();
            rd.cards = c;
            rd.auction = g.rules.auctionMax && c == top;
            g.rounds.add(rd);
        }
        return g;
    }

    /** Índice de la primera ronda sin guardar (la que se está jugando), o el total si no queda ninguna. */
    static int current(Game g) {
        for (int i = 0; i < g.rounds.size(); i++) if (!g.rounds.get(i).saved) return i;
        return g.rounds.size();
    }

    static int regularCount(Game g) {
        int k = 0;
        for (Round r : g.rounds) if (!r.tiebreak) k++;
        return k;
    }

    static int savedCount(Game g) {
        int k = 0;
        for (Round r : g.rounds) if (r.saved) k++;
        return k;
    }

    static int savedRegular(Game g) {
        int k = 0;
        for (Round r : g.rounds) if (r.saved && !r.tiebreak) k++;
        return k;
    }

    /** Reparte el primero de la lista y rota en cada ronda. */
    static String dealer(Game g, int idx) {
        if (g.order.isEmpty()) return null;
        return g.order.get(((idx % g.n()) + g.n()) % g.n());
    }

    static List<String> players(Game g, Round rd) {
        List<String> out = new ArrayList<>();
        for (String p : g.order) if (rd.plays(p)) out.add(p);
        return out;
    }

    // ---------------------------------------------------------------- botones − / +

    /** Modo sencillo: desde «—», + da 0 (acierto de cero) y − da −1 (fallo por una). */
    static Integer stepSimple(Integer v, int delta, int cards) {
        if (v == null) return delta > 0 ? 0 : -1;
        return Math.max(-cards, Math.min(cards, v + delta));
    }

    /** Modo completo (pedidas o hechas): desde «—» cualquier botón da 0; rango 0..cartas. */
    static Integer stepFull(Integer v, int delta, int cards) {
        if (v == null) return 0;
        return Math.max(0, Math.min(cards, v + delta));
    }

    // ---------------------------------------------------------------- puntos

    static boolean entered(Game g, Round rd, String pid) {
        if (!rd.plays(pid)) return true;
        Res x = rd.res.get(pid);
        if (x == null) return false;
        if (Model.FULL.equals(g.mode)) return x.bid != null && x.made != null;
        return x.v != null;
    }

    /** Acierto (true), fallo (false) o sin dato (null). */
    static Boolean hit(Game g, Round rd, String pid) {
        if (!rd.plays(pid)) return null;
        Res x = rd.res.get(pid);
        if (x == null) return null;
        if (Model.FULL.equals(g.mode)) {
            if (x.bid == null || x.made == null) return null;
            return x.bid.intValue() == x.made.intValue();
        }
        if (x.v == null) return null;
        return x.v >= 0;
    }

    /** Puntos de una ronda para un jugador, o null si falta el dato. */
    static Integer points(Game g, Round rd, String pid) {
        return points(g.rules, g.mode, rd.mult, rd.plays(pid) ? rd.res.get(pid) : null);
    }

    static Integer points(Rules r, String mode, String mult, Res x) {
        if (x == null) return null;
        int f = r.factor(mult);
        int base = r.base * (r.multBase ? f : 1);
        if (Model.FULL.equals(mode)) {
            if (x.bid == null || x.made == null) return null;
            if (x.bid.intValue() == x.made.intValue()) return base + x.made * r.perTrick * f;
            return -Math.abs(x.bid - x.made) * r.penalty * f;
        }
        if (x.v == null) return null;
        if (x.v >= 0) return base + x.v * r.perTrick * f;
        return x.v * r.penalty * f;
    }

    /** Puntos acumulados hasta la ronda idx incluida (con lo provisional que haya). */
    static int total(Game g, int idx, String pid) {
        int t = 0;
        for (int i = 0; i <= idx && i < g.rounds.size(); i++) {
            Integer p = points(g, g.rounds.get(i), pid);
            if (p != null) t += p;
        }
        return t;
    }

    static int total(Game g, String pid) {
        return total(g, g.rounds.size() - 1, pid);
    }

    /** Rondas acertadas [0] y falladas [1] hasta la ronda idx incluida. */
    static int[] af(Game g, int idx, String pid) {
        int a = 0, f = 0;
        for (int i = 0; i <= idx && i < g.rounds.size(); i++) {
            Boolean h = hit(g, g.rounds.get(i), pid);
            if (h == null) continue;
            if (h) a++; else f++;
        }
        return new int[]{a, f};
    }

    // ---------------------------------------------------------------- validación

    /** Motivos por los que todavía no se puede guardar la ronda (vacío = se puede). */
    static List<String> problems(Game g, Round rd) {
        List<String> out = new ArrayList<>();
        List<String> ps = players(g, rd);
        int missing = 0;
        for (String p : ps) if (!entered(g, rd, p)) missing++;
        if (missing > 0) out.add(missing == 1 ? "Falta 1 resultado" : "Faltan " + missing + " resultados");
        if (Model.FULL.equals(g.mode)) {
            int made = 0, bids = 0;
            boolean allMade = true, allBids = true;
            for (String p : ps) {
                Res x = rd.res.get(p);
                if (x == null || x.made == null) allMade = false; else made += x.made;
                if (x == null || x.bid == null) allBids = false; else bids += x.bid;
            }
            if (allMade && made != rd.cards)
                out.add("Las hechas suman " + made + " y hay " + rd.cards + (rd.cards == 1 ? " baza" : " bazas"));
            if (allBids && g.rules.forbidSum && bids == rd.cards && ps.size() > 1)
                out.add("Las pedidas no pueden sumar " + rd.cards);
        }
        if (!Model.FULL.equals(g.mode) && missing == 0 && !ps.isEmpty()) {
            // Modo sencillo: quien acierta hizo exactamente las bazas anotadas.
            int hitTricks = 0, misses = 0;
            for (String p : ps) {
                Integer v = rd.res.get(p).v;
                if (v >= 0) hitTricks += v; else misses++;
            }
            if (hitTricks > rd.cards)
                out.add("Los aciertos suman " + hitTricks + " bazas y solo hay " + rd.cards);
            else if (misses == 0 && g.rules.forbidSum && ps.size() > 1)
                out.add("Alguien tiene que fallar: las pedidas no pueden sumar " + rd.cards);
            else if (misses == 0 && hitTricks != rd.cards)
                out.add("Si aciertan todos, las bazas deben sumar " + rd.cards + " (ahora " + hitTricks + ")");
        }
        if (rd.auction && (rd.winner == null || !ps.contains(rd.winner)))
            out.add("Marca quién se llevó la subasta");
        return out;
    }

    static boolean canSave(Game g, Round rd) {
        return problems(g, rd).isEmpty();
    }

    /** Guarda la ronda en juego y, si hace falta, añade una ronda de desempate. */
    static void save(Game g, Round rd, long now) {
        save(g, rd, now, true);
    }

    /** Con autoTiebreak = false no añade el desempate: la app pregunta antes qué hacer con el empate. */
    static void save(Game g, Round rd, long now, boolean autoTiebreak) {
        rd.saved = true;
        rd.savedAt = now;
        if (!rd.auction) rd.winner = null;
        if (autoTiebreak) ensureTiebreak(g);
    }

    /** Todas las rondas guardadas y varios jugadores empatados en cabeza. */
    static boolean tieAtEnd(Game g) {
        return allSaved(g) && leaders(g).size() > 1;
    }

    /** Cambia la regla de empate en mitad de la partida y ajusta las rondas de desempate. */
    static void setTieRule(Game g, String tie) {
        g.rules.tie = tie;
        if (Model.TIE_BREAK.equals(tie)) {
            ensureTiebreak(g);
        } else {
            for (int i = g.rounds.size() - 1; i >= 0; i--) {
                Round r = g.rounds.get(i);
                if (r.tiebreak && !r.saved) g.rounds.remove(i); else break;
            }
        }
    }

    // ---------------------------------------------------------------- clasificación

    static final class Rank {
        String pid;
        int total;
        int pos;
    }

    /** Clasificación por puntos; los empatados comparten puesto (1, 1, 3…). */
    static List<Rank> standings(Game g) {
        List<Rank> out = new ArrayList<>();
        for (String p : g.order) {
            Rank r = new Rank();
            r.pid = p;
            r.total = total(g, p);
            out.add(r);
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.total, a.total));
        for (int i = 0; i < out.size(); i++) {
            Rank r = out.get(i);
            r.pos = (i > 0 && out.get(i - 1).total == r.total) ? out.get(i - 1).pos : i + 1;
        }
        return out;
    }

    static List<String> leaders(Game g) {
        List<String> out = new ArrayList<>();
        for (Rank r : standings(g)) if (r.pos == 1) out.add(r.pid);
        return out;
    }

    static boolean allSaved(Game g) {
        return current(g) >= g.rounds.size();
    }

    static boolean needsTiebreak(Game g) {
        return allSaved(g) && Model.TIE_BREAK.equals(g.rules.tie) && leaders(g).size() > 1;
    }

    /** Si la partida acabó empatada y la regla es desempatar, añade una ronda de una carta. */
    static boolean ensureTiebreak(Game g) {
        if (!needsTiebreak(g)) return false;
        Round rd = new Round();
        rd.cards = 1;
        rd.tiebreak = true;
        rd.only = new ArrayList<>(leaders(g));
        g.rounds.add(rd);
        return true;
    }

    /** Todas las rondas jugadas y sin desempate pendiente. */
    static boolean finished(Game g) {
        return allSaved(g) && !needsTiebreak(g);
    }

    /** Ganadores. Ninguno si la partida acabó antes de tiempo o aún no ha terminado. */
    static List<String> winners(Game g) {
        if (Model.EARLY.equals(g.status)) return new ArrayList<>();
        if (!allSaved(g)) return new ArrayList<>();
        return leaders(g);
    }

    /** Fracción de victoria (1/k si comparte la victoria con k−1). */
    static double winShare(Game g, String pid) {
        List<String> w = winners(g);
        if (!w.contains(pid)) return 0;
        return 1.0 / w.size();
    }

    // ---------------------------------------------------------------- edición

    /** Registra la corrección si cambió algo. Devuelve true si hubo cambios. */
    static boolean commitEdit(Game g, Round rd, Snap before, long now) {
        Snap after = rd.snap();
        if (sameSnap(before, after)) return false;
        Revision rv = new Revision();
        rv.at = now;
        rv.before = before.copy();
        rv.after = after;
        rd.revs.add(rv);
        if (!rd.auction) rd.winner = null;
        if (Model.ACTIVE.equals(g.status)) ensureTiebreak(g);
        return true;
    }

    static boolean canUndo(Round rd) {
        for (int i = rd.revs.size() - 1; i >= 0; i--) {
            Revision rv = rd.revs.get(i);
            if (!rv.isUndo && !rv.undone) return true;
        }
        return false;
    }

    /** Deshace la última corrección pendiente de la ronda, dejando constancia. */
    static boolean undo(Game g, Round rd, long now) {
        for (int i = rd.revs.size() - 1; i >= 0; i--) {
            Revision rv = rd.revs.get(i);
            if (rv.isUndo || rv.undone) continue;
            Snap cur = rd.snap();
            rd.apply(rv.before);
            rv.undone = true;
            Revision u = new Revision();
            u.at = now;
            u.isUndo = true;
            u.before = cur;
            u.after = rv.before.copy();
            rd.revs.add(u);
            if (Model.ACTIVE.equals(g.status)) ensureTiebreak(g);
            return true;
        }
        return false;
    }

    static boolean sameSnap(Snap a, Snap b) {
        if (!eq(a.mult, b.mult) || !eq(a.winner, b.winner)) return false;
        Set<String> keys = new HashSet<>(a.res.keySet());
        keys.addAll(b.res.keySet());
        for (String k : keys) {
            Res x = a.res.get(k), y = b.res.get(k);
            if (x == null) { if (y != null && !y.same(null)) return false; }
            else if (!x.same(y)) return false;
        }
        return true;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** La ronda tiene correcciones vigentes (difiere de cómo se guardó por primera vez). */
    static boolean edited(Round rd) {
        Snap o = rd.original();
        return o != null && !sameSnap(o, rd.snap());
    }

    /** Jugadores cuyo resultado cambió respecto al original. */
    static Set<String> editedPlayers(Game g, Round rd) {
        Set<String> out = new HashSet<>();
        Snap o = rd.original();
        if (o == null) return out;
        boolean multChanged = !eq(o.mult, rd.mult);
        for (String p : g.order) {
            if (!rd.plays(p)) continue;
            Res a = o.res.get(p), b = rd.res.get(p);
            boolean same = a == null ? (b == null || b.same(null)) : a.same(b);
            boolean wonChanged = (p.equals(o.winner) != p.equals(rd.winner));
            if (!same || wonChanged || (multChanged && b != null)) out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- estadísticas

    /** Partidas en las que jugaron TODOS los jugadores indicados (puede haber más). */
    static List<Game> withAll(List<Game> games, java.util.Collection<String> pids) {
        List<Game> out = new ArrayList<>();
        for (Game g : games) if (g.order.containsAll(pids)) out.add(g);
        return out;
    }

    static boolean counts(Game g) {
        return Model.COMPLETED.equals(g.status) || Model.EARLY.equals(g.status);
    }

    static final class Recent {
        Game game;
        int pos;        // 0 = sin puesto (finalizada antes de tiempo)
        int points;
        boolean won;
    }

    static final class PStats {
        String pid;
        int games;          // completas + anticipadas
        int completed;      // completas
        double wins;
        int lasts;
        int posSum;
        int bestScore = Integer.MIN_VALUE;
        long pointsSum;
        int rounds, hits, misses;
        int fullRounds, bidSum, madeSum, fullHits;
        int aucPlayed, aucWon, aucWonHit;
        List<Recent> recent = new ArrayList<>();

        double posAvg() { return completed == 0 ? Double.NaN : posSum / (double) completed; }
        double pointsAvg() { return completed == 0 ? Double.NaN : pointsSum / (double) completed; }
        double hitPct() { return rounds == 0 ? Double.NaN : 100.0 * hits / rounds; }
        double missPct() { return rounds == 0 ? Double.NaN : 100.0 * misses / rounds; }
        double bidAvg() { return fullRounds == 0 ? Double.NaN : bidSum / (double) fullRounds; }
        double madeAvg() { return fullRounds == 0 ? Double.NaN : madeSum / (double) fullRounds; }
        double fullHitPct() { return fullRounds == 0 ? Double.NaN : 100.0 * fullHits / fullRounds; }
        double aucPct() { return aucPlayed == 0 ? Double.NaN : 100.0 * aucWon / aucPlayed; }
        double aucHitPct() { return aucWon == 0 ? Double.NaN : 100.0 * aucWonHit / aucWon; }
    }

    /** Estadísticas de un jugador sobre las partidas cerradas (completas y anticipadas). */
    static PStats stats(List<Game> games, String pid) {
        PStats s = new PStats();
        s.pid = pid;
        List<Game> mine = new ArrayList<>();
        for (Game g : games) if (counts(g) && g.order.contains(pid)) mine.add(g);
        Collections.sort(mine, (a, b) -> Long.compare(b.created, a.created));
        for (Game g : mine) {
            s.games++;
            for (Round rd : g.rounds) {
                if (!rd.saved || !rd.plays(pid)) continue;
                Boolean h = hit(g, rd, pid);
                if (h == null) continue;
                s.rounds++;
                if (h) s.hits++; else s.misses++;
                if (Model.FULL.equals(g.mode)) {
                    Res x = rd.res.get(pid);
                    s.fullRounds++;
                    s.bidSum += x.bid;
                    s.madeSum += x.made;
                    if (h) s.fullHits++;
                }
                if (rd.auction) {
                    s.aucPlayed++;
                    if (pid.equals(rd.winner)) {
                        s.aucWon++;
                        if (h) s.aucWonHit++;
                    }
                }
            }
            Recent rc = new Recent();
            rc.game = g;
            rc.points = total(g, pid);
            if (Model.COMPLETED.equals(g.status)) {
                s.completed++;
                List<Rank> st = standings(g);
                int pos = 0, worst = 0;
                for (Rank r : st) {
                    if (r.pid.equals(pid)) pos = r.pos;
                    worst = Math.max(worst, r.pos);
                }
                s.posSum += pos;
                if (pos == worst && st.size() > 1) s.lasts++;
                s.pointsSum += rc.points;
                s.bestScore = Math.max(s.bestScore, rc.points);
                s.wins += winShare(g, pid);
                rc.pos = pos;
                rc.won = winShare(g, pid) > 0;
            }
            s.recent.add(rc);
        }
        return s;
    }

    static Map<String, PStats> statsAll(List<Game> games, List<String> pids) {
        Map<String, PStats> out = new LinkedHashMap<>();
        for (String p : pids) out.put(p, stats(games, p));
        return out;
    }
}
