---
name: dep-inventory
description: Phase 1 of the dependency upgrade workflow - verified list of package, current version, target version, how each version is set in the pom, what moves with it and coupled libraries, written to 01-inventory.md. Read-only.
argument-hint: "<upgrade-id | 'group:artifact current->target, ...'>"
---

# dep-inventory

Shared with GitHub Copilot: the procedure lives in `.github/skills/dep-upgrade/` — do not duplicate it here.

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/01-inventory.md` step by step, with `$ARGUMENTS` as the input.
   Templates: `.github/skills/dep-upgrade/templates/` · scripts: `.github/skills/dep-upgrade/scripts/` (run from the learning-java root).

End by updating the upgrade's `STATUS.md` and replying with what was produced (links),
headline findings, and **▶ Next step**.
