---
name: dep-analyze
description: Phases 2-3 of the dependency upgrade workflow. Fans out dependency-analyst subagents (one per package x repo) to document pom-level and code-level impact of each version jump with citations, then writes a step-by-step upgrade plan for user approval. Read-only. Use after /dep-inventory.
argument-hint: "<upgrade-id>"
---

# Phases 2–3 — Impact analysis and plan

Read `.claude/skills/dep-upgrade/SKILL.md` first. Requires phase 1 ✅ in `STATUS.md`.
This phase changes nothing in any repo.

## Phase 2 — Impact (`02-impact.md`)

1. Copy `templates/02-impact.md` into the upgrade folder. STATUS phase 2 → 🟡.
2. Build the work list from `01-inventory.md`: one item per **(lever, repo)** — several Snyk
   rows that all move with the Spring Boot parent are *one* item — plus one item per
   coupled dependency that needs its own bump (e.g. Camel).
3. **Launch one `dependency-analyst` subagent per item, all in a single message so they run
   in parallel.** Give each:
   - `package`, `current`, `target`, `repo` (absolute path)
   - `facts_dir` = `<upgrade>/facts/<repo-name>` (absolute)
   - `report` = `<upgrade>/analysis/<repo-name>--<artifact>.md` (absolute)
   - `coupled` = the coupled-dependency rows from 01 for that repo
   - the list of intermediate version lines to cover (e.g. `3.4, 3.5, 4.0, 4.1`)
4. When they finish, assemble `02-impact.md`: the Summary table from each report's
   `SUMMARY |` line, then each report's body under "Per package" (link the analysis file).
   Cross-check: if two analysts disagree (e.g. on the Camel version needed), resolve it with
   the cited sources or flag it as an open question in STATUS.

## Phase 3 — Plan (`03-plan.md`)

5. Copy `templates/03-plan.md`. Write a plan **from 02 only** (no new research here):
   - Strategy: one jump or stepping stones. For a major jump with medium/high risk,
     default to: (a) latest patch of current minor, (b) latest minor of current major,
     (c) the target — each a separately green checkpoint. Explain the choice.
   - Steps ordered so each compiles: pom/BOM bumps → coupled deps → renamed
     starters/artifacts → code package moves → config keys → test changes.
   - Give exact diffs for pom/config changes; precise descriptions for code edits with
     `path:line` from 02.
   - A step's "Verify after" is `compile`, `test`, or `smoke`.
6. STATUS: phases 2 ✅, 3 ⏸ awaiting approval. ▶ Next step: "Review `03-plan.md`; reply
   *approve* (or request changes), then `/dep-apply <upgrade-id>`".
7. Reply with the Summary table, the top risks, the plan's step list, and the next step.
   **Do not tick the approval box yourself.** When the user approves in chat, tick it,
   quote their words, and date it.
