CREATE TABLE IF NOT EXISTS lender_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_lender_requests_status_created (status, created_at, id),
    INDEX idx_lender_requests_user_created (user_id, created_at, id),
    CONSTRAINT fk_lender_requests_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);
