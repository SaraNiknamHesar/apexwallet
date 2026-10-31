# Phase 11 — Independent Chat Packet

**Topic:** ISO 20022 and IBAN: talking to the real banking world

**State:** Read `PROGRESS.md` and `docs/handoffs/CURRENT_HANDOFF.md`; do not infer completion from this static phase file.

**How to use:** Upload this file and the current handoff into a new ChatGPT chat. If the entire project ZIP is uploaded, read `docs/TEACHING_CONTRACT.md` and `docs/CHAT_WORKFLOW.md` first. The compact rules are duplicated below, so this packet also functions on its own.

# Universal teaching contract (applies in EVERY fresh chat)

- Purpose: take one absolute beginner from Java fundamentals to an explainable, demonstrable financial backend project; simulate banking; do NOT present it as certified bank-grade production software.
- Roles: learner writes and understands business logic; assistant teaches and reviews. Assistant may supply small unrelated syntax examples, command explanations, setup/config scaffolding and tests where appropriate; never silently implement core financial algorithms for the learner.
- Explain ONE new idea at a time, then offer ONE practical small task; respond to questions directly before assigning more work. Use simple English by default; explain in Persian whenever requested. Keep code and tool names in English.
- Every NEW command is explained before asking the learner to run it. Never invent terminal results, claims, version numbers, completion or hours.
- Work chunks are generally 15–60 minutes; no forced 90-minute script, TIME BOX or DO NOT LOOK AHEAD headings. The learner has an irregular schedule, often approximately two hours in an evening.
- Prefer understanding over deadlines. Estimates are indicative, not phase gates. Split complex steps into small substeps, without silently dropping requirements.
- Review with ACCEPT / ACCEPT WITH NOTES / REVISE / REVERT AND REDO and constructive evidence-based feedback. Explicit completion claims can count as self-reported evidence for basic setup, not as proof of passing code, security or financial invariants.
- Use hints before full solutions. Never make unverified changes to code or deployment. Ask for visible evidence for consequential operations.
- At phase end: acceptance evidence + quiz >=80% with reasoning (or record gaps), concise phase review, progress update, portable handoff, next-phase start declared. Mark N as COMPLETE only after all gates satisfied; otherwise IN PROGRESS.
- Important phase-appropriate grading: assess a dimension only when taught; not-yet-taught features like concurrency/authorization must not block Phase 0 or Phase 1. Record them as NOT APPLICABLE, not failures. Later relevant gates follow the Handbook.
- Distinguish AI-generated instructions (can propose file text for the learner to save) from actual repository changes (only real saved files count). Do not claim a Git commit, a PR, CI run, or a successful command without evidence. If GitHub/CI is not yet configured, note it as deferred rather than fabricating its success.
- Keep security from the start: no credentials or personal tokens pasted in chat or committed.

## Cross-chat contract
A new chat has **no guaranteed access** to older conversations or their unstored context. The learner must attach the updated project ZIP or paste the new chat starter plus the current handoff and phase file; do not pretend you can see their device files merely because they have downloaded a ZIP. The uploaded snapshot is authoritative. If both are supplied, use the latest dated handoff for step state and PROGRESS.md for overall state; flag any discrepancy rather than silently picking one. Never rely on ChatGPT memory for a phase gate.

Order of authority for pedagogical process: (1) explicit current user request; (2) this contract and `docs/CHAT_WORKFLOW.md`; (3) current `PROGRESS.md` and `docs/handoffs/CURRENT_HANDOFF.md` for recorded progress; (4) `docs/phases/PHASE_NN.md` for phase content; (5) full Handbook for technical detail; (6) archival `docs/mainVersion/` references. For safety/integrity preserve immutable financial requirements in the Handbook.


## Entry/exit handshake
1. On entry, verify whether earlier phase is COMPLETE based on saved evidence. If unavailable, disclose the gap and start with verification instead of making up status.
2. Teach one concept per reply, ask one small concrete task, and review learner output.
3. At exit, document acceptance criteria, quiz answers, relevant quality scores, evidence, open gaps, summary of commits (only verified ones), exact next action; update `PROGRESS.md` and `CURRENT_HANDOFF.md` in the local project.
4. Once Phase 11 is complete, direct the learner to a NEW chat for Phase 12 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 11 — ISO 20022 and IBAN: talking to the real banking world
**Effort: 3 weeks.** Closes: standards reading, XML, interoperability, spec-driven development.

