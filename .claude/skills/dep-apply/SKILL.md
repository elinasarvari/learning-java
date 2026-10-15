---
name: dep-apply
description: Phase 4 of the dependency upgrade workflow. Applies the user-approved 03-plan.md to each repo on a deps/<upgrade-id> branch via upgrade-applier subagents, compiling after each step and logging to 04-apply-log.md. Modifies repositories - only run when the user invokes it.
argument-hint: "<upgrade-id> [repo-name ...]"
disable-model-invocation: true
---

# Phase 4 — Apply

Read `.claude/skills/dep-upgrade/SKILL.md` first.

## Gate (stop if any fails, and tell the user why)

- `03-plan.md` contains `- [x] Approved by user` with the user's words and a date.
- Each target repo is clean apart from build output (`target/`, `build/`). Show the user
  `git status --short` of any dirty repo and ask how to proceed; never stash or discard
  for them.

## Steps

1. Create `04-apply-log.md` in the upgrade folder (header: date, plan revision, repos).
   STATUS phase 4 → 🟡.
2. **Baseline first** (skip if STATUS already has one for a repo): for each repo, on its
   *current* branch, run
   `bash .claude/skills/dep-upgrade/scripts/verify-repo.sh <repo> <upgrade>/verification/baseline/<repo-name> --smoke`
   (in parallel across repos). Record results in the STATUS "Repos → Baseline" column.
   Phase 5 uses these to tell a regression from a pre-existing failure.
3. Launch one `upgrade-applier` subagent per repo (in a single message, in parallel), with
   `repo`, `plan`, `log` (absolute paths) and `branch = deps/<upgrade-id>`.
   If the user named repos in `$ARGUMENTS`, only those.
4. Collect results into `04-apply-log.md` (one section per repo: steps ✅/⛔, deviations,
   final `git diff --stat`). Any deviation from the plan is listed again at the top.
5. STATUS: phase 4 ✅ if all repos finished, else ⛔ with the stopping step.
   ▶ Next step: `/dep-verify <upgrade-id>`, or the decision needed to unblock a ⛔ repo.
6. Reply with per-repo outcome, deviations, and the next step. Remind the user that
   changes are uncommitted on `deps/<upgrade-id>`; commit/PR only if they ask.
