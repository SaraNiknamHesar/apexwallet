# Phase 13 — Independent Chat Packet

**Topic:** Quality gates: tests as a system

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
4. Once Phase 13 is complete, direct the learner to a NEW chat for Phase 14 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 13 — Quality gates: tests as a system
**Effort: 2–3 weeks.** Closes: mutation testing, architecture tests, CI as a gate.

**Goal.** The build refuses to accept regressions, and the architecture is enforced by machines.

**Concepts:**
1. **Test pyramid, honestly** — many fast unit tests on money and rules; a deliberately small number of Spring context tests; integration tests only against real infrastructure.
2. **Test doubles discipline** — mock at architectural boundaries only; mocking a repository usually means the test is testing the wrong thing.
3. **Property-based testing beyond the ledger** — invariants for fees (never above cap), limits (never exceeded), reversals (net zero), idempotency (replay never changes state).
4. **Mutation testing with PIT** on the money packages — a surviving mutant is an *unwritten requirement*. Set a threshold; a drop fails the build.
5. **Architecture tests with ArchUnit** — the domain imports neither Spring nor JPA; controllers never touch repositories; the ISO module does not leak into the domain; package naming rules.
6. **CI as the honest referee** — build, unit tests, integration tests with Testcontainers, coverage, mutation report, dependency scan, artefacts. A red pipeline means the phase is not done.
7. **Flaky tests are defects** — quarantine, diagnose, fix; never `@Disabled` and forget.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 13.1 | Classify every existing test; delete the useless ones | A written breakdown: counts per category and module | Report |
| 13.2 | Property tests for fees, limits and reversals | Random inputs never violate the invariants | Test run |
| 13.3 | Run PIT on the money package and beat the agreed threshold | Surviving mutants listed; each fixed with a test or justified in writing | Mutation report |
| 13.4 | ArchUnit rules, one made to fail on purpose | The violation breaks the build; then it is restored | Two CI runs |
| 13.5 | Full CI pipeline on every push | A red build blocks merge; runtime within a documented budget | Pipeline log |
| 13.6 | Eliminate every flaky test | 20 consecutive green pipeline runs | Run history |

**Agent must not:** let him chase a coverage percentage. Coverage is a diagnostic; mutation and property tests are the proof.

**Mistakes to expect.** Testing getters; asserting implementation details so every refactor breaks tests; one giant `@SpringBootTest`; `Thread.sleep` in tests; shared mutable state between tests; a mutation threshold gamed by excluding packages.

**Gate quiz.** (1) Why is 100% line coverage compatible with a broken system? (2) What does a surviving mutant tell you? (3) Which architectural rule saves you the most future pain, and why? (4) What makes a test flaky, in your own code? (5) What exactly does your pipeline refuse to accept?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
