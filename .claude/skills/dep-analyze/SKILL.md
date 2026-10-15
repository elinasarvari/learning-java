---
name: dep-analyze
description: Phases 2-3 of the dependency upgrade workflow - dependency-analyst subagents document pom/code/config/test impact of each version jump with citations, then a step-by-step 03-plan.md for user approval. Read-only.
argument-hint: "<upgrade-id>"
---

# dep-analyze

Shared with GitHub Copilot: the procedure lives in `.github/skills/dep-upgrade/` — do not duplicate it here.

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/02-analyze.md` step by step, with `$ARGUMENTS` as the input.
   Templates: `.github/skills/dep-upgrade/templates/` · scripts: `.github/skills/dep-upgrade/scripts/` (run from the learning-java root).
3. Launch one `dependency-analyst` agent per (lever, repo) item, all in a single message (parallel).
4. Never tick the approval box yourself.
End by updating the upgrade's `STATUS.md` and replying with what was produced (links),
headline findings, and **▶ Next step**.
