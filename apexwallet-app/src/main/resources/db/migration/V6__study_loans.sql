-- Study loans. The receivable is debit-normal, like bank cash.
-- Repayment reduces that asset and the customer balance together.

CREATE TABLE loans (
    id UUID PRIMARY KEY,
    borrower_id UUID NOT NULL,
    borrower_username VARCHAR(64) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts (id),
    principal_minor BIGINT NOT NULL CHECK (principal_minor > 0),
    outstanding_minor BIGINT NOT NULL CHECK (outstanding_minor >= 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    checker_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX loans_borrower_idx ON loans (borrower_id, created_at);

CREATE UNIQUE INDEX loans_one_open_idx ON loans (borrower_id) WHERE status IN ('PENDING', 'ACTIVE');

CREATE OR REPLACE VIEW account_reconciliation AS
SELECT
    a.id,
    a.cached_balance_minor,
    COALESCE(SUM(
        CASE
            WHEN a.account_type IN ('BANK_CASH', 'LOAN_RECEIVABLE') AND e.direction = 'DEBIT' THEN e.amount_minor
            WHEN a.account_type IN ('BANK_CASH', 'LOAN_RECEIVABLE') AND e.direction = 'CREDIT' THEN -e.amount_minor
            WHEN e.direction = 'CREDIT' THEN e.amount_minor
            ELSE -e.amount_minor
        END
    ), 0) AS folded_balance_minor
FROM accounts a
LEFT JOIN ledger_entries e ON e.account_id = a.id
GROUP BY a.id, a.cached_balance_minor;
