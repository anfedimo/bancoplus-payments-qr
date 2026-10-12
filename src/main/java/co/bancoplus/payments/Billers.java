package co.bancoplus.payments;

import java.util.Map;

/** Catálogo de convenios de recaudo (entidades ficticias de servicios públicos). Valores ASCII: viajan en cabeceras HTTP. */
final class Billers {

    record Biller(String code, String name) {}

    private static final Map<String, Biller> CATALOG = Map.of(
            "ELECTROPLUS", new Biller("ELECTROPLUS", "ElectroPlus S.A. E.S.P. (energia)"),
            "AGUASPLUS", new Biller("AGUASPLUS", "Aguas del Valle Plus S.A. E.S.P. (acueducto)"),
            "GASPLUS", new Biller("GASPLUS", "GasPlus S.A. E.S.P. (gas natural)"));

    private Billers() {}

    static Biller find(String code) {
        return code == null ? null : CATALOG.get(code);
    }
}
