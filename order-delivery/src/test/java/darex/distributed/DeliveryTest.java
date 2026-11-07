package darex.distributed;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryFixture {
    JdbcTemplate orders;
    JdbcTemplate shipping;
    Exercise receiver;
    final List<Long> created = new ArrayList<>();

    @BeforeEach void connect() {
        var orderSource = source("ORDER");
        var shippingSource = source("SHIPPING");
        orders = new JdbcTemplate(orderSource);
        shipping = new JdbcTemplate(shippingSource);
        assertNotEquals(orders.queryForObject("SELECT DATABASE()", String.class),
                shipping.queryForObject("SELECT DATABASE()", String.class));
        new ResourceDatabasePopulator(new ClassPathResource("order.sql")).execute(orderSource);
        new ResourceDatabasePopulator(new ClassPathResource("shipping.sql")).execute(shippingSource);
        receiver = new Exercise(shipping, new TransactionTemplate(new DataSourceTransactionManager(shippingSource)), ignored -> {});
    }

    static DriverManagerDataSource source(String role) {
        String prefix = "DISTRIBUTED_" + role + "_TEST_DB_";
        return new DriverManagerDataSource(System.getenv(prefix + "URL"), System.getenv(prefix + "USERNAME"),
                System.getenv(prefix + "PASSWORD"));
    }

    // 각 테스트가 만든 행에만 초기화를 적용한다.
    @AfterEach void cleanup() {
        for (long id : created) {
            shipping.update("DELETE FROM shipments WHERE order_id=?", id);
            shipping.update("DELETE FROM processed_events WHERE order_id=?", id);
            orders.update("DELETE FROM outbox_events WHERE order_id=?", id);
            orders.update("DELETE FROM orders WHERE id=?", id);
        }
    }

    Exercise publisher(Delivery transport) {
        var source = orders.getDataSource();
        return new Exercise(orders, new TransactionTemplate(new DataSourceTransactionManager(source)), transport);
    }

    Delivery.Event event(long id) {
        return orders.queryForObject("SELECT event_id,order_id FROM outbox_events WHERE order_id=?",
                (row, index) -> new Delivery.Event(row.getString(1), row.getLong(2)), id);
    }

    boolean published(long id) {
        return orders.queryForObject("SELECT published FROM outbox_events WHERE order_id=?", Boolean.class, id);
    }

    void check(long id, int count) {
        assertEquals(count, shipping.queryForObject("SELECT COUNT(*) FROM shipments WHERE order_id=?", Integer.class, id));
        assertEquals(count, shipping.queryForObject("SELECT COUNT(*) FROM processed_events WHERE order_id=?", Integer.class, id));
    }

}

class DeliveryTest extends DeliveryFixture {
    @Test void normalDelivery() {
        var publisher = publisher(event -> receiver.receive(event, () -> {}));
        long id = publisher.order(created::add);
        publisher.publishPending();
        assertTrue(published(id));
        check(id, 1);
    }

    @Test void duplicateDelivery() {
        long id = publisher(ignored -> {}).order(created::add);
        var event = event(id);
        for (int i = 0; i < 3; i++) receiver.receive(event, () -> {});
        check(id, 1);
    }

    @Test void shipmentAndReceiptRollBackTogether() {
        long id = publisher(ignored -> {}).order(created::add);
        var event = event(id);
        assertThrows(IllegalStateException.class, () -> receiver.receive(event, () -> { throw new IllegalStateException(); }));
        check(id, 0);
        receiver.receive(event, () -> {});
        check(id, 1);
    }

    @Test void lostAcknowledgementRetriesSameEvent() {
        var attempts = new ArrayList<Delivery.Event>();
        var publisher = publisher(event -> {
            attempts.add(event);
            receiver.receive(event, () -> {});
            if (attempts.size() == 1) throw new Delivery.Unavailable();
        });
        long id = publisher.order(created::add);
        publisher.publishPending();
        check(id, 1);
        assertFalse(published(id));
        publisher.publishPending();
        assertEquals(List.of(event(id), event(id)), attempts);
        assertTrue(published(id));
        check(id, 1);
    }

    @Test void unavailableShippingAndPublisherRestart() {
        var offline = publisher(ignored -> { throw new Delivery.Unavailable(); });
        for (int i = 0; i < 3; i++) offline.order(created::add);
        offline.publishPending();
        for (long id : created) {
            assertFalse(published(id));
            check(id, 0);
        }
        publisher(event -> receiver.receive(event, () -> {})).publishPending();
        for (long id : created) {
            assertTrue(published(id));
            check(id, 1);
        }
    }

    @Test void orderAndOutboxRollBackTogether() {
        assertThrows(IllegalStateException.class, () -> publisher(ignored -> {}).order(id -> {
            created.add(id);
            throw new IllegalStateException();
        }));
        long id = created.getFirst();
        assertEquals(0, orders.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?", Integer.class, id));
        assertEquals(0, orders.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE order_id=?", Integer.class, id));
    }

    @Test void restartBeforeFirstPublish() {
        long id = publisher(ignored -> {}).order(created::add);
        assertFalse(published(id));
        publisher(event -> receiver.receive(event, () -> {})).publishPending();
        assertTrue(published(id));
        check(id, 1);
    }
}
