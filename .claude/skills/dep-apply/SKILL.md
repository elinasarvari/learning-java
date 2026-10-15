---
name: dep-apply
description: Phase 4 of the dependency upgrade workflow - applies the user-approved 03-plan.md to each repo on branch deps/<upgrade-id> via upgrade-applier subagents, compiling per step, logging to 04-apply-log.md. Modifies repositories.
argument-hint: "<upgrade-id> [repo-name ...]"
disable-model-invocation: true
---

# dep-apply

Shared with GitHub Copilot: the procedure lives in `.github/skills/dep-upgrade/` — do not duplicate it here.

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/04-apply.md` step by step, with `$ARGUMENTS` as the input.
   Templates: `.github/skills/dep-upgrade/templates/` · scripts: `.github/skills/dep-upgrade/scripts/` (run from the learning-java root).
3. Launch one `upgrade-applier` agent per repo, all in a single message (parallel).
End by updating the upgrade's `STATUS.md` and replying with what was produced (links),
headline findings, and **▶ Next step**.
