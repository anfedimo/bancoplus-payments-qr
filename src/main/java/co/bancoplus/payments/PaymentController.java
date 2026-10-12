package co.bancoplus.payments;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Pago QR y pago de servicios públicos. Expone el resultado de negocio en las cabeceras X-Business-*
 * (contrato de plataforma) y reproduce los anti-patrones de producción: rechazo de riesgo como HTTP 500
 * y falla de sistema como HTTP 200.
 *
 * El nombre del cliente nunca sale del servicio: no se emite en cabeceras, trazas ni logs. El documento
 * viaja en X-Business-Customer y el Gateway lo seudonimiza (SHA-256 con sal) antes de cualquier destino.
 */
@RestController
public class PaymentController {

    private static final String OPERATION_QR = "pago_qr";
    private static final String OPERATION_BILLER = "pago_servicio_publico";

    private final RestClient fraudApi;
    private final RestClient conveniosApi;

    public PaymentController(@Value("${fraud-api.url}") String fraudApiUrl,
                             @Value("${convenios-api.url}") String conveniosApiUrl) {
        this.fraudApi = RestClient.builder().baseUrl(fraudApiUrl).build();
        this.conveniosApi = RestClient.builder().baseUrl(conveniosApiUrl).build();
    }

    public record PaymentRequest(long amount, String cardNumber, String channel,
                                 String billerCode, String customerDocument, String customerName) {}

    @PostMapping("/payments/qr")
    public ResponseEntity<Map<String, Object>> pay(@RequestBody PaymentRequest request,
                                                   @RequestParam(required = false) String account) {
        Context ctx = new Context(transactionId(), request);
        RiskScore risk = fraudApi.post().uri("/risk/score").body(request).retrieve().body(RiskScore.class);

        if (risk != null && risk.score() >= 0.85) {
            // Anti-patrón: decisión de negocio propagada como HTTP 500
            return respond(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "declined", "RIESGO_ALTO",
                    Map.of("error", "Operación declinada por score de riesgo " + risk.score()));
        }

        if (ctx.biller() != null) {
            return payBiller(ctx);
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.05) {
            return respond(ctx, HttpStatus.OK, "declined", "FONDOS_INSUFICIENTES",
                    Map.of("status", "DECLINED", "message", "Fondos insuficientes"));
        }
        if (roll < 0.08) {
            // Anti-patrón: falla de sistema devuelta como HTTP 200
            return respond(ctx, HttpStatus.OK, "failed", "ERROR_SISTEMA",
                    Map.of("status", "ERROR", "message", "Error del sistema"));
        }
        if (roll < 0.10) {
            throw new IllegalStateException(
                    "Timeout del core AS400 para cuenta " + account + " tarjeta " + request.cardNumber());
        }
        return respond(ctx, HttpStatus.CREATED, "approved", "OK", Map.of("status", "APPROVED"));
    }

    /** Recaudo en la entidad de servicios públicos a través del servicio de convenios. */
    private ResponseEntity<Map<String, Object>> payBiller(Context ctx) {
        try {
            conveniosApi.post()
                    .uri("/billers/{code}/payments", ctx.biller().code())
                    .body(Map.of("transactionId", ctx.transactionId(), "amount", ctx.request().amount()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            return respond(ctx, HttpStatus.BAD_GATEWAY, "failed", "CONVENIO_NO_DISPONIBLE",
                    Map.of("status", "ERROR", "message", "La entidad " + ctx.biller().name() + " no respondió"));
        }
        return respond(ctx, HttpStatus.CREATED, "approved", "OK", Map.of("status", "APPROVED"));
    }

    private ResponseEntity<Map<String, Object>> respond(Context ctx, HttpStatus status, String outcome,
                                                        String reason, Map<String, Object> body) {
        Map<String, Object> payload = new HashMap<>(body);
        payload.put("transactionId", ctx.transactionId());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .header("X-Business-Operation", ctx.biller() != null ? OPERATION_BILLER : OPERATION_QR)
                .header("X-Business-Outcome", outcome)
                .header("X-Business-Reason", reason)
                .header("X-Business-Transaction-Id", ctx.transactionId())
                .header("X-Business-Amount", Long.toString(ctx.request().amount()));
        if (ctx.biller() != null) {
            response.header("X-Business-Biller", ctx.biller().code())
                    .header("X-Business-Biller-Name", ctx.biller().name());
        }
        if (ctx.request().customerDocument() != null) {
            response.header("X-Business-Customer", ctx.request().customerDocument());
        }
        return response.body(payload);
    }

    /**
     * Código de transacción alfanumérico (QR-&lt;tiempo base36&gt;-&lt;sufijo&gt;): empieza por letra y no contiene
     * secuencias numéricas aisladas, así que la regla de PII del Gateway no lo confunde con una cuenta.
     */
    static String transactionId() {
        String time = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            suffix.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
        }
        return "QR-" + time + "-" + suffix;
    }

    private record Context(String transactionId, PaymentRequest request) {
        Billers.Biller biller() {
            return Billers.find(request.billerCode());
        }
    }
}
