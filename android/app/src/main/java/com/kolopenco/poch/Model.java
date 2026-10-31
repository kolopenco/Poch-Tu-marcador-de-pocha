package com.kolopenco.poch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Datos de la app. Java puro (sin Android) para poder probarlo fuera del móvil. */
final class Model {
    private Model() {}

    static final String SIMPLE = "simple";
    static final String FULL = "full";

    static final String ACTIVE = "active";
    static final String COMPLETED = "completed";
    static final String EARLY = "early";

    static final String TIE_SHARED = "shared";
    static final String TIE_BREAK = "tiebreak";

    static final String[] SUITS = {"oros", "copas", "espadas", "bastos"};
    static final String[] SUIT_NAMES = {"Oros", "Copas", "Espadas", "Bastos"};

    static String suitName(String s) {
        for (int i = 0; i < SUITS.length; i++) if (SUITS[i].equals(s)) return SUIT_NAMES[i];
        return s;
    }

    static final class Player {
        String id;
        String name;
        int color;
        long created;
        /** Última vez que se cambió el nombre o el color (para sincronizar con los grupos). */
        long updated;
    }

    /** Grupo de juego sincronizado con Firebase (ver Cloud). */
    static final class Group {
        String id, name, apiKey, projectId, key;
        String uid, refresh;
        boolean owner;
        /** Jugador local que es «este móvil». */
        String me;
        /** idEnElGrupo → idLocal, para jugadores unidos con uno que ya tenía. */
        Map<String, String> ids = new LinkedHashMap<>();
        /** Jugadores que ya están en el grupo (ids del grupo). */
        java.util.Set<String> roster = new java.util.LinkedHashSet<>();
        List<String> pending = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        List<String> dirtyPlayers = new ArrayList<>();
        String lastPlayers, lastGames;
        long lastSync;
        /** Nombre de «tú» al unirse, por si aún no está entre los jugadores del móvil. */
        String meName;
        /** 2 = el nombre de este móvil ya está bien apuntado en el grupo. */
        int memberV;
    }

    static final class Rules {
        String id = "";
        String name = "Pocha clásica";
        int deck = 40;
        int maxCards = 7;
        /** "standard" (sube, repite el máximo y baja) o "custom" (lista escrita). */
        String seqType = "standard";
        String customSeq = "";
        /** Veces que se repite la ronda máxima; 0 = tantas como jugadores. */
        int repeatMax = 0;
        /** Las rondas con el máximo de cartas son subastadas. */
        boolean auctionMax = true;
        int base = 10;
        int perTrick = 5;
        int penalty = 5;
        /** El multiplicador afecta también a la base. */
        boolean multBase = false;
        boolean[] multOn = {true, false, false, false};
        int[] multF = {2, 2, 2, 2};
        /** La suma de pedidas no puede igualar las bazas de la ronda (solo modo completo). */
        boolean forbidSum = true;
        String tie = TIE_SHARED;

        Rules copy() {
            Rules r = new Rules();
            r.id = id; r.name = name; r.deck = deck; r.maxCards = maxCards;
            r.seqType = seqType; r.customSeq = customSeq; r.repeatMax = repeatMax;
            r.auctionMax = auctionMax; r.base = base; r.perTrick = perTrick; r.penalty = penalty;
            r.multBase = multBase; r.multOn = multOn.clone(); r.multF = multF.clone();
            r.forbidSum = forbidSum; r.tie = tie;
            return r;
        }

        int factor(String suit) {
            if (suit == null) return 1;
            for (int i = 0; i < SUITS.length; i++)
                if (SUITS[i].equals(suit)) return multOn[i] ? Math.max(1, multF[i]) : 1;
            return 1;
        }

        boolean anyMult() {
            for (boolean b : multOn) if (b) return true;
            return false;
        }
    }

    /** Resultado de un jugador en una ronda. Null = sin introducir («—»). */
    static final class Res {
        Integer v;      // modo sencillo: n ≥ 0 acierto de n bazas; −k fallo por k
        Integer bid;    // modo completo: pedidas
        Integer made;   // modo completo: hechas

        Res copy() {
            Res r = new Res();
            r.v = v; r.bid = bid; r.made = made;
            return r;
        }

        boolean same(Res o) {
            if (o == null) return v == null && bid == null && made == null;
            return eq(v, o.v) && eq(bid, o.bid) && eq(made, o.made);
        }

        static boolean eq(Integer a, Integer b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    /** Estado editable de una ronda (lo que se guarda en cada revisión). */
    static final class Snap {
        String mult;
        String winner;
        Map<String, Res> res = new LinkedHashMap<>();

        Snap copy() {
            Snap s = new Snap();
            s.mult = mult; s.winner = winner;
            for (Map.Entry<String, Res> e : res.entrySet()) s.res.put(e.getKey(), e.getValue().copy());
            return s;
        }
    }

    static final class Revision {
        long at;
        Snap before;
        Snap after;
        boolean undone;
        /** true si esta entrada registra un «deshacer». */
        boolean isUndo;
    }

    static final class Round {
        int cards;
        boolean auction;
        boolean tiebreak;
        /** Null = juegan todos; en desempates, solo los empatados. */
        List<String> only;
        String mult;
        String winner;
        Map<String, Res> res = new LinkedHashMap<>();
        boolean saved;
        long savedAt;
        List<Revision> revs = new ArrayList<>();

        Res r(String pid) {
            Res x = res.get(pid);
            if (x == null) { x = new Res(); res.put(pid, x); }
            return x;
        }

        boolean plays(String pid) {
            return only == null || only.contains(pid);
        }

        Snap snap() {
            Snap s = new Snap();
            s.mult = mult; s.winner = winner;
            for (Map.Entry<String, Res> e : res.entrySet()) s.res.put(e.getKey(), e.getValue().copy());
            return s;
        }

        void apply(Snap s) {
            mult = s.mult; winner = s.winner;
            res.clear();
            for (Map.Entry<String, Res> e : s.res.entrySet()) res.put(e.getKey(), e.getValue().copy());
        }

        /** Estado original (antes de la primera corrección), o null si nunca se editó. */
        Snap original() {
            for (Revision rv : revs) if (!rv.isUndo) return rv.before;
            return null;
        }
    }

    static final class Game {
        String id;
        String status = ACTIVE;
        long created;
        long ended;
        String mode = SIMPLE;
        List<String> order = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        Rules rules = new Rules();
        List<Round> rounds = new ArrayList<>();
        /** El usuario ya vio la clasificación final y la cerró. */
        boolean closed;
        /** Grupo de juego al que pertenece (null = solo en este móvil). */
        String group;
        /** Última modificación (para sincronizar). */
        long updated;

        int n() { return order.size(); }

        String name(String pid) {
            String s = names.get(pid);
            return s == null ? "?" : s;
        }

        int color(String pid) {
            Integer c = colors.get(pid);
            return c == null ? 0xFF607080 : c;
        }
    }
}
