---
name: dep-verify
description: 'Phase 5 of the dependency upgrade workflow (or standalone health check with "baseline"): builds, runs tests and smoke-boots every repo in repos.txt via repo-verifier subagents, compares to the pre-upgrade baseline and writes 05-verification.md with a go/no-go verdict.'
argument-hint: '<upgrade-id | baseline> [repo-name ...]'
tools: ['read', 'search', 'web/fetch', 'execute/runInTerminal', 'execute/getTerminalOutput', 'edit/editFiles', 'todo', 'agent']
agents: ['repo-verifier']
handoffs:
  - label: 'Red: re-plan the fixes'
    agent: dep-analyze
    prompt: 'Add plan steps that fix the regressions listed in 05-verification.md for this upgrade (needs my re-approval).'
    send: false
---

# dep-verify

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/05-verify.md` step by step. The user's message is the input
   (upgrade id, Snyk JSON path, pasted Snyk output, or package list).
3. Use the templates in `.github/skills/dep-upgrade/templates/` and the scripts in `.github/skills/dep-upgrade/scripts/`; run commands from the repository root.

End by updating the upgrade's `STATUS.md` and replying with: what was produced (links), headline findings, and **▶ Next step**.
