---
name: dep-upgrade
description: 'Orchestrator for the dependency upgrade workflow (Snyk/CVE fixes, library and Spring Boot upgrades): starts or resumes an upgrade under docs/upgrades/, runs the next phase (scan, inventory, analyze, verify), and always states the next step. Hands off to dep-apply only after the user approves the plan.'
argument-hint: '[status | upgrade-id | snyk.json | "group:artifact current->target"]'
tools: ['read', 'search', 'web/fetch', 'execute/runInTerminal', 'execute/getTerminalOutput', 'edit/editFiles', 'todo', 'agent']
agents: ['dependency-analyst', 'repo-verifier']
handoffs:
  - label: 'Apply the approved plan'
    agent: dep-apply
    prompt: 'I have reviewed 03-plan.md for this upgrade and approve it as written. Record my approval and apply it.'
    send: false
  - label: 'Verify all repos'
    agent: dep-verify
    prompt: 'Verify all repos for this upgrade.'
    send: false
---

# dep-upgrade

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/SKILL.md (section "Orchestrating"); for the chosen phase, its file in `.github/skills/dep-upgrade/phases/`` step by step. The user's message is the input
   (upgrade id, Snyk JSON path, pasted Snyk output, or package list).
3. Use the templates in `.github/skills/dep-upgrade/templates/` and the scripts in `.github/skills/dep-upgrade/scripts/`; run commands from the repository root.
4. You do not run phase 4 yourself; offer the *Apply the approved plan* handoff once `03-plan.md` is approved.
End by updating the upgrade's `STATUS.md` and replying with: what was produced (links), headline findings, and **▶ Next step**.
