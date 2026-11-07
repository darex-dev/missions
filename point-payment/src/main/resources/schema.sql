CREATE TABLE IF NOT EXISTS wallets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    balance BIGINT NOT NULL CHECK (balance >= 0)
);
CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    price BIGINT NOT NULL CHECK (price > 0)
);
CREATE TABLE IF NOT EXISTS attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    outcome VARCHAR(20) NOT NULL
);
CREATE TABLE IF NOT EXISTS purchases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL UNIQUE,
    wallet_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    FOREIGN KEY (attempt_id) REFERENCES attempts(id),
    FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE IF NOT EXISTS point_usages (
    purchase_id BIGINT PRIMARY KEY,
    amount BIGINT NOT NULL CHECK (amount > 0),
    FOREIGN KEY (purchase_id) REFERENCES purchases(id)
);
