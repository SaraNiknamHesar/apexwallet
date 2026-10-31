-- Durable ledger shape. The local demo profile keeps the same rules in memory.
-- Money is BIGINT minor units. ledger_entries is insert-only.

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_ref VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    overdraft_limit_minor BIGINT NOT NULL DEFAULT 0 CHECK (overdraft_limit_minor >= 0),
    cached_balance_minor BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128),
    reverses_transaction_id UUID REFERENCES transactions (id),
    requested_at TIMESTAMPTZ NOT NULL,
    posted_at TIMESTAMPTZ,
    description VARCHAR(280) NOT NULL
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions (id),
    account_id UUID NOT NULL REFERENCES accounts (id),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    booked_at TIMESTAMPTZ NOT NULL,
    seq INT NOT NULL CHECK (seq >= 0),
    UNIQUE (transaction_id, seq)
);

CREATE INDEX ledger_entries_account_booked_idx
    ON ledger_entries (account_id, booked_at, seq);

CREATE OR REPLACE FUNCTION forbid_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END;
$$;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION forbid_ledger_mutation();

-- BANK_CASH is debit-normal. Customer and fee accounts are credit-normal.
CREATE VIEW account_reconciliation AS
SELECT
    a.id,
    a.cached_balance_minor,
    COALESCE(SUM(
        CASE
            WHEN a.account_type = 'BANK_CASH' AND e.direction = 'DEBIT' THEN e.amount_minor
            WHEN a.account_type = 'BANK_CASH' AND e.direction = 'CREDIT' THEN -e.amount_minor
            WHEN e.direction = 'CREDIT' THEN e.amount_minor
            ELSE -e.amount_minor
        END
    ), 0) AS folded_balance_minor
FROM accounts a
LEFT JOIN ledger_entries e ON e.account_id = a.id
GROUP BY a.id, a.cached_balance_minor;
