CREATE TABLE IF NOT EXISTS loans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    borrower_id BIGINT NOT NULL,
    lender_id BIGINT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) NOT NULL,
    term_months INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_loans_status_created (status, created_at, id),
    INDEX idx_loans_borrower_created (borrower_id, created_at, id),
    INDEX idx_loans_lender_created (lender_id, created_at, id),
    CONSTRAINT fk_loans_borrower FOREIGN KEY (borrower_id) REFERENCES users (id),
    CONSTRAINT fk_loans_lender FOREIGN KEY (lender_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS loan_repayments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    loan_id BIGINT NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    due_date DATE NOT NULL,
    paid_date DATE NULL,
    status VARCHAR(16) NOT NULL,
    installment_number INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_loan_repayments_loan_due (loan_id, due_date, id),
    CONSTRAINT fk_loan_repayments_loan FOREIGN KEY (loan_id) REFERENCES loans (id)
);
