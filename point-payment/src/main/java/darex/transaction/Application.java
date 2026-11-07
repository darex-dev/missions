package darex.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }

    @Bean
    Exercise purchases(JdbcTemplate db, TransactionTemplate transaction) {
        return new Exercise(new PurchaseStore(db), transaction);
    }

    @RestController
    static class Purchases {
        private final Exercise exercise;
        Purchases(Exercise exercise) { this.exercise = exercise; }

        // 가격은 요청에서 받지 않고 상품 데이터에서 읽는다.
        @PostMapping("/wallets/{walletId}/purchases/{productId}")
        ResponseEntity<PurchaseStore.Result> purchase(@PathVariable long walletId, @PathVariable long productId) {
            var result = exercise.purchase(walletId, productId, stage -> {});
            return ResponseEntity.status(switch (result) {
                case PURCHASED -> 201;
                case INSUFFICIENT_POINTS -> 409;
                case NOT_FOUND -> 404;
                case FAILED -> 500;
            }).body(result);
        }
    }
}
