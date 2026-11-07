package darex.concurrency;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    static JdbcTemplate db;
    static TransactionTemplate tx;

    @BeforeAll static void connect() {
        var source = new DriverManagerDataSource(System.getenv("CONCURRENCY_TEST_DB_URL"),
                System.getenv("MISSION_DB_USERNAME"), System.getenv("MISSION_DB_PASSWORD"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    static long product(int stock) {
        var key = new GeneratedKeyHolder();
        JdbcClient.create(db).sql("INSERT INTO products(stock) VALUES (:stock)").param("stock", stock).update(key);
        return key.getKey().longValue();
    }

    @Test void normalAndMissing() {
        long id = product(10);
        var service = new Exercise(db, tx, ignored -> {});
        assertEquals(Exercise.Result.ORDERED, service.order(id));
        check(id, 1, 9);
        assertEquals(Exercise.Result.NOT_FOUND, service.order(-1));
        long empty = product(0);
        assertEquals(Exercise.Result.SOLD_OUT, service.order(empty));
        check(empty, 0, 0);
    }

    @Test void concurrentScarcity() throws Exception { race(10, 30); }
    @Test void concurrentSurplus() throws Exception { race(30, 10); }

    // 시작 코드의 읽기 이후 지점을 맞춰 갱신 유실을 재현한다.
    static void race(int stock, int requests) throws Exception {
        long id = product(stock);
        var barrier = new CyclicBarrier(requests);
        LongConsumer checkpoint = ignored -> {
            try { barrier.await(1, TimeUnit.SECONDS); }
            catch (BrokenBarrierException | TimeoutException ignoredTimeout) {
                // DB 락으로 읽기가 직렬화되는 정답도 계속 진행한다.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        };
        var first = new Exercise(db, tx, checkpoint);
        var second = new Exercise(db, tx, checkpoint);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Exercise.Result>> calls = java.util.stream.IntStream.range(0, requests)
                    .mapToObj(i -> (Callable<Exercise.Result>) () -> (i % 2 == 0 ? first : second).order(id)).toList();
            var futures = executor.invokeAll(calls, 20, TimeUnit.SECONDS);
            var results = new java.util.ArrayList<Exercise.Result>();
            for (var future : futures) results.add(future.get());
            int successes = Math.min(stock, requests);
            assertEquals(successes, results.stream().filter(r -> r == Exercise.Result.ORDERED).count());
            assertEquals(requests - successes, results.stream().filter(r -> r == Exercise.Result.SOLD_OUT).count());
            check(id, successes, stock - successes);
        }
    }

    static void check(long id, int orders, int stock) {
        assertEquals(orders, db.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id=?", Integer.class, id));
        assertEquals(stock, db.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, id));
    }
}
