-- Columns the HTTP stores need. Existing ledger inserts leave dead false.

ALTER TABLE idempotency_keys ADD COLUMN failure_reason TEXT;
ALTER TABLE idempotency_keys ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE outbox ADD COLUMN dead BOOLEAN NOT NULL DEFAULT FALSE;
