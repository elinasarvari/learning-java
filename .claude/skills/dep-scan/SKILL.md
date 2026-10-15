---
name: dep-scan
description: Phase 0 of the dependency upgrade workflow - runs Snyk across every repo in docs/upgrades/repos.txt (or parses supplied Snyk JSON / pasted output) and documents EVERY flagged package with current version, severity, CVEs, fixed-in, how it is introduced and remediation, in 00-snyk-findings.md plus CSV. Read-only.
argument-hint: "[snyk.json ... | pasted output | (empty = scan repos.txt)]"
---

# dep-scan

Shared with GitHub Copilot: the procedure lives in `.github/skills/dep-upgrade/` — do not duplicate it here.

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/00-scan.md` step by step, with `$ARGUMENTS` as the input.
   Templates: `.github/skills/dep-upgrade/templates/` · scripts: `.github/skills/dep-upgrade/scripts/` (run from the learning-java root).

End by updating the upgrade's `STATUS.md` and replying with what was produced (links),
headline findings, and **▶ Next step**.
