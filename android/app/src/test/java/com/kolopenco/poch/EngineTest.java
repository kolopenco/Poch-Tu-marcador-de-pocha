package com.kolopenco.poch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Res;
import com.kolopenco.poch.Model.Round;
import com.kolopenco.poch.Model.Rules;
import com.kolopenco.poch.Model.Snap;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pruebas de aceptación del README (sección 15) que dependen de la lógica. */
public class EngineTest {

    static Game game(int n, String mode, Rules r) {
        List<String> order = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> colors = new HashMap<>();
        for (int i = 0; i < n; i++) {
            order.add("p" + i);
            names.put("p" + i, "J" + i);
            colors.put("p" + i, 0xFF000000 + i);
        }
        return Engine.newGame("g", r == null ? new Rules() : r, mode, order, names, colors, 1000);
    }

    static int auctions(Game g) {
        int k = 0;
        for (Round r : g.rounds) if (r.auction) k++;
        return k;
    }

    static List<Integer> cards(Game g) {
        List<Integer> out = new ArrayList<>();
        for (Round r : g.rounds) out.add(r.cards);
        return out;
    }

    /** Rellena la ronda en modo sencillo: todos aciertan 0 salvo lo indicado. */
    static void fillSimple(Game g, Round rd, int... values) {
        int i = 0;
        for (String p : Engine.players(g, rd)) rd.r(p).v = i < values.length ? values[i++] : 0;
        if (rd.auction) rd.winner = Engine.players(g, rd).get(0);
    }

