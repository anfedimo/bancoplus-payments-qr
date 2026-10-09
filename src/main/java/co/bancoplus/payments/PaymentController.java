package co.bancoplus.payments;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Pago QR. Expone el resultado de negocio en las cabeceras X-Business-* (contrato de plataforma)
 * y reproduce los anti-patrones de producción: rechazo de riesgo como HTTP 500 y falla de sistema
 * como HTTP 200.
 */
@RestController
public class PaymentController {

    private static final String OPERATION = "pago_qr";

    private final RestClient fraudApi;

    public PaymentController(@Value("${fraud-api.url}") String fraudApiUrl) {
        this.fraudApi = RestClient.builder().baseUrl(fraudApiUrl).build();
    }

    public record PaymentRequest(long amount, String cardNumber, String channel) {}

    @PostMapping("/payments/qr")
    public ResponseEntity<Map<String, Object>> pay(@RequestBody PaymentRequest request,
                                                   @RequestParam(required = false) String account) {
        RiskScore risk = fraudApi.post().uri("/risk/score").body(request).retrieve().body(RiskScore.class);

        if (risk != null && risk.score() >= 0.85) {
            // Anti-patrón: decisión de negocio propagada como HTTP 500
            return respond(HttpStatus.INTERNAL_SERVER_ERROR, "declined", "RIESGO_ALTO",
                    Map.of("error", "Operación declinada por score de riesgo " + risk.score()));
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.05) {
            return respond(HttpStatus.OK, "declined", "FONDOS_INSUFICIENTES",
                    Map.of("status", "DECLINED", "message", "Fondos insuficientes"));
        }
        if (roll < 0.08) {
            // Anti-patrón: falla de sistema devuelta como HTTP 200
            return respond(HttpStatus.OK, "failed", "ERROR_SISTEMA",
                    Map.of("status", "ERROR", "message", "Error del sistema"));
        }
        if (roll < 0.10) {
            throw new IllegalStateException(
                    "Timeout del core AS400 para cuenta " + account + " tarjeta " + request.cardNumber());
        }
        return respond(HttpStatus.CREATED, "approved", "OK", Map.of("status", "APPROVED"));
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus status, String outcome, String reason,
                                                        Map<String, Object> body) {
        return ResponseEntity.status(status)
                .header("X-Business-Operation", OPERATION)
                .header("X-Business-Outcome", outcome)
                .header("X-Business-Reason", reason)
                .body(body);
    }
}
