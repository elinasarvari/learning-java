---
name: dep-scan
description: 'Phase 0 of the dependency upgrade workflow: runs Snyk across every repo in docs/upgrades/repos.txt (or reads supplied Snyk JSON / pasted output) and documents EVERY flagged package - current version, severity, CVEs, fixed-in, how it is introduced, remediation - in 00-snyk-findings.md plus a CSV. Read-only.'
argument-hint: '[snyk.json ... | pasted Snyk output | nothing to scan repos.txt]'
tools: ['read', 'search', 'web/fetch', 'execute/runInTerminal', 'execute/getTerminalOutput', 'edit/editFiles', 'todo']
handoffs:
  - label: 'Next: inventory the chosen scope'
    agent: dep-inventory
    prompt: 'Inventory the in-scope findings from 00-snyk-findings.md for this upgrade. Scope: all'
    send: false
---

# dep-scan

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/00-scan.md` step by step. The user's message is the input
   (upgrade id, Snyk JSON path, pasted Snyk output, or package list).
3. Use the templates in `.github/skills/dep-upgrade/templates/` and the scripts in `.github/skills/dep-upgrade/scripts/`; run commands from the repository root.

End by updating the upgrade's `STATUS.md` and replying with: what was produced (links), headline findings, and **▶ Next step**.
