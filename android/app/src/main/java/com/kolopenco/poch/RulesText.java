package com.kolopenco.poch;

import com.kolopenco.poch.Model.Rules;

import java.util.ArrayList;
import java.util.List;

/** Textos que describen unas reglas (resúmenes y filas clave/valor). */
final class RulesText {
    private RulesText() {}

    static String mults(Rules r) {
        List<String> on = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (r.multOn[i]) on.add(Model.SUIT_NAMES[i] + " ×" + r.multF[i]);
        return on.isEmpty() ? "Sin multiplicadores" : android.text.TextUtils.join(", ", on);
    }

    static String summary(Rules r) {
        return "Acierto " + r.base + " + " + r.perTrick + "/baza · Fallo −" + r.penalty + "/baza · " + mults(r);
    }

    static String sequenceText(Rules r, int n) {
        if ("custom".equals(r.seqType)) return "Personalizada";
        String rep = r.repeatMax > 0 ? Ui.plural(r.repeatMax, "vez", "veces") : "una por jugador";
        return "Sube, repite el máximo (" + rep + ") y baja";
    }

    static String tieText(Rules r) {
        return Model.TIE_BREAK.equals(r.tie) ? "Desempate a 1 carta" : "Victoria compartida";
    }

    static List<String[]> lines(Rules r, String mode, int players) {
        List<String[]> l = new ArrayList<>();
        if (mode != null) l.add(new String[]{"Modo de anotación", Model.FULL.equals(mode) ? "Completo (pedidas y hechas)" : "Sencillo"});
        l.add(new String[]{"Baraja", "Española de " + r.deck + " cartas"});
        if (players > 0) {
            List<Integer> seq = Engine.sequence(r, players);
            int max = 0, auc = 0;
            for (int c : seq) max = Math.max(max, c);
            if (r.auctionMax) for (int c : seq) if (c == max) auc++;
            l.add(new String[]{"Rondas", seq.size() + " · máximo " + Ui.plural(max, "carta", "cartas")});
            l.add(new String[]{"Rondas subastadas", auc == 0 ? "Ninguna" : String.valueOf(auc)});
        } else {
            l.add(new String[]{"Máximo de cartas", String.valueOf(r.maxCards)});
            l.add(new String[]{"Rondas de máximo subastadas", r.auctionMax ? "Sí" : "No"});
        }
        l.add(new String[]{"Secuencia", "custom".equals(r.seqType) ? r.customSeq : sequenceText(r, players)});
        l.add(new String[]{"Puntos base por acierto", String.valueOf(r.base)});
        l.add(new String[]{"Puntos por baza", String.valueOf(r.perTrick)});
        l.add(new String[]{"Penalización por diferencia", String.valueOf(r.penalty)});
        for (int i = 0; i < 4; i++) {
            String v = r.multOn[i]
                    ? "×" + r.multF[i] + (r.multBase ? " (también la base)" : " (solo bazas y penalizaciones)")
                    : "Desactivado";
            l.add(new String[]{Model.SUIT_NAMES[i], v});
        }
        l.add(new String[]{"Prohibir que las pedidas sumen las bazas", r.forbidSum ? "Sí" : "No"});
        l.add(new String[]{"Empates", tieText(r)});
        return l;
    }
}
