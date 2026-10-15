---
name: dep-upgrade
description: Orchestrates the documented dependency upgrade workflow (Snyk/CVE fixes, library and Spring Boot upgrades) - scan, inventory, analyze, plan, apply, verify across repos - resuming from STATUS.md and always ending with the next step. Use when the user wants to upgrade library versions, fix Snyk findings, or asks where an upgrade stands.
argument-hint: "[status | upgrade-id | snyk.json | 'group:artifact current->target']"
---

# dep-upgrade

Shared with GitHub Copilot: the procedure lives in `.github/skills/dep-upgrade/` — do not duplicate it here.

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/SKILL.md` section "Orchestrating", then the chosen phase file in `phases/` step by step, with `$ARGUMENTS` as the input.
   Templates: `.github/skills/dep-upgrade/templates/` · scripts: `.github/skills/dep-upgrade/scripts/` (run from the learning-java root).
3. Subagents: launch the `dependency-analyst` / `upgrade-applier` / `repo-verifier` agents, one per item, all in one message so they run in parallel.
4. Never run phase 4 from here unless the user invoked `/dep-apply` or explicitly asked to apply an approved plan.
End by updating the upgrade's `STATUS.md` and replying with what was produced (links),
headline findings, and **▶ Next step**.
