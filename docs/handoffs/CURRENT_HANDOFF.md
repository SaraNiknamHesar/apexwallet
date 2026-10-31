# ApexWallet — Portable Handoff (latest saved snapshot)

- Recorded at: 2026-10-23
- Phase packets on disk: 00–17 still present and unchanged as the learning syllabus
- Reference implementation: through phase 17, coded by the assistant at the learner's request
- Learner mastery: NOT ASSESSED. Do not treat green tests as the learner having finished a phase
- Study entry point: `docs/study/PHASES_00_08_FA.md` then `docs/study/PHASES_09_17_FA.md`
- Run: from `ApexWallet`, `mvn test`, then `mvn -pl apexwallet-app spring-boot:run` (profile `local`)
- Verified: domain tests, idempotency (including 50 threads), field encryption, IBAN, security primitives, `WalletApiTest`, `LedgerSchemaIT`, and `PostgresLedgerIT` (8 tests, JDBC + JPA on Postgres 16)
- Not wired into the local HTTP demo: Postgres posting. Users and HTTP idempotency stay in memory. Kafka is compose-only. No Grafana deployment, no PIT score, no demo video
- Decisions: `docs/adr/0001` … `0008`
- Demo secrets: `DemoWorld` only. Never reuse them. No git commit was created in this step
- EXACT NEXT ACTION: Sit with the learner and explain phase 1–2 from the guide (money, double-entry, invariants) against the real classes. Do not start by pretending the repository has no code.