    // 1, 2, 3
    @Test public void rondasCinco() {
        Game g = game(5, Model.SIMPLE, null);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 7, 7, 7, 7, 6, 5, 4, 3, 2, 1), cards(g));
        assertEquals(17, g.rounds.size());
        assertEquals(5, auctions(g));
        for (Round r : g.rounds) assertEquals(r.cards == 7, r.auction);
    }

    @Test public void rondasSeis() {
        Game g = game(6, Model.SIMPLE, null);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 6, 6, 6, 6, 6, 5, 4, 3, 2, 1), cards(g));
        assertEquals(6, auctions(g));
    }

    @Test public void rondasSiete() {
        Game g = game(7, Model.SIMPLE, null);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 4, 3, 2, 1), cards(g));
        assertEquals(7, auctions(g));
    }

    @Test public void secuenciaPersonalizada() {
        Rules r = new Rules();
        r.seqType = "custom";
        r.customSeq = "1, 3, 5, 9, 5";
        Game g = game(5, Model.SIMPLE, r);
        assertEquals(Arrays.asList(1, 3, 5, 8, 5), cards(g)); // 9 se limita a 40/5 = 8
        assertEquals(1, auctions(g));
    }

    // 4, 5
    @Test public void botonesSencillo() {
        Rules r = new Rules();
        Integer v = Engine.stepSimple(null, +1, 5);
        assertEquals(Integer.valueOf(0), v);
        Res x = new Res(); x.v = v;
        assertEquals(Integer.valueOf(10), Engine.points(r, Model.SIMPLE, null, x));
        assertEquals(Integer.valueOf(1), Engine.stepSimple(0, +1, 5));
        Integer m = Engine.stepSimple(null, -1, 5);
        assertEquals(Integer.valueOf(-1), m);
        x.v = m;
        assertEquals(Integer.valueOf(-5), Engine.points(r, Model.SIMPLE, null, x));
        assertEquals(Integer.valueOf(-5), Engine.stepSimple(-5, -1, 5));
        assertEquals(Integer.valueOf(0), Engine.stepFull(null, -1, 5));
        assertEquals(Integer.valueOf(5), Engine.stepFull(5, +1, 5));
    }

    // 6, 7 y tabla del README
    @Test public void tablaPuntos() {
        Rules r = new Rules();
        int[][] t = {{0, 10, 10}, {1, 15, 20}, {3, 25, 40}, {-1, -5, -10}, {-2, -10, -20}};
        for (int[] row : t) {
            Res x = new Res(); x.v = row[0];
            assertEquals(Integer.valueOf(row[1]), Engine.points(r, Model.SIMPLE, null, x));
            assertEquals(Integer.valueOf(row[2]), Engine.points(r, Model.SIMPLE, "oros", x));
        }
        Res f = new Res(); f.bid = 3; f.made = 3;
        assertEquals(Integer.valueOf(40), Engine.points(r, Model.FULL, "oros", f));
        f.made = 1;
        assertEquals(Integer.valueOf(-20), Engine.points(r, Model.FULL, "oros", f));
        // Copas desactivadas por defecto: no multiplican
        Res c = new Res(); c.v = 3;
        assertEquals(Integer.valueOf(25), Engine.points(r, Model.SIMPLE, "copas", c));
        // Variante que multiplica también la base
        r.multBase = true;
        assertEquals(Integer.valueOf(50), Engine.points(r, Model.SIMPLE, "oros", c));
    }

    // 8, 9, 10
    @Test public void subasta() {
        Game g = game(5, Model.SIMPLE, null);
        Round rd = g.rounds.get(7);
        assertTrue(rd.auction);
        rd.mult = "oros";
        for (String p : g.order) rd.r(p).v = 1;
        rd.r("p4").v = -1;
        assertEquals(Integer.valueOf(20), Engine.points(g, rd, "p0"));
        assertFalse(Engine.canSave(g, rd));        // falta adjudicatario
        rd.winner = "p1";
        assertTrue(Engine.canSave(g, rd));
        rd.winner = "p3";                          // marcar otro sustituye al anterior
        assertEquals("p3", rd.winner);
        rd.r("p2").v = null;
        assertFalse(Engine.canSave(g, rd));        // falta un resultado
    }

    // 11
    @Test public void modoCompleto() {
        Game g = game(3, Model.FULL, null);
        Round rd = g.rounds.get(2); // 3 cartas
        assertEquals(3, rd.cards);
        rd.r("p0").bid = 1; rd.r("p0").made = 1;
        rd.r("p1").bid = 1; rd.r("p1").made = 1;
        rd.r("p2").bid = 1; rd.r("p2").made = 0;
        assertFalse(Engine.canSave(g, rd));        // hechas suman 2 y pedidas 3
        rd.r("p2").made = 1;
        assertFalse(Engine.canSave(g, rd));        // pedidas suman 3: prohibido
        rd.r("p2").bid = 0;
        assertTrue(Engine.canSave(g, rd));
        g.rules.forbidSum = false;
        rd.r("p2").bid = 1;
        assertTrue(Engine.canSave(g, rd));
        assertEquals(Integer.valueOf(15), Engine.points(g, rd, "p0"));
    }

    // 14
    @Test public void edicionYDeshacer() {
        Game g = game(4, Model.SIMPLE, null);
        for (int i = 0; i < 3; i++) {
            Round rd = g.rounds.get(i);
            fillSimple(g, rd, 1, 0, -1, 0);
            Engine.save(g, rd, 2000 + i);
        }
        // p0: 15 por ronda → 45
        assertEquals(45, Engine.total(g, 2, "p0"));
        Round r0 = g.rounds.get(0);
        Snap before = r0.snap();
        r0.r("p0").v = -1;
        assertTrue(Engine.commitEdit(g, r0, before, 5000));
        assertEquals(25, Engine.total(g, 2, "p0"));   // −5 + 15 + 15
        assertTrue(Engine.edited(r0));
        assertEquals(1, Engine.editedPlayers(g, r0).size());
        assertEquals(3, Engine.current(g));            // no avanza la partida
        assertTrue(Engine.undo(g, r0, 6000));
        assertEquals(45, Engine.total(g, 2, "p0"));
        assertFalse(Engine.edited(r0));
        assertFalse(Engine.canUndo(r0));
        assertEquals(2, r0.revs.size());               // queda constancia
        Snap same = r0.snap();
        assertFalse(Engine.commitEdit(g, r0, same, 7000)); // sin cambios no se registra
    }

    // 15 y persistencia (13)
    @Test public void jsonIdaVuelta() throws Exception {
        Game g = game(5, Model.SIMPLE, null);
        fillSimple(g, g.rounds.get(0), 0, 1, -1, 0, 0);
        Engine.save(g, g.rounds.get(0), 3000);
        g.rounds.get(1).r("p0").v = 2; // borrador
        String s = Json.game(g).toString();
        Game h = Json.game(new JSONObject(s));
        assertEquals(17, h.rounds.size());
        assertEquals(1, Engine.current(h));
        assertEquals(Integer.valueOf(2), h.rounds.get(1).r("p0").v);
        assertNull(h.rounds.get(0).r("p0").bid);
        assertNull(h.rounds.get(0).r("p0").made);
        assertNull(h.rounds.get(1).res.get("p1"));
        assertEquals(Model.SIMPLE, h.mode);
        assertEquals(Engine.total(g, "p1"), Engine.total(h, "p1"));
        assertEquals(s, Json.game(h).toString());
    }

    // 16
    @Test public void anticipada() {
        Game g = game(3, Model.SIMPLE, null);
        for (int i = 0; i < 4; i++) { fillSimple(g, g.rounds.get(i), 1, 0, -1); Engine.save(g, g.rounds.get(i), 1); }
        g.status = Model.EARLY;
        assertTrue(Engine.winners(g).isEmpty());
        Engine.PStats s = Engine.stats(Arrays.asList(g), "p0");
        assertEquals(1, s.games);
        assertEquals(0, s.completed);
        assertEquals(0.0, s.wins, 1e-9);
        assertEquals(4, s.rounds);
        assertEquals(4, s.hits);
    }

    static Game playAll(int n, String tie, int[] perPlayer) {
        Rules r = new Rules();
        r.tie = tie;
        Game g = game(n, Model.SIMPLE, r);
        while (!Engine.allSaved(g)) {
            Round rd = g.rounds.get(Engine.current(g));
            if (rd.tiebreak) {
                List<String> ps = Engine.players(g, rd);
                rd.r(ps.get(0)).v = 1; // el primero gana el desempate
                for (int i = 1; i < ps.size(); i++) rd.r(ps.get(i)).v = -1;
            } else {
                fillSimple(g, rd, perPlayer);
            }
            Engine.save(g, rd, 1);
        }
        return g;
    }

    // 17
    @Test public void empateCompartido() {
        Game g = playAll(3, Model.TIE_SHARED, new int[]{1, 1, -1});
        g.status = Model.COMPLETED;
        assertEquals(2, Engine.winners(g).size());
        assertEquals(0.5, Engine.winShare(g, "p0"), 1e-9);
        assertEquals(0.5, Engine.winShare(g, "p1"), 1e-9);
        assertEquals(0, Engine.winShare(g, "p2"), 1e-9);
        Engine.PStats s = Engine.stats(Arrays.asList(g), "p1");
        assertEquals(0.5, s.wins, 1e-9);
        assertEquals(1.0, s.posAvg(), 1e-9);
        assertEquals(1, Engine.stats(Arrays.asList(g), "p2").lasts);
    }

    @Test public void desempate() {
        Game g = playAll(3, Model.TIE_BREAK, new int[]{1, 1, -1});
        Round last = g.rounds.get(g.rounds.size() - 1);
        assertTrue(last.tiebreak);
        assertEquals(1, last.cards);
        assertEquals(Arrays.asList("p0", "p1"), last.only);
        assertTrue(Engine.finished(g));
        assertEquals(Arrays.asList("p0"), Engine.winners(g));
        assertNull(Engine.points(g, last, "p2"));
    }

    // 18
    @Test public void subconjunto() {
        List<Game> games = new ArrayList<>();
        Game g = playAll(10, Model.TIE_SHARED, new int[]{2, 1, 0, -1, -2, 0, 0, 0, 0, 0});
        g.status = Model.COMPLETED;
        games.add(g);
        Map<String, Engine.PStats> m = Engine.statsAll(games, Arrays.asList("p0", "p3", "p5", "p9"));
        assertEquals(4, m.size());
        assertEquals(1.0, m.get("p0").wins, 1e-9);
        assertEquals(0, m.get("p3").hits);
        assertEquals(g.rounds.size(), m.get("p3").misses);
        assertEquals(1, m.get("p0").completed);
    }

    // 19
    @Test public void repartidor() {
        Game g = game(4, Model.SIMPLE, null);
        assertEquals("p0", Engine.dealer(g, 0));
        assertEquals("p1", Engine.dealer(g, 1));
        assertEquals("p3", Engine.dealer(g, 3));
        assertEquals("p0", Engine.dealer(g, 4));
    }

    @Test public void estadisticasSubasta() {
        Game g = game(3, Model.FULL, null);
        while (!Engine.allSaved(g)) {
            Round rd = g.rounds.get(Engine.current(g));
            rd.r("p0").bid = rd.cards; rd.r("p0").made = rd.cards;     // acierta todo
            rd.r("p1").bid = 1; rd.r("p1").made = 0;
            rd.r("p2").bid = 0; rd.r("p2").made = 0;
            if (rd.cards == 1) { rd.r("p1").bid = 1; }
            if (rd.auction) rd.winner = "p0";
            assertTrue(Engine.problems(g, rd).toString(), Engine.canSave(g, rd));
            Engine.save(g, rd, 1);
        }
        g.status = Model.COMPLETED;
        Engine.PStats s = Engine.stats(Arrays.asList(g), "p0");
        assertEquals(3, s.aucPlayed);
        assertEquals(3, s.aucWon);
        assertEquals(3, s.aucWonHit);
        assertEquals(100.0, s.fullHitPct(), 1e-9);
        Engine.PStats s1 = Engine.stats(Arrays.asList(g), "p1");
        assertEquals(3, s1.aucPlayed);
        assertEquals(0, s1.aucWon);
        assertEquals(0.0, s1.aucPct(), 1e-9);
    }

    // Comprobación del modo sencillo (petición de Michael, 18/10/2026)
    @Test public void sencilloComprobaciones() {
        Game g = game(4, Model.SIMPLE, null);
        Round rd = g.rounds.get(2); // 3 cartas
        assertEquals(3, rd.cards);
        // Todos aciertan: con la regla activa, alguien tiene que fallar
        rd.r("p0").v = 1; rd.r("p1").v = 1; rd.r("p2").v = 1; rd.r("p3").v = 0;
        assertFalse(Engine.canSave(g, rd));
        // Más bazas acertadas que cartas: imposible
        rd.r("p3").v = -1; rd.r("p0").v = 2;
        assertFalse(Engine.canSave(g, rd));      // 2 + 1 + 1 = 4 > 3
        rd.r("p0").v = 1;
        assertTrue(Engine.canSave(g, rd));       // 3 bazas acertadas y un fallo
        // Sin la regla: si aciertan todos, deben sumar exactamente las cartas
        g.rules.forbidSum = false;
        rd.r("p3").v = 0;
        assertTrue(Engine.canSave(g, rd));       // 1 + 1 + 1 + 0 = 3
        rd.r("p0").v = 0;
        assertFalse(Engine.canSave(g, rd));      // suman 2
    }

    // Comparativa: solo partidas en las que jugaron todos los elegidos (Michael, 21/10/2026)
    @Test public void partidasJuntos() {
        Game a = playAll(3, Model.TIE_SHARED, new int[]{1, 0, -1});           // p0 p1 p2
        a.status = Model.COMPLETED; a.id = "a";
        Game b = playAll(4, Model.TIE_SHARED, new int[]{-1, 1, 0, -1});       // p0 p1 p2 p3
        b.status = Model.COMPLETED; b.id = "b";
        Game c = playAll(2, Model.TIE_SHARED, new int[]{1, -1});              // p0 p1
        c.status = Model.COMPLETED; c.id = "c";
        List<Game> all = Arrays.asList(a, b, c);
        assertEquals(3, Engine.withAll(all, Arrays.asList("p0", "p1")).size());
        assertEquals(2, Engine.withAll(all, Arrays.asList("p0", "p1", "p2")).size());
        assertEquals(1, Engine.withAll(all, Arrays.asList("p0", "p3")).size());
        // Ficha de p0 filtrada «con p2 y p3»: solo la partida b
        Engine.PStats s = Engine.stats(Engine.withAll(all, Arrays.asList("p0", "p2", "p3")), "p0");
        assertEquals(1, s.games);
        assertEquals(0, s.hits);
    }
}
