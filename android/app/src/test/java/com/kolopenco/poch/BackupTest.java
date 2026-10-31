package com.kolopenco.poch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kolopenco.poch.Model.Game;
import com.kolopenco.poch.Model.Player;

import org.junit.Test;

public class BackupTest {

    @Test public void idaYVuelta() throws Exception {
        Backup.Data d = new Backup.Data();
        for (int i = 0; i < 5; i++) {
            Player p = new Player();
            p.id = "p" + i; p.name = "Jugador " + i; p.color = 0xFF112233 + i; p.created = i;
            d.players.add(p);
        }
        d.profiles.add(Backup.defaultRules());
        d.defaultProfile = "clasica";
        Game g = EngineTest.game(5, Model.SIMPLE, null);
        EngineTest.fillSimple(g, g.rounds.get(0), 1, 0, -1, 0, 2);
        Engine.save(g, g.rounds.get(0), 5);
        d.games.add(g);
        d.activeGame = g.id;
        d.statsSel.add("p1");
        String s = Backup.build(d, "0.2.0", 99).toString();
        Backup.Data e = Backup.parse(s);
        assertEquals(5, e.players.size());
        assertEquals(1, e.games.size());
        assertEquals("g", e.activeGame);
        assertEquals("Jugador 3", e.players.get(3).name);
        assertEquals(Engine.total(g, "p4"), Engine.total(e.games.get(0), "p4"));
        assertEquals(s, Backup.build(e, "0.2.0", 99).toString());
    }

    @Test public void rechazaArchivosAjenos() {
        String[] bad = {"hola", "{}", "{\"app\":\"Otra\"}", "{\"app\":\"Poch\",\"schema\":99}"};
        for (String b : bad) {
            boolean failed = false;
            try { Backup.parse(b); } catch (Exception ex) { failed = true; }
            assertTrue(b, failed);
        }
    }

    static Player pl(String id, String name) {
        Player p = new Player();
        p.id = id; p.name = name; p.color = 1; p.created = 0;
        return p;
    }

    /** Mezclar copias: emparejar jugadores, añadir partidas, no duplicar (Michael, 31/10/2026). */
    @Test public void mezclar() throws Exception {
        Backup.Data mine = new Backup.Data();
        mine.players.add(pl("m1", "Mich"));
        mine.players.add(pl("m2", "Berto"));
        mine.profiles.add(Backup.defaultRules());
        mine.defaultProfile = "clasica";

        // Copia de otro teléfono: «Michael» (que es Mich), «Berto» (mismo nombre) y «Ana» (nueva)
        Backup.Data in = new Backup.Data();
        in.players.add(pl("x1", "Michael"));
        in.players.add(pl("x2", "berto "));
        in.players.add(pl("x3", "Ana"));
        java.util.List<String> order = java.util.Arrays.asList("x1", "x2", "x3");
        java.util.Map<String, String> names = new java.util.HashMap<>();
        java.util.Map<String, Integer> colors = new java.util.HashMap<>();
        for (Player p : in.players) { names.put(p.id, p.name); colors.put(p.id, 2); }
        Model.Rules r = new Model.Rules();
        r.forbidSum = false;
        Game g = Engine.newGame("g1", r, Model.SIMPLE, order, names, colors, 10);
        while (!Engine.allSaved(g)) {
            Model.Round rd = g.rounds.get(Engine.current(g));
            EngineTest.fillSimple(g, rd, 1, 0, -1);
            Engine.save(g, rd, 1);
        }
        g.status = Model.COMPLETED;
        in.games.add(g);
        Game live = Engine.newGame("g2", r, Model.SIMPLE, order, names, colors, 20);
        in.games.add(live);
        in.profiles.add(Backup.defaultRules());

        java.util.Map<String, String> map = Backup.suggest(mine, in);
        assertEquals(null, map.get("x1"));       // nombre distinto: no se adivina
        assertEquals("m2", map.get("x2"));       // mismo nombre (sin mayúsculas ni espacios)
        assertEquals(null, map.get("x3"));
        map.put("x1", "m1");                     // el usuario une «Michael» con «Mich»

        Backup.MergeResult res = Backup.merge(mine, in, map);
        assertEquals(1, res.newPlayers);
        assertEquals(2, res.mergedPlayers);
        assertEquals(1, res.newGames);
        assertEquals(1, res.skippedActive);
        assertEquals(3, res.data.players.size());
        Game got = res.data.games.get(0);
        assertEquals(java.util.Arrays.asList("m1", "m2", "x3"), got.order);
        assertEquals("Mich", got.name("m1"));
        assertEquals(Engine.total(g, "x1"), Engine.total(got, "m1"));
        Engine.PStats s = Engine.stats(res.data.games, "m1");
        assertEquals(1, s.games);

        // Mezclar otra vez la misma copia no duplica nada
        Backup.MergeResult again = Backup.merge(res.data, in, Backup.suggest(res.data, in));
        assertEquals(1, again.data.games.size());
        assertEquals(1, again.sameGames);
        assertEquals(0, again.newPlayers);
        assertEquals(3, again.data.players.size());

        // Dos jugadores de la copia unidos al mismo mío: error
        map.put("x3", "m1");
        boolean failed = false;
        try { Backup.merge(mine, in, map); } catch (Exception e) { failed = true; }
        assertTrue(failed);
    }
}
