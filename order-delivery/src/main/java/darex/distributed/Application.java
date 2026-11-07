package darex.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.JmsException;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }

    @Bean
    DefaultJmsListenerContainerFactory jmsListenerContainerFactory(jakarta.jms.ConnectionFactory connection) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connection);
        factory.setSessionTransacted(true);
        return factory;
    }

    @Bean
    Exercise exercise(JdbcTemplate db, TransactionTemplate transaction, ObjectMapper json, JmsTemplate broker) {
        broker.setExplicitQosEnabled(true);
        broker.setDeliveryPersistent(true);
        return new Exercise(db, transaction, event -> {
            try {
                broker.convertAndSend(Broker.QUEUE, json.writeValueAsString(event));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException(exception);
            } catch (JmsException exception) {
                throw new Delivery.Unavailable();
            }
        });
    }

    @RestController
    @ConditionalOnProperty(name = "mission.role", havingValue = "order")
    static class Orders {
        private final Exercise exercise;
        Orders(Exercise exercise) { this.exercise = exercise; }

        @PostMapping("/orders")
        @ResponseStatus(HttpStatus.ACCEPTED)
        Map<String, Long> order() { return Map.of("orderId", exercise.order(ignored -> {})); }

        // 서비스 재시작 뒤에도 DB에 남은 작업을 조회한다.
        @Scheduled(fixedDelay = 1000)
        void publish() { exercise.publishPending(); }
    }

    @org.springframework.stereotype.Component
    @ConditionalOnProperty(name = "mission.role", havingValue = "shipping")
    static class Shipping {
        private final Exercise exercise;
        private final ObjectMapper json;
        Shipping(Exercise exercise, ObjectMapper json) { this.exercise = exercise; this.json = json; }

        // 배송 트랜잭션이 성공한 뒤에만 JMS 세션이 수신을 확정한다.
        @JmsListener(destination = Broker.QUEUE)
        void receive(String body) throws com.fasterxml.jackson.core.JsonProcessingException {
            exercise.receive(json.readValue(body, Delivery.Event.class), () -> {});
        }
    }
}
