# ApexWallet — one phase per ChatGPT chat

## The rule
**One ChatGPT chat = one phase.** Name chats `ApexWallet | Phase 00`, `ApexWallet | Phase 01`, etc. You may have multiple chat sessions within the same phase; if the chat becomes slow, start another chat in that phase using the SAME phase packet + current handoff. Starting a different chat does not automatically transfer files or state.

## The minimal portable bundle for starting Phase NN
1. `docs/phases/PHASE_NN.md` — a self-contained teaching packet (rules, phase goal, tasks, evidence, gate quiz, references).
2. `docs/handoffs/CURRENT_HANDOFF.md` — exact completed/current step, decisions, blockers, verified proof, next action. The only dynamic cross-chat handoff.
3. `PROGRESS.md` — overall status if the handoff lacks context. Alternatively upload the latest full `ApexWallet.zip` (which contains all three); tell the assistant which phase to open.

## Standard opening message (copy-paste into the NEW chat)
```
We are continuing ApexWallet with a NEW INDEPENDENT CHAT for PHASE NN.
Read docs/TEACHING_CONTRACT.md, docs/CHAT_WORKFLOW.md, PROGRESS.md,
docs/handoffs/CURRENT_HANDOFF.md and docs/phases/PHASE_NN.md from
my uploaded ZIP (or pasted texts). This chat cannot see prior chats.
Use the last verified evidence and explicitly flag any missing files.
Start with the recorded NEXT ACTION, teach one concept, give one small task.
Do not skip prerequisites or write the finance business logic for me.
```
Replace `NN` with `00`, `01`, ... `17`.

## Closing a chat or phase
Ask: `Create the updated PROGRESS.md, CURRENT_HANDOFF.md, phase review and the next phase opener from the evidence in this chat.` Save those texts into your local project (the assistant cannot mutate the local copy just by describing updates). Capture the files or ask for a new ZIP built from the latest uploaded project. Confirm files were actually saved. Handoff must include: phase/step, status, what was learned, files changed, exact evidence seen (self-reported vs tested), decisions/ADRs, pending blockers, next micro-task, date. Never put secrets in handoff.

At end of a phase, preserve the full project sources, record review under `docs/reviews/phase-NN-review.md`, update progress, and set handoff to `Phase NN COMPLETE → Phase NN+1 NOT STARTED` with prerequisites/evidence and exact next step. **Only** then start a fresh chat. Archive old handoffs under `docs/handoffs/archive/` if desired. The latest portable ZIP must be generated on the learner's computer from these updated files, or uploaded to ChatGPT for editing. A ZIP downloaded today does not magically track future work.

## Practical conflict handling
- If a new chat says Phase 1 but progress says Phase 0 still IN PROGRESS, inspect phase-0 exit evidence and request missing closure work instead of inventing completion.
- If only the phase packet is available, start at its entry checklist; ask for current progress and label unknown items UNVERIFIED.
- If a chat crashes before file updates, resume from most recent saved handoff and independently re-check any unrecorded assertions.
- Each phase packet includes the entire original phase specification, so earlier chat history is not required.
