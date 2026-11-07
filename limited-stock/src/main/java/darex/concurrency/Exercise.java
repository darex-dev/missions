package darex.concurrency;

import java.util.function.LongConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class Exercise {
    public enum Result { ORDERED, SOLD_OUT, NOT_FOUND }
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final LongConsumer afterRead;

    public Exercise(JdbcTemplate db, TransactionTemplate transaction, LongConsumer afterRead) {
        this.db = db;
        this.transaction = transaction;
        this.afterRead = afterRead;
    }

    // 실습 대상: 읽은 재고를 사용해 덮어쓰므로 동시 요청에서 갱신이 유실된다.
    public Result order(long productId) {
        return transaction.execute(status -> {
            var stock = db.queryForList("SELECT stock FROM products WHERE id=?", Integer.class, productId);
            if (stock.isEmpty()) return Result.NOT_FOUND;
            if (stock.getFirst() == 0) return Result.SOLD_OUT;
            afterRead.accept(productId);
            db.update("UPDATE products SET stock=? WHERE id=?", stock.getFirst() - 1, productId);
            db.update("INSERT INTO orders(product_id) VALUES (?)", productId);
            return Result.ORDERED;
        });
    }
}
