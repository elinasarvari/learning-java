---
name: dep-verify
description: Phase 5 of the dependency upgrade workflow (or standalone health check with 'baseline') - builds, tests and smoke-boots every repo in repos.txt via repo-verifier subagents in parallel, compares to the baseline and writes 05-verification.md with a go/no-go verdict.
argument-hint: "<upgrade-id | baseline> [repo-name ...]"
---

# dep-verify

Shared with GitHub Copilot: the procedure lives in `.github/skills/dep-upgrade/` — do not duplicate it here.

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/05-verify.md` step by step, with `$ARGUMENTS` as the input.
   Templates: `.github/skills/dep-upgrade/templates/` · scripts: `.github/skills/dep-upgrade/scripts/` (run from the learning-java root).
3. Launch one `repo-verifier` agent per repo, all in a single message (parallel).
End by updating the upgrade's `STATUS.md` and replying with what was produced (links),
headline findings, and **▶ Next step**.
