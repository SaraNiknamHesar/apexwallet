# Quality gates in this reference

Run from `ApexWallet`:

```text
mvn test
```

What that command actually checks:

- Domain unit and property tests, including fees, interest, the daily limit, reconciliation, and the order book
- ArchUnit: the domain package does not depend on Spring or JPA
- ISO parser, IBAN, a 100 MB streamed statement, and idempotent import
- AI fallback, masking, eval threshold, and budget
- Outbox retry, dead letter, per-key order, and correlation across a thread
- API story on the local profile
- Postgres schema, append-only ledger and audit, JDBC and JPA posting, when Docker is running

PIT mutation testing is not wired into this build, so there is no mutation score to quote. Twenty consecutive hosted CI runs have not been collected. The workflow file is `.github/workflows/ci.yml`.

Do not invent a Grafana screenshot or a load-test graph. The pool-exhaustion failure and the 500-transfer test are the evidence that exists.
