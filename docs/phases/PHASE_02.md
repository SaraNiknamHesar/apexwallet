# Phase 02 — Independent Chat Packet

**Topic:** The in-memory double-entry ledger

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
4. Once Phase 02 is complete, direct the learner to a NEW chat for Phase 03 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 2 — The in-memory double-entry ledger
**Effort: 3 weeks.** Closes: collections, generics, streams, exceptions, I/O.

**Goal.** A working in-memory ledger: postings, entries, derived balances, statements — with invariants enforced by tests.

**Concepts:**
1. **Double entry, precisely.** Every transaction produces entries whose signed sum is zero. Debit and credit are *directions*, not signs: he decides how direction is represented (enum plus positive amount, or signed amount) and defends it.
2. **Append-only thinking.** No `update`; a mistake is corrected by a reversing transaction referencing the original. Analogy: a teller crosses nothing out — he writes a correcting line.
3. **Balance as a fold, not a field.** `balance(account) = entries.filter(account).sum(signed)`. A cached balance is a *performance* artefact that must always be rebuildable — the seed of Phase 9's reconciliation.
4. **Data structures for the journal.** Why an append to an `ArrayList` is fine, why a `HashMap<AccountId, List<Entry>>` index helps reads, and what that index costs in memory and consistency.
5. **Streams used honestly** — including when a loop is clearer, and why `reduce` over `Money` needs an associative, overflow-checked operator.
6. **Transaction state machine** — legal transitions (`PENDING → POSTED → REVERSED`), illegal transitions rejected with a domain exception. A state machine is a *test* of the domain model, not a diagram.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 2.1 | Implement `LedgerEntry`, `Transaction`, `Ledger.post(Transaction)` | I1 asserted after every post; an unbalanced transaction throws before any state change | Tests |
| 2.2 | Implement `balanceOf(accountId)` as a fold and `rebuildBalances()` | Recomputation equals the cached value for any sequence | Test |
| 2.3 | Implement statement generation with a defined order and running balance | Deterministic, human-readable; ordering explicit (time, then sequence) | Sample output in the PR |
| 2.4 | Implement reversal referencing the original | Zero net effect on balances; **both** entries remain visible | Test |
| 2.5 | Property-based test over random transfer sequences | I1 and I2 hold; negative balances refused unless overdraft is explicit | jqwik report |
| 2.6 | Exception hierarchy with stable error codes | Each carries a code and enough context to debug | Tests on codes |
| 2.7 | Export a day's statement with `java.nio.file` | UTF-8, deterministic, re-readable by a test | Test |

**Agent must not:** write `post()`, the fold or the reversal. Give the invariant list (I1–I5) and the acceptance criteria, then get out of the way.

**Mistakes to expect.** Storing a `balance` field as truth; forgetting the counterpart entry; `mapToDouble().sum()` inside a stream (a classic accident); mutating a list returned from a getter; comparing `Money` with `==`; `Optional` as a field type; ordering by timestamp alone (ties break determinism).

**Gate quiz.** (1) State the five ledger invariants from memory. (2) Why is a reversal better than deleting an entry? (3) Why can a cached balance never be the source of truth? (4) Where exactly could a cent be lost in your `allocate`? (5) Give a transfer sequence where a single-entry implementation silently diverges from double-entry.

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
