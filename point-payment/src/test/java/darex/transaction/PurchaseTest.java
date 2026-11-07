package darex.transaction;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static darex.transaction.PurchaseStore.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseTest {
    static JdbcTemplate db;
    static Exercise exercise;

    @BeforeAll static void connect() {
        var source = new DriverManagerDataSource(System.getenv("TRANSACTION_TEST_DB_URL"),
                System.getenv("MISSION_DB_USERNAME"), System.getenv("MISSION_DB_PASSWORD"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        db = new JdbcTemplate(source);
        exercise = new Exercise(new PurchaseStore(db), new TransactionTemplate(new DataSourceTransactionManager(source)));
    }

    static long fixture(String sql, long value) {
        var key = new GeneratedKeyHolder();
        JdbcClient.create(db).sql(sql).param("value", value).update(key);
        return key.getKey().longValue();
    }

    static long wallet(long balance) { return fixture("INSERT INTO wallets(balance) VALUES (:value)", balance); }
    static long product(long price) { return fixture("INSERT INTO products(price) VALUES (:value)", price); }

    @Test void normal() { success(10000, 3000); }
    @Test void exactBalance() { success(3000, 3000); }

    static void success(long balance, long price) {
        long wallet = wallet(balance);
        assertEquals(PURCHASED, exercise.purchase(wallet, product(price), stage -> {}));
        check(wallet, balance - price, 1, price, 0);
    }

    @Test void insufficient() {
        long wallet = wallet(2000);
        assertEquals(INSUFFICIENT_POINTS, exercise.purchase(wallet, product(3000), stage -> {}));
        check(wallet, 2000, 0, 0, 1);
    }

    @Test void debitRuntimeFailure() { failure(PurchaseStore.Stage.AFTER_DEBIT, new IllegalStateException()); }
    @Test void purchaseCheckedFailure() { failure(PurchaseStore.Stage.AFTER_PURCHASE, new Exception()); }
    @Test void usageRuntimeFailure() { failure(PurchaseStore.Stage.AFTER_USAGE, new IllegalStateException()); }

    static void failure(PurchaseStore.Stage at, Exception problem) {
        long wallet = wallet(10000);
        assertEquals(FAILED, exercise.purchase(wallet, product(3000), stage -> {
            if (stage == at) throw problem;
        }));
        check(wallet, 10000, 0, 0, 1);
    }

    static void check(long wallet, long balance, int purchases, long amount, int failures) {
        assertEquals(balance, db.queryForObject("SELECT balance FROM wallets WHERE id=?", Long.class, wallet));
        assertEquals(purchases, db.queryForObject("SELECT COUNT(*) FROM purchases WHERE wallet_id=?", Integer.class, wallet));
        assertEquals(purchases, db.queryForObject("SELECT COUNT(*) FROM point_usages u JOIN purchases p ON p.id=u.purchase_id WHERE p.wallet_id=?", Integer.class, wallet));
        assertEquals(amount, db.queryForObject("SELECT COALESCE(SUM(u.amount),0) FROM point_usages u JOIN purchases p ON p.id=u.purchase_id WHERE p.wallet_id=?", Long.class, wallet));
        assertEquals(failures, db.queryForObject("SELECT COUNT(*) FROM attempts WHERE wallet_id=? AND outcome='FAILED'", Integer.class, wallet));
        assertEquals(purchases, db.queryForObject("SELECT COUNT(*) FROM attempts WHERE wallet_id=? AND outcome='SUCCESS'", Integer.class, wallet));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM attempts WHERE wallet_id=? AND outcome='PENDING'", Integer.class, wallet));
    }
}
