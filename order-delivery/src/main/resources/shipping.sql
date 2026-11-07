CREATE TABLE IF NOT EXISTS shipments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    INDEX (order_id)
);
CREATE TABLE IF NOT EXISTS processed_events (
    event_id CHAR(36) PRIMARY KEY,
    order_id BIGINT NOT NULL
);
