# Phase 15 — Independent Chat Packet

**Topic:** The AI layer, off the critical path

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
4. Once Phase 15 is complete, direct the learner to a NEW chat for Phase 16 with the updated files.


## Original technical phase contract (preserved verbatim)

# PHASE 15 — The AI layer, off the critical path
**Effort: 3 weeks.** Closes: applied AI engineering for a backend engineer.

**Goal.** AI features that add real value, are measurable, degrade gracefully, and cannot break a payment.

**Concepts:**
1. **The governing ADR: "AI never gates a payment."** The LLM is never on the synchronous path of a money movement, and its failure changes nothing about payment success.
2. **Deterministic first, model second** — fraud *detection* is a scoring function over features (velocity, amount versus history, new counterparty, geography, time of day). It must be reproducible and explainable. The LLM *explains* the score and categorises; it never decides.
3. **Feature engineering on the ledger** — velocity per hour, amount z-score against customer history, count of new counterparties in 7 days, night-time ratio. Computed from the ledger, tested with known inputs. This is the part banks actually interview for.
4. **Structured output** — a JSON schema or typed response over a bounded enum (`GROCERIES`, `UTILITIES`, `SUBSCRIPTIONS`, `SALARY`, `TRANSFER`, `OTHER`), validated; free-text replies are useless downstream.
5. **Prompt and model versioning, cost and latency budgets** — prompts in version control, model pinned, temperature low, token and cost accounting per call, batching and async by default, caching identical requests.
6. **Data privacy** — no PII to a third-party model; descriptions masked (`CARD ****1234` → token); the minimum fields leaving the system documented; a local-model option considered.
7. **Evaluation harness** — a golden set of labelled transactions, per-class accuracy, and a regression test that fails when a prompt or model change degrades accuracy below the threshold. **An AI feature without an eval is a hope, not a feature.**
8. **Human in the loop** — AI category corrections are stored and feed the evaluation set; suspicion explanations are reviewed by a human before any customer-facing use.
9. **Semantic search** — embeddings over descriptions for "find transactions like this", with a bounded vector store; embeddings are similarity, not truth.
10. **Where an agent may act** — a read-only tool interface (MCP-style) exposing ledger queries, with hard authorisation, audit and rate limits. Write access for an autonomous agent in a banking system is a governance decision before it is a technical one.

| # | Task | Acceptance criteria | Evidence |
|---|---|---|---|
| 15.1 | ADR: AI off the critical path, with failure-mode analysis | Explicit statement: an LLM outage causes no payment failure | ADR |
| 15.2 | Deterministic risk score over ledger features | Reproducible; unit tests with hand-computed features | Tests |
| 15.3 | Async categorisation with structured output and validation | Invalid model output is rejected and falls back to `OTHER` | Test |
| 15.4 | Privacy review: which fields leave the process, with masking proof | Masked payloads only, shown in a test or log | Excerpt |
| 15.5 | Eval harness with a golden set and an accuracy threshold | A prompt change that lowers accuracy fails the test | Test output |
| 15.6 | Cost and latency accounting per call with a budget | A table of tokens, cost, latency; budget breach alerts | Numbers |
| 15.7 | Semantic search over statements | Ranked similar transactions with a relevance assessment | Output |
| 15.8 | Read-only agent interface with audit | Every agent query logged with the principal and rate limited | Log |
| 15.9 | Degradation drill: disable the model provider | Payments unaffected; categorisation degrades to rules; documented | Test + note |

**Agent must not:** write the feature calculations or the eval harness. Supply the requirement ("flag a transfer when its amount is more than three standard deviations above the customer's 90-day mean for that counterparty type") and let him implement it.

**Mistakes to expect.** Calling an LLM inside a `@Transactional` method; sending the full account number or customer name to the model; trusting the category without validation; no fallback path; prompts stored with no version history; measuring quality by "it looked right"; a fraud score that cannot be reproduced twice on the same input.

**Gate quiz.** (1) Why must a fraud score be deterministic and explainable? (2) What happens to a payment when the model provider is down, in your design? (3) How do you know a prompt change made the system worse? (4) Which fields could you not send to a third-party API, and why? (5) Why is a write-capable agent in a bank a governance problem before a technical one?

---

## Source of full curriculum
For deep background, consult `docs/ApexWallet_Agent_Handbook.md` Parts 1–3 and 5–7. Its rigid session format/timebox and blanket early-phase rubric requirements are superseded by the current teaching contract.
