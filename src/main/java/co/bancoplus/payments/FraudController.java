package co.bancoplus.payments;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Scoring de riesgo. Se despliega como servicio fraud-api con la misma imagen. */
@RestController
public class FraudController {

    @PostMapping("/risk/score")
    public RiskScore score(@RequestBody PaymentController.PaymentRequest request) {
        double score = ThreadLocalRandom.current().nextDouble() < 0.10
                ? ThreadLocalRandom.current().nextDouble(0.86, 0.99)
                : ThreadLocalRandom.current().nextDouble(0.01, 0.40);
        return new RiskScore(Math.round(score * 100) / 100.0, score >= 0.85 ? "decline" : "approve");
    }
}
