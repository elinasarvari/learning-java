---
name: dep-inventory
description: 'Phase 1 of the dependency upgrade workflow: verified inventory of package, current version, target version, how each version is set in the pom (lever), what else moves with it, and coupled libraries - written to 01-inventory.md. Read-only.'
argument-hint: '<upgrade-id | "group:artifact current->target, ...">'
tools: ['read', 'search', 'web/fetch', 'execute/runInTerminal', 'execute/getTerminalOutput', 'edit/editFiles', 'todo']
handoffs:
  - label: 'Next: analyse impact and plan'
    agent: dep-analyze
    prompt: 'Analyse impact and write the plan for this upgrade.'
    send: false
---

# dep-inventory

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/01-inventory.md` step by step. The user's message is the input
   (upgrade id, Snyk JSON path, pasted Snyk output, or package list).
3. Use the templates in `.github/skills/dep-upgrade/templates/` and the scripts in `.github/skills/dep-upgrade/scripts/`; run commands from the repository root.

End by updating the upgrade's `STATUS.md` and replying with: what was produced (links), headline findings, and **▶ Next step**.
