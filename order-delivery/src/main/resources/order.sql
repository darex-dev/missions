CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT
);
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id CHAR(36) PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX (published, order_id)
);
