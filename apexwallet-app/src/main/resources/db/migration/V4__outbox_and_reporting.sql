-- Reporting view and the outbox row that commits with the ledger post.

CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;

CREATE VIEW monthly_volume AS
SELECT date_trunc('month', booked_at) AS month,
       currency,
       count(*) AS entries,
       sum(amount_minor) AS volume_minor
FROM ledger_entries
GROUP BY 1, 2;
