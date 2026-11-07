package darex.distributed;

import java.util.UUID;

@FunctionalInterface
public interface Delivery {
    void send(Event event) throws Unavailable;

    record Event(String eventId, long orderId) {
        public Event {
            if (eventId == null || !UUID.fromString(eventId).toString().equals(eventId) || orderId <= 0) {
                throw new IllegalArgumentException("잘못된 주문 이벤트입니다.");
            }
        }
    }

    final class Unavailable extends RuntimeException {
        public Unavailable() { super("배송 서비스 확인 응답을 받지 못했습니다."); }
    }
}
