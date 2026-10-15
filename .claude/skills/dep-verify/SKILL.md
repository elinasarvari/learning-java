---
name: dep-verify
description: Phase 5 of the dependency upgrade workflow (also usable standalone). For every repo in repos.txt, runs a full build, the test suite and a smoke boot via repo-verifier subagents in parallel, compares against the pre-upgrade baseline, and writes 05-verification.md with a go/no-go verdict. Use after /dep-apply, or to health-check all listed repos.
argument-hint: "<upgrade-id | baseline> [repo-name ...]"
---

# Phase 5 — Verify across repos

Read `.claude/skills/dep-upgrade/SKILL.md` first.

## Steps

1. **Repo list.** The upgrade folder's `repos.txt`, else `docs/upgrades/repos.txt`.
   Each non-comment line is `<absolute-or-~-path> [verify-repo.sh args]`, e.g.
   `~/repos/learning-java/backend --smoke`. Expand `~`. If `$ARGUMENTS` names repos, only those.
   Paths that don't exist → listed as `missing` in the report, not silently dropped.
2. **Standalone mode** (`/dep-verify baseline`): verify each repo on its current branch into
   `docs/upgrades/_health/<date>/<repo-name>/` and report — no upgrade folder needed.
3. **Upgrade mode:** confirm each repo is on `deps/<upgrade-id>` (if not, ask — don't switch
   branches for the user). Copy `templates/05-verification.md`. STATUS phase 5 → 🟡.
4. **Launch one `repo-verifier` subagent per repo in a single message (parallel)** with:
   - `repo`, `args` (from repos.txt), `script` = absolute path of
     `.claude/skills/dep-upgrade/scripts/verify-repo.sh`
   - `out_dir` = `<upgrade>/verification/after/<repo-name>`
   - `baseline` = `<upgrade>/verification/baseline/<repo-name>/summary.json` if present
   - `impact` = `<upgrade>/02-impact.md`
   Maven builds share `~/.m2`; if two builds download the same artifacts concurrently and one
   fails with a corrupted-artifact error, re-run that repo alone before calling it red.
5. **Assemble `05-verification.md`** from each `summary.json` + `diagnosis.md`: the Result
   table, a Failures section per red repo (classification: regression / pre-existing /
   environmental / unknown), and a Verdict.
6. **STATUS:** phase 5 ✅ if every repo is green or only pre-existing/environmental failures
   remain (say which); otherwise ⛔.
   ▶ Next step when green: "review `git diff` on `deps/<id>` and ask me to commit / open PRs".
   ▶ Next step when red: the proposed fix per regression — re-plan by adding steps to
   `03-plan.md` (needs re-approval), then `/dep-apply` → `/dep-verify` again.
7. Reply with the Result table, regressions with root cause, and the next step.
   Never re-run with tests skipped to manufacture a green result.
