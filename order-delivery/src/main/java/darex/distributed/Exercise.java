package darex.distributed;

import java.util.UUID;
import java.util.function.LongConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

public class Exercise {
    private final JdbcTemplate db;
    private final Delivery delivery;

    public Exercise(JdbcTemplate db, TransactionTemplate transaction, Delivery delivery) {
        this.db = db;
        this.delivery = delivery;
    }

    // 실습 대상: 주문과 이벤트가 각각 커밋된다.
    public long order(LongConsumer afterOrder) {
        var key = new GeneratedKeyHolder();
        JdbcClient.create(db).sql("INSERT INTO orders () VALUES ()").update(key);
        long id = key.getKey().longValue();
        afterOrder.accept(id);
        db.update("INSERT INTO outbox_events(event_id,order_id) VALUES (?,?)", UUID.randomUUID().toString(), id);
        return id;
    }

    // 실습 대상: 응답 유실·배송 장애에도 발행 완료로 기록한다.
    public void publishPending() {
        for (var event : db.query("SELECT event_id,order_id FROM outbox_events WHERE published=FALSE ORDER BY order_id LIMIT 32",
                (row, index) -> new Delivery.Event(row.getString(1), row.getLong(2)))) {
            try { delivery.send(event); }
            catch (Delivery.Unavailable ignored) { }
            db.update("UPDATE outbox_events SET published=TRUE WHERE event_id=?", event.eventId());
        }
    }

    // 실습 대상: 배송 생성과 중복 처리 확인이 하나의 작업이 아니다.
    public void receive(Delivery.Event event, Runnable afterShipment) {
        db.update("INSERT INTO shipments(order_id) VALUES (?)", event.orderId());
        afterShipment.run();
        db.update("""
                INSERT INTO processed_events(event_id,order_id) VALUES (?,?)
                ON DUPLICATE KEY UPDATE order_id=order_id
                """, event.eventId(), event.orderId());
    }
}
