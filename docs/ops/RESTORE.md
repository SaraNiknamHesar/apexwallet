# Restore drill

This is the procedure for a local Postgres started by Docker Compose. It is not a production backup design.

1. From `ApexWallet`, start the database: `docker compose up -d postgres`
2. Dump it: `docker compose exec postgres pg_dump -U apex apexwallet > apexwallet.sql`
3. Drop and recreate the database, then restore:
   `docker compose exec -T postgres psql -U apex -d postgres -c "DROP DATABASE IF EXISTS apexwallet"`
   `docker compose exec -T postgres psql -U apex -d postgres -c "CREATE DATABASE apexwallet"`
   `docker compose exec -T postgres psql -U apex -d apexwallet < apexwallet.sql`
4. Re-run reconciliation. The study check is `Reconciliation.compare` on cached balances versus the fold, and the SQL view `account_reconciliation` where `cached_balance_minor <> folded_balance_minor` must return no rows.

The automated proof that a mismatch is detected is `ReconciliationTest`. The automated proof that the ledger and audit tables reject updates is `LedgerSchemaIT`.
