# Phase 5 — Verify across repos

Also usable on its own as a health check (`baseline` mode). Changes no source.

## Steps

1. **Repo list:** `<upgrade>/repos.txt`, else `docs/upgrades/repos.txt`. Each non-comment line is
   `<path> [verify-repo.sh args]`; expand `~`. Paths that don't exist are reported as `missing`.
2. **Baseline mode** (argument `baseline`): verify each repo on its current branch into
   `docs/upgrades/_health/<date>/<repo-name>/` and report. No upgrade folder is needed.
3. **Upgrade mode:** check each repo is on `deps/<upgrade-id>`. If one isn't, ask; don't switch
   branches for the user. Copy `templates/05-verification.md`. STATUS phase 5 → 🟡.
4. **Delegate each repo to the `repo-verifier` subagent** (role: `roles/repo-verifier.md`),
   in parallel when possible. Pass:
   - `repo`, `args` (from repos.txt), `script` = absolute path of `scripts/verify-repo.sh`
   - `out_dir` = `<upgrade>/verification/after/<repo-name>`
   - `baseline` = `<upgrade>/verification/baseline/<repo-name>/summary.json` (if present)
   - `impact` = `<upgrade>/02-impact.md`
   Parallel Maven builds share `~/.m2`. A corrupted-artifact error during concurrent downloads →
   re-run that repo alone before calling it red.
5. **Optional re-scan:** if the Snyk CLI is available, re-run phase 0 step 2 into
   `<upgrade>/snyk-after/` and add "Snyk findings before → after" to the report. This proves the
   targeted issues are gone and that no new ones came in.
6. **Assemble `05-verification.md`:** the Result table, a Failures section per red repo
   (regression / pre-existing / environmental / unknown), and the Verdict.
7. STATUS phase 5 → ✅ if every repo is green or only pre-existing/environmental failures
   remain (say which); else ⛔.
   ▶ Next step when green: "review `git diff` on `deps/<id>`, then ask to commit / open PRs".
   ▶ Next step when red: the fix for each regression. Add it as new plan steps (needs
   re-approval), then apply → verify again.
8. Never re-run with tests skipped to manufacture a green result.
