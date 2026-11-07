package darex.transaction;

import org.springframework.transaction.support.TransactionTemplate;

public class Exercise {
    private final PurchaseStore store;
    public Exercise(PurchaseStore store, TransactionTemplate transaction) { this.store = store; }

    // 실습 대상: 각 변경이 따로 커밋되어 처리 실패 뒤 일부 데이터가 남는다.
    public PurchaseStore.Result purchase(long wallet, long product, PurchaseStore.Checkpoint checkpoint) {
        long attempt = store.start(wallet, product);
        PurchaseStore.Result result;
        try {
            result = store.write(attempt, wallet, product, checkpoint);
        } catch (Exception exception) {
            result = PurchaseStore.Result.FAILED;
        }
        store.finish(attempt, result);
        return result;
    }
}