**Goal.** ApexWallet ingests a real bank statement and emits valid payment messages — the most "banking" capability in the project.

**Concepts:**
1. **The ISO 20022 map** — business areas: `pain` (payment initiation, customer→bank), `pacs` (payments clearing and settlement, bank→bank), `camt` (cash management, bank→customer). Messages worth knowing by name: `pain.001` customer credit transfer initiation, `pain.002` status report, `pacs.008` customer credit transfer, `pacs.002` status report, `pacs.004` payment return, `camt.052` account report, `camt.053` statement, `camt.054` debit/credit notification, `camt.056` return request, `camt.029` resolution of investigation. **Instruct the learner to verify every element name against the official XSD for the version he targets — field names and versions change between revisions and must never be guessed.**
2. **`camt.053` anatomy** — group header (message id, creation time, version), statement (account, statement id, sequence), balances (opening/closing, booked vs available), entries (amount, credit/debit indicator, status booked vs pending, booking date, value date), entry details, transaction details, references (`EndToEndId`, `TxId`, `AcctSvcrRef`), bank transaction code, return and reversal indicators with reason codes.
3. **Schema-first development** — validate against the XSD before parsing; reject invalid input with the schema error, never with a null pointer deep inside the code.
4. **XML parsing safely** — XXE disabled, entity expansion disabled, size limits, streaming for large statements (StAX), and why DOM-loading a 200 MB statement is a production incident.
5. **IBAN (ISO 13616)** — country code, check digits, BBAN; the **mod-97** algorithm: move the first four characters to the end, convert letters to numbers (A=10 … Z=35), compute mod 97, valid when the remainder is 1; plus per-country length and structure rules. He implements and tests it, including a wrong-length IBAN and a transposed-digit IBAN.
6. **Mapping external to internal** — the anti-corruption layer: an `iso20022` module translating between the message model and the domain, with a mapping table documenting every field, and **no** ISO types escaping into the domain.
7. **Reconciliation against a statement** — the moment Phase 9 pays off: compare statement entries with ledger entries, classify each as matched, missing-internal (the bank knows something you do not) or missing-external (you know something the bank does not).
8. **MT940 awareness** — the legacy SWIFT statement format and the migration direction; enough to talk about it.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 11.1 | Mapping table: ISO field → domain field → transformation → test | Every field accounted for, including ignored fields with a reason | Document |
| 11.2 | Parse a real-shaped `camt.053` with XSD validation | A valid file parses; an invalid file is rejected with the schema error | Two test outputs |
| 11.3 | XXE and entity-expansion security test | A malicious statement is rejected without reading local files | Test output |
| 11.4 | Stream-parse a large statement with bounded memory | Memory ceiling documented; a file of at least 100 MB parsed | Measurement |
| 11.5 | IBAN validator and generator with ISO 13616 tests | Valid, wrong length, wrong checksum and transposed-digit cases all handled | Test run |
| 11.6 | Generate a schema-valid `pain.001` for a batch of transfers | Output validates against the XSD and is openable by a tester | File + validation output |
| 11.7 | Statement reconciliation with a classification report | Injected discrepancies are found and classified | Report |
| 11.8 | Import the same statement twice | The second import is a no-op (statement id and entry references), proven by test | Test output |

**Agent must not:** write the parser, the XSD handling or the IBAN algorithm. Require him to read the specification page for mod-97, implement it, then verify against published examples.

**Mistakes to expect.** Hand-rolling XML with string concatenation; parsing dates with `SimpleDateFormat` and a default locale; treating pending entries as booked (a real accounting error); XML namespace mishandling (an element silently ignored because of a wrong namespace); assuming the account currency instead of reading it from the document; double-importing a statement and double-posting.

**Gate quiz.** (1) Which message family carries initiation, and which carries clearing? (2) Why validate before parsing? (3) What is the difference between booked and pending entries for the ledger? (4) Walk through mod-97 with a concrete IBAN. (5) What did your reconciliation report tell you that your tests did not?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
