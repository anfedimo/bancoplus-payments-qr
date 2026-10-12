package co.bancoplus.payments;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integración con las entidades de recaudo. Se despliega como servicio convenios-api con la misma imagen.
 * CONVENIOS_EN_FALLA simula la indisponibilidad de una entidad externa: responde HTTP 500 a sus recaudos.
 */
@RestController
public class ConveniosController {

    private final Set<String> unavailable;

    public ConveniosController(@Value("${CONVENIOS_EN_FALLA:}") String unavailable) {
        this.unavailable = Arrays.stream(unavailable.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @PostMapping("/billers/{code}/payments")
    public ResponseEntity<Map<String, Object>> collect(@PathVariable String code,
                                                       @RequestBody Map<String, Object> payment) {
        Billers.Biller biller = Billers.find(code);
        if (biller == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Convenio inexistente: " + code));
        }
        if (unavailable.contains(code)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Servicio de recaudo de " + biller.name() + " no disponible"));
        }
        return ResponseEntity.ok(Map.of("status", "RECAUDADO", "biller", biller.code(),
                "transactionId", String.valueOf(payment.get("transactionId"))));
    }
}
