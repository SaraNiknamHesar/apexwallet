-- The race between two identical submits is closed by the database, not by an if-statement.
CREATE UNIQUE INDEX transactions_idempotency_key_uidx
    ON transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
