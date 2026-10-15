# Phases 2–3 — Impact analysis and plan

Requires phase 1 ✅. Read-only.

## Phase 2 — Impact (`02-impact.md`)

1. Copy `templates/02-impact.md`. STATUS phase 2 → 🟡.
2. **Work list** from `01-inventory.md`: one item per **(lever, repo)**. Many Snyk rows that move
   with the Spring Boot parent are *one* item. Add one item per coupled dependency that needs its
   own bump (e.g. Camel).
3. **Delegate each item to the `dependency-analyst` subagent** (role: `roles/dependency-analyst.md`).
   Run them in parallel when the tool allows it. Pass:
   - `package`, `current`, `target`, `repo` (absolute path)
   - `facts_dir` = `<upgrade>/facts/<repo-name>`, `report` = `<upgrade>/analysis/<repo-name>--<artifact>.md`
   - `coupled` = the coupled-dependency rows from 01 for that repo
   - the intermediate version lines to cover (e.g. `3.4, 3.5, 4.0, 4.1`)
4. **Assemble** `02-impact.md`: the Summary table from each report's `SUMMARY |` line, then each
   report under "Per package" (link the analysis file). If two analysts disagree (e.g. on the
   Camel version), settle it from the cited sources or record it as an open question in STATUS.

## Phase 3 — Plan (`03-plan.md`)

5. Copy `templates/03-plan.md`. Plan **from 02 only**, with no new research:
   - Strategy: one jump or stepping stones. For a medium/high-risk major jump, default to
     (a) latest minor of the current major, then (b) the target. Each is a separately green
     checkpoint. Explain the choice.
   - Order steps so each one compiles: pom/BOM bumps → coupled deps → renamed starters/artifacts
     → code package moves → config keys → test changes.
   - Exact diffs for pom/config edits. Precise descriptions with `path:line` for code edits.
   - "Verify after" for each step is `compile`, `test` or `smoke`.
6. STATUS: phase 2 ✅, phase 3 ⏸. ▶ Next step: "review `03-plan.md`, reply *approve* (or ask
   for changes), then run the apply phase".
7. **Never tick the approval box yourself.** When the user approves in chat, tick it, quote
   their words, date it, and copy the entry into STATUS "Decisions".
