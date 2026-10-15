# Phase 4 — Apply

**Changes repositories.** Run only when the user explicitly asks for this phase.

## Gate — stop and say why if any fails

- `03-plan.md` contains `- [x] Approved by user` with the user's words and a date.
- Each target repo has no uncommitted changes apart from build output (`target/`, `build/`).
  Show `git status --short` for a dirty repo and ask how to proceed. Never stash, reset or
  discard for the user.

## Steps

1. Create `04-apply-log.md` (date, plan revision, repos). STATUS phase 4 → 🟡.
2. **Baseline** — if STATUS has none for a repo, run `verify-repo.sh ... --smoke` into
   `<upgrade>/verification/baseline/<repo-name>` on the repo's current branch first.
3. **Delegate each repo to the `upgrade-applier` subagent** (role: `roles/upgrade-applier.md`).
   Pass `repo`, `plan`, `log` (absolute paths) and `branch = deps/<upgrade-id>`. If the user
   named repos, do only those.
4. Collect the results into `04-apply-log.md`, one section per repo: steps ✅/⛔, deviations,
   final `git diff --stat`. List every deviation from the plan again at the top.
5. STATUS phase 4 → ✅ if all repos finished, else ⛔ with the step where it stopped.
   ▶ Next step: the verify phase, or the decision needed to unblock.
6. Remind the user the changes are uncommitted on `deps/<upgrade-id>`. Commit, push or open
   PRs only if they ask.
