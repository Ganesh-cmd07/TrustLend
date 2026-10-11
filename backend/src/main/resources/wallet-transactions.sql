CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    transaction_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_wallet_transactions_wallet_time (wallet_id, transaction_time, id),
    CONSTRAINT fk_wallet_transactions_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);
