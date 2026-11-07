package darex.transaction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class PurchaseStore {
    public enum Stage { AFTER_DEBIT, AFTER_PURCHASE, AFTER_USAGE }
    public enum Result { PURCHASED, INSUFFICIENT_POINTS, NOT_FOUND, FAILED }
    @FunctionalInterface
    public interface Checkpoint { void check(Stage stage) throws Exception; }

    private final JdbcTemplate db;
    public PurchaseStore(JdbcTemplate db) { this.db = db; }

    public long start(long wallet, long product) {
        var key = new GeneratedKeyHolder();
        JdbcClient.create(db).sql("INSERT INTO attempts(wallet_id,product_id,outcome) VALUES (:wallet,:product,'PENDING')")
                .param("wallet", wallet).param("product", product).update(key);
        return key.getKey().longValue();
    }

    // 장애 지점은 채점 계약의 일부이므로 그대로 유지한다.
    public Result write(long attempt, long wallet, long product, Checkpoint checkpoint) throws Exception {
        var price = db.queryForList("SELECT price FROM products WHERE id=?", Long.class, product);
        var balance = db.queryForList("SELECT balance FROM wallets WHERE id=?", Long.class, wallet);
        if (price.isEmpty() || balance.isEmpty()) return Result.NOT_FOUND;
        long amount = price.getFirst();
        if (balance.getFirst() < amount) return Result.INSUFFICIENT_POINTS;
        db.update("UPDATE wallets SET balance=balance-? WHERE id=?", amount, wallet);
        checkpoint.check(Stage.AFTER_DEBIT);
        var key = new GeneratedKeyHolder();
        JdbcClient.create(db).sql("INSERT INTO purchases(attempt_id,wallet_id,product_id) VALUES (:attempt,:wallet,:product)")
                .param("attempt", attempt).param("wallet", wallet).param("product", product).update(key);
        checkpoint.check(Stage.AFTER_PURCHASE);
        db.update("INSERT INTO point_usages(purchase_id,amount) VALUES (?,?)", key.getKey().longValue(), amount);
        checkpoint.check(Stage.AFTER_USAGE);
        return Result.PURCHASED;
    }

    public void finish(long attempt, Result result) {
        db.update("UPDATE attempts SET outcome=? WHERE id=?", result == Result.PURCHASED ? "SUCCESS" : "FAILED", attempt);
    }
}
