package darex.concurrency;

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
    Exercise orders(JdbcTemplate db, TransactionTemplate transaction) {
        return new Exercise(db, transaction, ignored -> {});
    }

    @RestController
    static class Orders {
        private final Exercise exercise;

        Orders(Exercise exercise) { this.exercise = exercise; }

        // 주문 결과를 실습 계약의 HTTP 상태로 변환한다.
        @PostMapping("/products/{id}/orders")
        ResponseEntity<Exercise.Result> order(@PathVariable long id) {
            var result = exercise.order(id);
            return ResponseEntity.status(switch (result) {
                case ORDERED -> 201;
                case SOLD_OUT -> 409;
                case NOT_FOUND -> 404;
            }).body(result);
        }
    }
}
